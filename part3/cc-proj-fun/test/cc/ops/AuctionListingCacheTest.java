package cc.ops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import cc.data.Auction;
import cc.data.AuctionDAO;
import cc.data.DataLayer;
import cc.utils.Result;

class AuctionListingCacheTest {
	@Test
	void recentListingUsesCachedSnapshotAndAppliesPagination() {
		DataLayer db = mock(DataLayer.class);
		AuctionDAO first = auction("first");
		AuctionDAO second = auction("second");
		when(db.getCachedAuctionList("recent")).thenReturn(new AuctionDAO[] { first, second });

		Result<Auction[]> result = new AuctionOps(db).recent(1, 1);

		assertTrue(result.isOK());
		assertEquals(1, result.value().length);
		assertEquals("second", result.value()[0].getId());
		verify(db, never()).getInformationalObjs(anyInt(), anyInt(), any(), eq(AuctionDAO.class));
	}

	@Test
	void refreshesAllListingSnapshotsFromCosmosData() {
		DataLayer db = mock(DataLayer.class);
		AuctionDAO open = auction("open");
		AuctionDAO closed = auction("closed");
		closed.setStatus(AuctionDAO.CLOSED);
		when(db.getInformationalObjs(0, 1000, null, AuctionDAO.class))
				.thenReturn(Result.ok(new AuctionDAO[] { open, closed }));
		when(db.countByField("auctionId", cc.data.BidDAO.class)).thenReturn(Result.ok(java.util.Map.of()));

		Result<Void> refreshed = new AuctionOps(db).refreshListingCaches();

		assertTrue(refreshed.isOK());
		verify(db).putCachedAuctionList(eq("recent"), argThat(auctions -> auctions.length == 1 && auctions[0] == open));
		verify(db).putCachedAuctionList(eq("about-to-close"), argThat(auctions -> auctions.length == 0));
		verify(db).putCachedAuctionList(eq("popular"), argThat(auctions -> auctions.length == 1 && auctions[0] == open));
	}

	private static AuctionDAO auction(String id) {
		AuctionDAO auction = new AuctionDAO();
		auction.setId(id);
		auction.setTitle(id);
		auction.setStatus(AuctionDAO.OPEN);
		return auction;
	}
}
