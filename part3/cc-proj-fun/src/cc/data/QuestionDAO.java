package cc.data;

public class QuestionDAO extends DBTypeDAO {
	private String auctionId;
	private String user;
	private String text;
	private String reply;

	public QuestionDAO() {
	}

	public QuestionDAO(Question q) {
		this(q.getId(), q.getAuctionId(), q.getUser(), q.getText(), q.getReply());
	}

	public QuestionDAO(String id, String auctionId, String user, String text, String reply) {
		super(id);
		this.auctionId = auctionId;
		this.user = user;
		this.text = text;
		this.reply = reply;
	}

	public Question toQuestion() {
		return new Question(getId(), auctionId, user, text, reply);
	}

	@Override
	public String getPartKey() {
		return auctionId;
	}

	public String getAuctionId() {
		return auctionId;
	}
	public void setAuctionId(String auctionId) {
		this.auctionId = auctionId;
	}
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	public String getReply() {
		return reply;
	}
	public void setReply(String reply) {
		this.reply = reply;
	}

	@Override
	public String toString() {
		return "QuestionDAO [" + super.toString() + ", auctionId=" + auctionId + ", user=" + user + "]";
	}
}
