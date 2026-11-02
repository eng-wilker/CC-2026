package cc.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.azure.cosmos.models.CosmosItemResponse;

import cc.db.CosmosDBLayer;
import cc.utils.Result;

class DataLayerCacheTest {
	@Test
	void usesCachedObjectForOrdinaryReads() {
		CosmosDBLayer cosmos = mock(CosmosDBLayer.class);
		RedisCache cache = mock(RedisCache.class);
		AuctionDAO cached = new AuctionDAO();
		cached.setId("auction");
		when(cache.getObject("auction", AuctionDAO.class)).thenReturn(cached);

		Result<AuctionDAO> result = new DataLayer(cosmos, cache).getObjById("auction", AuctionDAO.class);

		assertSame(cached, result.value());
		verifyNoInteractions(cosmos);
	}

	@Test
	void checksSessionsFromCacheForAuthentication() {
		CosmosDBLayer cosmos = mock(CosmosDBLayer.class);
		RedisCache cache = mock(RedisCache.class);
		SessionDAO cached = new SessionDAO("session", "user");
		when(cache.getObject("session", SessionDAO.class)).thenReturn(cached);

		Result<Session> result = new DataLayer(cosmos, cache).checkSession("session", "user");

		assertTrue(result.isOK());
		assertEquals("user", result.value().getUser());
		verifyNoInteractions(cosmos);
	}

	@Test
	void fetchesFreshAuctionAndEtagForUpdatesEvenWhenCached() {
		CosmosDBLayer cosmos = mock(CosmosDBLayer.class);
		RedisCache cache = mock(RedisCache.class);
		AuctionDAO cached = new AuctionDAO();
		cached.setId("auction");
		cached.setEtag("stale");
		AuctionDAO fresh = new AuctionDAO();
		fresh.setId("auction");
		CosmosItemResponse<AuctionDAO> response = mock(CosmosItemResponse.class);
		when(response.getItem()).thenReturn(fresh);
		when(response.getETag()).thenReturn("fresh");
		when(cosmos.readObj("auction", "auction", AuctionDAO.class)).thenReturn(response);

		Result<AuctionDAO> result = new DataLayer(cosmos, cache).getAuctionForUpdate("auction");

		assertTrue(result.isOK());
		assertSame(fresh, result.value());
		assertEquals("fresh", result.value().getEtag());
		verify(cosmos).readObj("auction", "auction", AuctionDAO.class);
		verify(cache).putObject(fresh);
	}
}
