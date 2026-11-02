package cc.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RedisCacheTest {
	private final Map<String, String> values = new HashMap<>();
	private final RedisCache cache = new RedisCache(new RedisCache.RedisCommands() {
		@Override
		public String get(String key) {
			return values.get(key);
		}

		@Override
		public String setex(String key, int seconds, String value) {
			values.put(key, value);
			return "OK";
		}

		@Override
		public long del(String key) {
			return values.remove(key) == null ? 0 : 1;
		}
	});

	@Test
	void serializesAndLoadsDataObjects() {
		AuctionDAO auction = auction("auction", "title");
		auction.setEtag("etag");

		cache.putObject(auction);

		AuctionDAO cached = cache.getObject("auction", AuctionDAO.class);
		assertNotNull(cached);
		assertEquals("title", cached.getTitle());
		assertEquals("etag", cached.getEtag());
	}

	@Test
	void updatesAndRemovesAuctionsInRecentListing() {
		cache.putAuctionList("recent", new AuctionDAO[] { auction("first", "old"), auction("second", "second") });
		AuctionDAO updated = auction("first", "new");

		cache.updateRecentAuction(updated);

		AuctionDAO[] recent = cache.getAuctionList("recent");
		assertEquals(2, recent.length);
		assertEquals("first", recent[0].getId());
		assertEquals("new", recent[0].getTitle());

		cache.updateRecentAuction(auction("second", "updated second"));
		recent = cache.getAuctionList("recent");
		assertEquals("second", recent[1].getId());
		assertEquals("updated second", recent[1].getTitle());

		updated.setStatus(AuctionDAO.CLOSED);
		cache.updateRecentAuction(updated);

		recent = cache.getAuctionList("recent");
		assertEquals(1, recent.length);
		assertEquals("second", recent[0].getId());
		assertEquals("updated second", recent[0].getTitle());
	}

	@Test
	void insertsNewAuctionsAtTheFrontOfRecentListing() {
		cache.putAuctionList("recent", new AuctionDAO[] { auction("first", "first") });

		cache.addRecentAuction(auction("new", "new"));

		AuctionDAO[] recent = cache.getAuctionList("recent");
		assertEquals(2, recent.length);
		assertEquals("new", recent[0].getId());
		assertEquals("first", recent[1].getId());
	}

	@Test
	void invalidatesAllAuctionListingSnapshots() {
		cache.putAuctionList("recent", new AuctionDAO[0]);
		cache.putAuctionList("popular", new AuctionDAO[0]);
		cache.putAuctionList("about-to-close", new AuctionDAO[0]);

		cache.invalidateAuctionListings();

		assertNull(cache.getAuctionList("recent"));
		assertNull(cache.getAuctionList("popular"));
		assertNull(cache.getAuctionList("about-to-close"));
	}

	private static AuctionDAO auction(String id, String title) {
		AuctionDAO auction = new AuctionDAO();
		auction.setId(id);
		auction.setTitle(title);
		auction.setStatus(AuctionDAO.OPEN);
		return auction;
	}
}
