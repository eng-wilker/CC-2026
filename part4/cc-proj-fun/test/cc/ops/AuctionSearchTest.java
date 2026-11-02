package cc.ops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import cc.data.AuctionDAO;
import cc.data.AuctionSearch;
import cc.data.DataLayer;
import cc.utils.Result;

class AuctionSearchTest {
	@Test
	void searchesByIndexAndHydratesFullAuctionRecordsInRankOrder() {
		DataLayer db = mock(DataLayer.class);
		AuctionSearch search = mock(AuctionSearch.class);
		AuctionDAO first = auction("first", "First description");
		AuctionDAO second = auction("second", "Second description");
		when(search.search("cats", 2, 5)).thenReturn(Result.ok(new String[] { "second", "first" }));
		when(db.getObjById("second", AuctionDAO.class)).thenReturn(Result.ok(second));
		when(db.getObjById("first", AuctionDAO.class)).thenReturn(Result.ok(first));

		var result = new AuctionOps(db, search).search("cats", 2, 5);

		assertTrue(result.isOK());
		assertEquals(2, result.value().length);
		assertEquals("second", result.value()[0].getId());
		assertEquals("Second description", result.value()[0].getDescription());
		assertEquals("first", result.value()[1].getId());
		verify(search).search("cats", 2, 5);
	}

	@Test
	void skipsIndexEntriesForAuctionsDeletedAfterTheLastIndexRun() {
		DataLayer db = mock(DataLayer.class);
		AuctionSearch search = mock(AuctionSearch.class);
		AuctionDAO current = auction("current", "Current record");
		when(search.search("cats", 0, 20)).thenReturn(Result.ok(new String[] { "deleted", "current" }));
		when(db.getObjById("deleted", AuctionDAO.class)).thenReturn(Result.error(404));
		when(db.getObjById("current", AuctionDAO.class)).thenReturn(Result.ok(current));

		var result = new AuctionOps(db, search).search("cats", 0, 20);

		assertTrue(result.isOK());
		assertEquals(1, result.value().length);
		assertEquals("current", result.value()[0].getId());
	}

	@Test
	void propagatesSearchAndHydrationErrors() {
		DataLayer db = mock(DataLayer.class);
		AuctionSearch search = mock(AuctionSearch.class);
		when(search.search("cats", 0, 20)).thenReturn(Result.error(503));

		assertEquals(503, new AuctionOps(db, search).search("cats", 0, 20).error());
		verifyNoInteractions(db);

		when(search.search("dogs", 0, 20)).thenReturn(Result.ok(new String[] { "auction" }));
		when(db.getObjById("auction", AuctionDAO.class)).thenReturn(Result.error(500));

		assertEquals(500, new AuctionOps(db, search).search("dogs", 0, 20).error());
	}

	@Test
	void rejectsMissingSearchText() {
		AuctionSearch search = mock(AuctionSearch.class);

		assertEquals(400, new AuctionOps(mock(DataLayer.class), search).search("", 0, 20).error());
		verifyNoInteractions(search);
	}

	private static AuctionDAO auction(String id, String description) {
		AuctionDAO auction = new AuctionDAO();
		auction.setId(id);
		auction.setTitle(id);
		auction.setDescription(description);
		auction.setStatus(AuctionDAO.OPEN);
		return auction;
	}
}
