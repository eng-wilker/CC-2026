package cc.ops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import cc.data.AuctionDAO;
import cc.data.BidDAO;
import cc.data.DataLayer;
import cc.data.Session;
import cc.data.UserDAO;
import cc.db.Filter;
import cc.utils.Result;

class UserOpsTest {
	@Test
	void deletingUserRetriesAuctionConflicts() {
		DataLayer db = mock(DataLayer.class);
		AuctionDAO auction = new AuctionDAO();
		auction.setId("auction");
		auction.setOwner("user");
		AuctionDAO latest = new AuctionDAO();
		latest.setId("auction");
		latest.setOwner("user");
		when(db.checkSession("session", "user")).thenReturn(Result.ok(new Session()));
		when(db.getObjById("user", UserDAO.class)).thenReturn(Result.ok(new UserDAO()));
		when(db.getObjs(eq(0), eq(1000), any(Filter.class), eq(AuctionDAO.class)))
				.thenReturn(Result.ok(new AuctionDAO[] { auction }));
		when(db.getAuctionForUpdate("auction")).thenReturn(Result.ok(auction), Result.ok(latest));
		when(db.updateObj(any(AuctionDAO.class))).thenReturn(Result.error(412), Result.ok(latest));
		when(db.getObjs(eq(0), eq(1000), any(Filter.class), eq(BidDAO.class)))
				.thenReturn(Result.ok(new BidDAO[0]));
		when(db.delObj("user", "user", UserDAO.class)).thenReturn(Result.ok());
		assertTrue(new UserOps(db).delete("session", "user").isOK());
		verify(db, times(2)).getAuctionForUpdate("auction");
		assertEquals(UserOps.DELETED_USER, latest.getOwner());
	}
}