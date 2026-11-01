package cc.ops;

import java.util.Arrays;
import java.util.UUID;

import cc.data.Auction;
import cc.data.AuctionDAO;
import cc.data.BidDAO;
import cc.db.Filter;

import cc.data.DataLayer;
import cc.data.Login;
import cc.data.Session;
import cc.data.SessionDAO;
import cc.data.User;
import cc.data.UserDAO;
import cc.utils.Hash;
import cc.utils.Result;

/**
 * User and session operations. Shared by the REST resource and by the Functions version,
 * so there are no jakarta.ws.rs types here and nothing throws - errors come back in the Result.
 */
public class UserOps {

	public static final String DELETED_USER = "Deleted User";

	private static UserOps instance;

	public static synchronized UserOps getInstance() {
		if (instance == null)
			instance = new UserOps(DataLayer.getInstance());
		return instance;
	}

	private final DataLayer db;

	public UserOps(DataLayer db) {
		this.db = db;
	}

	public Result<User> create(User user) {
		if (user == null || user.getId() == null || user.getId().isEmpty()
				|| user.getPwd() == null || user.getPwd().isEmpty())
			return Result.error(400);
		UserDAO dao = new UserDAO(user);
		dao.setPwd(Hash.of(user.getPwd().getBytes()));
		Result<UserDAO> r = db.postObj(dao);
		return r.isOK() ? Result.ok(r.value().toUser()) : Result.<User>error(r.error());
	}

	public Result<User> get(String id) {
		Result<UserDAO> r = db.getObjById(id, UserDAO.class);
		return r.isOK() ? Result.ok(r.value().toUser()) : Result.<User>error(r.error());
	}

	/** Returns the id of the new session. */
	public Result<String> auth(Login login) {
		if (login == null || login.getUser() == null || login.getPwd() == null)
			return Result.error(400);
		Result<UserDAO> u = db.getObjById(login.getUser(), UserDAO.class);
		// Only a missing user is an authentication failure. Anything else - a 429 from the
		// database, say - has to come back as itself, or a throttled login is indistinguishable
		// from a wrong password.
		if (!u.isOK())
			return Result.error(u.error() == 404 ? 401 : u.error());
		if (!u.value().getPwd().equals(Hash.of(login.getPwd().getBytes())))
			return Result.error(401);
		String sid = UUID.randomUUID().toString();
		Result<SessionDAO> s = db.postObj(new SessionDAO(sid, login.getUser()));
		return s.isOK() ? Result.ok(sid) : Result.<String>error(s.error());
	}

	public Result<User> update(String sessionId, String id, User user) {
		if (user == null)
			return Result.error(400);
		Result<Session> s = db.checkSession(sessionId, id);
		if (!s.isOK())
			return Result.error(s.error());
		Result<UserDAO> r = db.getObjById(id, UserDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		UserDAO dao = r.value();
		if (user.getName() != null) dao.setName(user.getName());
		if (user.getPhotoId() != null) dao.setPhotoId(user.getPhotoId());
		if (user.getPwd() != null && !user.getPwd().isEmpty())
			dao.setPwd(Hash.of(user.getPwd().getBytes()));
		Result<UserDAO> w = db.updateObj(dao);
		return w.isOK() ? Result.ok(w.value().toUser()) : Result.<User>error(w.error());
	}

	public Result<Auction[]> auctions(String id, String status, int offset, int limit) {
		// Filter holds a single predicate, so status is matched here
		Result<AuctionDAO[]> r = db.getObjs(offset, limit, Filter.eq("owner", id), AuctionDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		AuctionDAO[] all = r.value();
		if (status != null && !status.isEmpty())
			all = Arrays.stream(all).filter(a -> status.equals(a.getStatus())).toArray(AuctionDAO[]::new);
		return Result.ok(Arrays.stream(all).map(AuctionDAO::toAuction).toArray(Auction[]::new));
	}

	/**
	 * Deletes a user. Auctions and bids are reassigned to "Deleted User", not removed.
	 * L4: the reassignment is done on the request path.
	 */
	public Result<Void> delete(String sessionId, String id) {
		Result<Session> s = db.checkSession(sessionId, id);
		if (!s.isOK())
			return Result.error(s.error());
		Result<UserDAO> u = db.getObjById(id, UserDAO.class);
		if (!u.isOK())
			return Result.error(u.error());

		Result<AuctionDAO[]> auctions = db.getObjs(0, 1000, Filter.eq("owner", id), AuctionDAO.class);
		if (auctions.isOK())
			for (AuctionDAO a : auctions.value()) {
				a.setOwner(DELETED_USER);
				db.updateObj(a);
			}

		Result<BidDAO[]> bids = db.getObjs(0, 1000, Filter.eq("user", id), BidDAO.class);
		if (bids.isOK())
			for (BidDAO b : bids.value()) {
				b.setUser(DELETED_USER);
				db.updateObj(b);
			}

		return db.delObj(id, id, UserDAO.class);
	}
}
