package cc.ops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import cc.data.Auction;
import cc.data.AuctionDAO;
import cc.data.AuctionSearch;
import cc.data.AzureAuctionSearch;
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
	private final AuctionSearch auctionSearch;

	public AuctionOps(DataLayer db) {
		this(db, null);
	}

	public AuctionOps(DataLayer db, AuctionSearch auctionSearch) {
		this.db = db;
		this.auctionSearch = auctionSearch;
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
		for (int attempt = 0; attempt < 3; attempt++) {
			Result<AuctionDAO> r = db.getAuctionForUpdate(id);
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
			if (w.isOK())
				return Result.ok(w.value().toAuction());
			if (w.error() != 412)
				return Result.error(w.error());
		}
		return Result.error(409);
	}

	public Result<Auction[]> list(int offset, int limit) {
		Result<AuctionDAO[]> r = db.getObjs(offset, limit, null, AuctionDAO.class);
		return r.isOK() ? Result.ok(toAuctions(r.value())) : Result.<Auction[]>error(r.error());
	}

	/** Recent open auctions. */
	public Result<Auction[]> recent(int offset, int limit) {
		AuctionDAO[] cached = db.getCachedAuctionList("recent");
		if (cached != null)
			return Result.ok(page(cached, offset, limit));
		Result<AuctionDAO[]> r = db.getInformationalObjs(0, 1000, null, AuctionDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		AuctionDAO[] recent = Arrays.stream(r.value())
				.filter(a -> AuctionDAO.OPEN.equals(a.getStatus()))
				.toArray(AuctionDAO[]::new);
		db.putCachedAuctionList("recent", recent);
		return Result.ok(page(recent, offset, limit));
	}

	/** Open auctions by end time. */
	public Result<Auction[]> aboutToClose(int offset, int limit) {
		AuctionDAO[] cached = db.getCachedAuctionList("about-to-close");
		if (cached != null)
			return Result.ok(page(cached, offset, limit));
		Result<AuctionDAO[]> r = db.getInformationalObjs(0, 1000, Filter.eq("status", AuctionDAO.OPEN),
				AuctionDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		// Only auctions that have not ended yet: nothing moves an auction to "closed" when its
		// end time passes, so without this the endpoint returns exactly the ones that are over.
		Date now = new Date();
		AuctionDAO[] closing = Arrays.stream(r.value())
				.filter(a -> a.getEndTime() != null && a.getEndTime().after(now))
				.sorted(Comparator.comparing(AuctionDAO::getEndTime))
				.toArray(AuctionDAO[]::new);
		db.putCachedAuctionList("about-to-close", closing);
		return Result.ok(page(closing, offset, limit));
	}

	/** Open auctions by number of bids. */
	public Result<Auction[]> popular(int offset, int limit) {
		AuctionDAO[] cached = db.getCachedAuctionList("popular");
		if (cached != null)
			return Result.ok(page(cached, offset, limit));
		Result<AuctionDAO[]> computed = computePopular();
		if (!computed.isOK())
			return Result.error(computed.error());
		db.putCachedAuctionList("popular", computed.value());
		return Result.ok(page(computed.value(), offset, limit));
	}

	public Result<Void> refreshListingCaches() {
		Result<AuctionDAO[]> recent = db.getInformationalObjs(0, 1000, null, AuctionDAO.class);
		if (!recent.isOK())
			return Result.error(recent.error());
		AuctionDAO[] open = Arrays.stream(recent.value())
				.filter(a -> AuctionDAO.OPEN.equals(a.getStatus()))
				.toArray(AuctionDAO[]::new);
		db.putCachedAuctionList("recent", open);
		Date now = new Date();
		AuctionDAO[] closing = Arrays.stream(open)
				.filter(a -> a.getEndTime() != null && a.getEndTime().after(now))
				.sorted(Comparator.comparing(AuctionDAO::getEndTime))
				.toArray(AuctionDAO[]::new);
		db.putCachedAuctionList("about-to-close", closing);
		Result<AuctionDAO[]> popular = computePopular(open);
		if (!popular.isOK())
			return Result.error(popular.error());
		db.putCachedAuctionList("popular", popular.value());
		return Result.ok();
	}

	private Result<AuctionDAO[]> computePopular() {
		Result<AuctionDAO[]> r = db.getInformationalObjs(0, 1000, Filter.eq("status", AuctionDAO.OPEN),
				AuctionDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		return computePopular(r.value());
	}

	private Result<AuctionDAO[]> computePopular(AuctionDAO[] open) {
		Result<Map<String, Integer>> c = db.countByField("auctionId", BidDAO.class);
		if (!c.isOK())
			return Result.error(c.error());
		Map<String, Integer> bidCount = c.value();

		return Result.ok(Arrays.stream(open)
				.sorted(Comparator.comparingInt((AuctionDAO a) -> bidCount.getOrDefault(a.getId(), 0))
						.reversed())
				.toArray(AuctionDAO[]::new));
	}

	private static Auction[] page(AuctionDAO[] auctions, int offset, int limit) {
		return Arrays.stream(auctions)
				.skip(Math.max(0, offset)).limit(Math.max(0, limit))
				.map(AuctionDAO::toAuction).toArray(Auction[]::new);
	}

	public Result<Auction[]> search(String query, int offset, int limit) {
		if (query == null || query.isEmpty())
			return Result.error(400);
		Result<String[]> ids = searchProvider().search(query, Math.max(0, offset), Math.max(1, limit));
		if (!ids.isOK())
			return Result.error(ids.error());
		List<Auction> auctions = new ArrayList<>();
		for (String id : ids.value()) {
			Result<AuctionDAO> result = db.getObjById(id, AuctionDAO.class);
			if (!result.isOK()) {
				if (result.error() == 404)
					continue;
				return Result.error(result.error());
			}
			auctions.add(result.value().toAuction());
		}
		return Result.ok(auctions.toArray(Auction[]::new));
	}

	private AuctionSearch searchProvider() {
		return auctionSearch == null ? AzureAuctionSearch.getInstance() : auctionSearch;
	}

	static Auction[] toAuctions(AuctionDAO[] daos) {
		return Arrays.stream(daos).map(AuctionDAO::toAuction).toArray(Auction[]::new);
	}
}
