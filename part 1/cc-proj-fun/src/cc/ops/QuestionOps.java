package cc.ops;

import java.util.Arrays;

import cc.data.AuctionDAO;
import cc.data.DataLayer;
import cc.data.Question;
import cc.data.QuestionDAO;
import cc.data.Session;
import cc.db.Filter;
import cc.utils.Result;

/**
 * Question operations. Only the owner of the auction can reply, and only once.
 */
public class QuestionOps {

	private static QuestionOps instance;

	public static synchronized QuestionOps getInstance() {
		if (instance == null)
			instance = new QuestionOps(DataLayer.getInstance());
		return instance;
	}

	private final DataLayer db;

	public QuestionOps(DataLayer db) {
		this.db = db;
	}

	/** No session needed to ask a question. */
	public Result<Question> ask(String auctionId, Question question) {
		if (question == null || question.getText() == null || question.getText().isEmpty()
				|| question.getUser() == null || question.getUser().isEmpty())
			return Result.error(400);
		Result<AuctionDAO> a = db.getObjById(auctionId, AuctionDAO.class);
		if (!a.isOK())
			return Result.error(a.error());
		question.setAuctionId(auctionId);
		question.setReply(null);
		Result<QuestionDAO> r = db.postObj(new QuestionDAO(question));
		return r.isOK() ? Result.ok(r.value().toQuestion()) : Result.<Question>error(r.error());
	}

	public Result<Question[]> list(String auctionId, int offset, int limit) {
		Result<QuestionDAO[]> r = db.getObjs(offset, limit, Filter.eq("auctionId", auctionId),
				QuestionDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		return Result.ok(Arrays.stream(r.value()).map(QuestionDAO::toQuestion).toArray(Question[]::new));
	}

	public Result<Question> reply(String sessionId, String auctionId, String questionId, Question body) {
		if (body == null || body.getReply() == null || body.getReply().isEmpty())
			return Result.error(400);
		Result<AuctionDAO> a = db.getObjById(auctionId, AuctionDAO.class);
		if (!a.isOK())
			return Result.error(a.error());
		Result<Session> s = db.checkSession(sessionId, a.value().getOwner());
		if (!s.isOK())
			return Result.error(s.error());

		Result<QuestionDAO> r = db.getObjById(questionId, QuestionDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		QuestionDAO q = r.value();
		if (!auctionId.equals(q.getAuctionId()))
			return Result.error(404);
		if (q.getReply() != null && !q.getReply().isEmpty())
			return Result.error(403);

		q.setReply(body.getReply());
		Result<QuestionDAO> w = db.updateObj(q);
		return w.isOK() ? Result.ok(w.value().toQuestion()) : Result.<Question>error(w.error());
	}
}
