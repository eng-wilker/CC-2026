package cc.ops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cc.data.AuctionDAO;
import cc.data.Bid;
import cc.data.BidDAO;
import cc.data.DataLayer;
import cc.data.Session;
import cc.utils.Result;

class BidOpsTest {
	private DataLayer db;
	private BidOps ops;

	@BeforeEach
	void setUp() {
		db = mock(DataLayer.class);
		ops = new BidOps(db);
		when(db.checkSession("session", "bidder")).thenReturn(Result.ok(new Session()));
		when(db.postObj(any(BidDAO.class))).thenAnswer(invocation -> {
			BidDAO bid = invocation.getArgument(0);
			bid.setId("bid-id");
			return Result.ok(bid);
		});
		when(db.delObj(anyString(), anyString(), eq(BidDAO.class))).thenReturn(Result.ok());
	}

	private AuctionDAO auction(float winning) {
		AuctionDAO auction = new AuctionDAO();
		auction.setId("auction");
		auction.setStatus(AuctionDAO.OPEN);
		auction.setEndTime(new Date(System.currentTimeMillis() + 60000));
		auction.setMinimumPrice(1);
		auction.setEtag("etag");
		if (winning > 0) auction.setWinnerBid(new Bid("old", "auction", "other", winning, new Date()));
		return auction;
	}

	private Bid bid() {
		return new Bid(null, null, "bidder", 10, null);
	}

	@Test
	void acceptsValidBid() {
		when(db.getAuctionForUpdate("auction")).thenReturn(Result.ok(auction(0)));
		when(db.updateObj(any(AuctionDAO.class))).thenAnswer(invocation -> Result.ok(invocation.getArgument(0)));
		Result<Bid> result = ops.create("session", "auction", bid());
		assertTrue(result.isOK());
		assertEquals("bid-id", result.value().getId());
		verify(db, never()).delObj(anyString(), anyString(), eq(BidDAO.class));
	}

	@Test
	void conflictRevalidatesAndRemovesLosingBid() {
		when(db.getAuctionForUpdate("auction")).thenReturn(Result.ok(auction(0)), Result.ok(auction(20)));
		when(db.updateObj(any(AuctionDAO.class))).thenReturn(Result.error(412));
		Result<Bid> result = ops.create("session", "auction", bid());
		assertEquals(403, result.error());
		verify(db).delObj("bid-id", "auction", BidDAO.class);
		verify(db, times(2)).getAuctionForUpdate("auction");
	}

	@Test
	void contentionStopsAfterThreeAttempts() {
		when(db.getAuctionForUpdate("auction")).thenAnswer(invocation -> Result.ok(auction(0)));
		when(db.updateObj(any(AuctionDAO.class))).thenReturn(Result.error(412));
		assertEquals(409, ops.create("session", "auction", bid()).error());
		verify(db, times(3)).updateObj(any(AuctionDAO.class));
		verify(db).delObj("bid-id", "auction", BidDAO.class);
	}

	@Test
	void uncertainWriteDoesNotDeleteBid() {
		when(db.getAuctionForUpdate("auction")).thenReturn(Result.ok(auction(0)));
		when(db.updateObj(any(AuctionDAO.class))).thenReturn(Result.error(503));
		assertEquals(503, ops.create("session", "auction", bid()).error());
		verify(db, never()).delObj(anyString(), anyString(), eq(BidDAO.class));
	}

	@Test
	void invalidBidDoesNotWrite() {
		when(db.getAuctionForUpdate("auction")).thenReturn(Result.ok(auction(20)));
		assertEquals(403, ops.create("session", "auction", bid()).error());
		verify(db, never()).postObj(any(BidDAO.class));
	}
}