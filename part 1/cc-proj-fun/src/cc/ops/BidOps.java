package cc.ops;

import java.util.Arrays;
import java.util.Date;

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

		Result<AuctionDAO> a = db.getObjById(auctionId, AuctionDAO.class);
		if (!a.isOK())
			return Result.error(a.error());
		AuctionDAO auction = a.value();

		if (!AuctionDAO.OPEN.equals(auction.getStatus()))
			return Result.error(403);
		if (auction.getEndTime() != null && auction.getEndTime().before(new Date()))
			return Result.error(403);
		if (bid.getValue() < auction.getMinimumPrice())
			return Result.error(403);
		if (auction.getWinnerBid() != null && bid.getValue() <= auction.getWinnerBid().getValue())
			return Result.error(403);

		bid.setAuctionId(auctionId);
		bid.setTime(new Date());
		Result<BidDAO> r = db.postObj(new BidDAO(bid));
		if (!r.isOK())
			return Result.error(r.error());

		// keep the highest bid on the auction, to avoid scanning the bids when reading it
		auction.setWinnerBid(r.value().toBid());
		// NOTE: the bid is already stored, so this is not atomic - if the update fails the bid
		// exists but is not the winner yet. Report the error instead of dropping it, otherwise a
		// throttled update silently lets the next, lower bid win.
		Result<AuctionDAO> w = db.updateObj(auction);
		if (!w.isOK())
			return Result.error(w.error());

		return Result.ok(r.value().toBid());
	}

	public Result<Bid[]> list(String auctionId, int offset, int limit) {
		Result<BidDAO[]> r = db.getObjs(offset, limit, Filter.eq("auctionId", auctionId), BidDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		return Result.ok(Arrays.stream(r.value()).map(BidDAO::toBid).toArray(Bid[]::new));
	}

	public Result<Bid> get(String auctionId, String bidId) {
		Result<BidDAO> r = db.getObjById(bidId, BidDAO.class);
		if (!r.isOK())
			return Result.error(r.error());
		if (!auctionId.equals(r.value().getAuctionId()))
			return Result.error(404);
		return Result.ok(r.value().toBid());
	}
}
