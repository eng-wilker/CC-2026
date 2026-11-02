package cc.ops;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;

import cc.data.Auction;
import cc.data.AuctionDAO;
import cc.data.BidDAO;
import cc.data.DataLayer;
import cc.data.Session;
import cc.db.Filter;
import cc.utils.Result;

/**
 * Auction operations.
 */
public class AuctionOps {

	private static AuctionOps instance;

	public static synchronized AuctionOps getInstance() {
		if (instance == null)
			instance = new AuctionOps(DataLayer.getInstance());
		return instance;
	}

	private final DataLayer db;

	public AuctionOps(DataLayer db) {
		this.db = db;
	}

	public Result<Auction> create(String sessionId, Auction auction) {
		if (auction == null || auction.getTitle() == null || auction.getTitle().isEmpty()
				|| auction.getOwner() == null || auction.getOwner().isEmpty()
				|| auction.getEndTime() == null || auction.getMinimumPrice() < 0)
			return Result.error(400);
		Result<Session> s = db.checkSession(sessionId, auction.getOwner());
		if (!s.isOK())
			return Result.error(s.error());
		// An auction is always created open and with no bids; neither is taken from the request.
		auction.setStatus(AuctionDAO.OPEN);
		auction.setWinnerBid(null);
		Result<AuctionDAO> r = db.postObj(new AuctionDAO(auction));
		return r.isOK() ? Result.ok(r.value().toAuction()) : Result.<Auction>error(r.error());
	}

	public Result<Auction> get(String id) {
		Result<AuctionDAO> r = db.getObjById(id, AuctionDAO.class);
		return r.isOK() ? Result.ok(r.value().toAuction()) : Result.<Auction>error(r.error());
	}

	public Result<Auction> update(String sessionId, String id, Auction auction) {
		if (auction == null)
			return Result.error(400);
		Result<AuctionDAO> r = db.getObjById(id, AuctionDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		AuctionDAO dao = r.value();
		Result<Session> s = db.checkSession(sessionId, dao.getOwner());
		if (!s.isOK())
			return Result.error(s.error());
		if (auction.getTitle() != null) dao.setTitle(auction.getTitle());
		if (auction.getDescription() != null) dao.setDescription(auction.getDescription());
		if (auction.getImageId() != null) dao.setImageId(auction.getImageId());
		if (auction.getEndTime() != null) dao.setEndTime(auction.getEndTime());
		Result<AuctionDAO> w = db.updateObj(dao);
		return w.isOK() ? Result.ok(w.value().toAuction()) : Result.<Auction>error(w.error());
	}

	public Result<Auction[]> list(int offset, int limit) {
		Result<AuctionDAO[]> r = db.getObjs(offset, limit, null, AuctionDAO.class);
		return r.isOK() ? Result.ok(toAuctions(r.value())) : Result.<Auction[]>error(r.error());
	}

	/** Recent open auctions. L4: fetched and filtered on every request. */
	public Result<Auction[]> recent(int offset, int limit) {
		Result<AuctionDAO[]> r = db.getObjs(0, 1000, null, AuctionDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		return Result.ok(Arrays.stream(r.value())
				.filter(a -> AuctionDAO.OPEN.equals(a.getStatus()))
				.skip(Math.max(0, offset)).limit(Math.max(0, limit))
				.map(AuctionDAO::toAuction).toArray(Auction[]::new));
	}

	/** Open auctions by end time. L4: scan of all open auctions, sorted here. */
	public Result<Auction[]> aboutToClose(int offset, int limit) {
		Result<AuctionDAO[]> r = db.getObjs(0, 1000, Filter.eq("status", AuctionDAO.OPEN),
				AuctionDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		// Only auctions that have not ended yet: nothing moves an auction to "closed" when its
		// end time passes, so without this the endpoint returns exactly the ones that are over.
		Date now = new Date();
		return Result.ok(Arrays.stream(r.value())
				.filter(a -> a.getEndTime() != null && a.getEndTime().after(now))
				.sorted(Comparator.comparing(AuctionDAO::getEndTime))
				.skip(Math.max(0, offset)).limit(Math.max(0, limit))
				.map(AuctionDAO::toAuction).toArray(Auction[]::new));
	}

	/** Open auctions by number of bids. L4: the counts are still recomputed on every request. */
	public Result<Auction[]> popular(int offset, int limit) {
		Result<AuctionDAO[]> r = db.getObjs(0, 1000, Filter.eq("status", AuctionDAO.OPEN),
				AuctionDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		AuctionDAO[] open = r.value();

		// One query for all the counts, not one per auction: the database groups the bids and
		// only the totals come back. The ordering is done here because Cosmos DB does not
		// accept an ORDER BY on a GROUP BY.
		Result<Map<String, Integer>> c = db.countByField("auctionId", BidDAO.class);
		if (!c.isOK())
			return Result.error(c.error());
		Map<String, Integer> bidCount = c.value();

		return Result.ok(Arrays.stream(open)
				.sorted(Comparator.comparingInt((AuctionDAO a) -> bidCount.getOrDefault(a.getId(), 0))
						.reversed())
				.skip(Math.max(0, offset)).limit(Math.max(0, limit))
				.map(AuctionDAO::toAuction).toArray(Auction[]::new));
	}

	/** Search on title and description. L5: CONTAINS, so a cross-partition scan. */
	public Result<Auction[]> search(String query, int offset, int limit) {
		if (query == null || query.isEmpty())
			return Result.error(400);
		Result<AuctionDAO[]> r = db.searchObjs(new String[] { "title", "description" }, query,
				offset, limit, AuctionDAO.class);
		return r.isOK() ? Result.ok(toAuctions(r.value())) : Result.<Auction[]>error(r.error());
	}

	static Auction[] toAuctions(AuctionDAO[] daos) {
		return Arrays.stream(daos).map(AuctionDAO::toAuction).toArray(Auction[]::new);
	}
}
