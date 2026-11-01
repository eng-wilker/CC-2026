package cc.ops;

import java.util.Arrays;
import java.util.Date;
import java.util.logging.Logger;

import cc.data.AuctionDAO;
import cc.data.Bid;
import cc.data.BidDAO;
import cc.data.DataLayer;
import cc.data.Session;
import cc.db.Filter;
import cc.utils.Result;

/**
 * Bid operations.
 */
public class BidOps {
	private static final Logger LOG = Logger.getLogger(BidOps.class.getName());
	private static final int MAX_ATTEMPTS = 3;

	private static BidOps instance;

	public static synchronized BidOps getInstance() {
		if (instance == null)
			instance = new BidOps(DataLayer.getInstance());
		return instance;
	}

	private final DataLayer db;

	public BidOps(DataLayer db) {
		this.db = db;
	}

	public Result<Bid> create(String sessionId, String auctionId, Bid bid) {
		if (bid == null || bid.getUser() == null || bid.getUser().isEmpty())
			return Result.error(400);
		Result<Session> s = db.checkSession(sessionId, bid.getUser());
		if (!s.isOK())
			return Result.error(s.error());

		BidDAO stored = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			Result<AuctionDAO> read = db.getAuctionForUpdate(auctionId);
			if (!read.isOK())
				return failAndRemove(stored, read.error());
			AuctionDAO auction = read.value();
			if (!AuctionDAO.OPEN.equals(auction.getStatus())
					|| (auction.getEndTime() != null && auction.getEndTime().before(new Date()))
					|| bid.getValue() < auction.getMinimumPrice()
					|| (auction.getWinnerBid() != null && bid.getValue() <= auction.getWinnerBid().getValue())) {
				if (stored != null)
					LOG.info("Bid no longer valid after concurrent update");
				return failAndRemove(stored, 403);
			}
			if (stored == null) {
				bid.setAuctionId(auctionId);
				bid.setTime(new Date());
				Result<BidDAO> created = db.postObj(new BidDAO(bid));
				if (!created.isOK())
					return Result.error(created.error());
				stored = created.value();
			}
			auction.setWinnerBid(stored.toBid());
			Result<AuctionDAO> updated = db.updateObj(auction);
			if (updated.isOK())
				return Result.ok(stored.toBid());
			if (updated.error() != 412) {
				LOG.warning("Auction update outcome uncertain for bid " + stored.getId());
				return Result.error(updated.error());
			}
			LOG.info("Auction ETag conflict on attempt " + attempt);
		}
		LOG.warning("Bid rejected after repeated auction contention");
		return failAndRemove(stored, 409);
	}

	private Result<Bid> failAndRemove(BidDAO stored, int error) {
		if (stored != null) {
			Result<Void> removed = db.delObj(stored.getId(), stored.getAuctionId(), BidDAO.class);
			if (!removed.isOK() && removed.error() != 404) {
				LOG.warning("Failed to remove unsuccessful bid " + stored.getId());
				return Result.error(500);
			}
		}
		return Result.error(error);
	}

	public Result<Bid[]> list(String auctionId, int offset, int limit) {
		Result<BidDAO[]> r = db.getObjs(offset, limit, Filter.eq("auctionId", auctionId), BidDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		return Result.ok(Arrays.stream(r.value()).map(BidDAO::toBid).toArray(Bid[]::new));
	}

	public Result<Bid> get(String auctionId, String bidId) {
		Result<BidDAO> r = db.getObj(bidId, auctionId, BidDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		if (!auctionId.equals(r.value().getAuctionId()))
			return Result.error(404);
		return Result.ok(r.value().toBid());
	}
}
