package cc.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import cc.utils.AzureProperties;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisException;

public class RedisCache {
	private static final Logger LOGGER = Logger.getLogger(RedisCache.class.getName());
	private static final ObjectMapper MAPPER = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	private static final int OBJECT_TTL_SECONDS = 300;
	private static final int LISTING_TTL_SECONDS = 90;
	private static final int MAX_RECENT_AUCTIONS = 1000;
	private static volatile RedisCache instance;

	interface RedisCommands {
		String get(String key);
		String setex(String key, int seconds, String value);
		long del(String key);
	}

	private final RedisCommands redis;

	public static RedisCache getInstance() {
		if (instance == null) {
			synchronized (RedisCache.class) {
				if (instance == null)
					instance = fromEnvironment();
			}
		}
		return instance;
	}

	private static RedisCache fromEnvironment() {
		String host = System.getenv(AzureProperties.REDIS_HOST);
		String key = System.getenv(AzureProperties.REDIS_KEY);
		String configuredPort = System.getenv(AzureProperties.REDIS_PORT);
		if (host == null || host.isBlank() || key == null || key.isBlank()) {
			LOGGER.warning("Redis cache is disabled because REDIS_HOST or REDIS_KEY is not configured");
			return new RedisCache(null);
		}
		int port = configuredPort == null || configuredPort.isBlank() ? 10000 : Integer.parseInt(configuredPort);
		JedisPooled jedis = new JedisPooled(new HostAndPort(host, port),
				DefaultJedisClientConfig.builder().user("default").password(key).ssl(true).build());
		return new RedisCache(new RedisCommands() {
			@Override
			public String get(String cacheKey) {
				return jedis.get(cacheKey);
			}

			@Override
			public String setex(String cacheKey, int seconds, String value) {
				return jedis.setex(cacheKey, seconds, value);
			}

			@Override
			public long del(String cacheKey) {
				return jedis.del(cacheKey);
			}
		});
	}

	RedisCache(RedisCommands redis) {
		this.redis = redis;
	}

	public <T extends DBTypeDAO> T getObject(String id, Class<T> clazz) {
		return get(objectKey(id, clazz), clazz);
	}

	public void putObject(DBTypeDAO obj) {
		put(objectKey(obj.getId(), obj.getClass()), obj, OBJECT_TTL_SECONDS);
	}

	public void deleteObject(String id, Class<? extends DBTypeDAO> clazz) {
		delete(objectKey(id, clazz));
	}

	public AuctionDAO[] getAuctionList(String name) {
		return get("listing:" + name, AuctionDAO[].class);
	}

	public void putAuctionList(String name, AuctionDAO[] auctions) {
		put("listing:" + name, auctions, LISTING_TTL_SECONDS);
	}

	public void addRecentAuction(AuctionDAO auction) {
		updateRecentAuction(auction, true);
	}

	public void updateRecentAuction(AuctionDAO auction) {
		updateRecentAuction(auction, false);
	}

	private void updateRecentAuction(AuctionDAO auction, boolean addToFront) {
		AuctionDAO[] recent = getAuctionList("recent");
		if (recent == null)
			return;
		List<AuctionDAO> updated = new ArrayList<>();
		if (addToFront && AuctionDAO.OPEN.equals(auction.getStatus()))
			updated.add(auction);
		boolean found = Arrays.stream(recent).anyMatch(existing -> existing.getId().equals(auction.getId()));
		Arrays.stream(recent)
				.forEach(existing -> {
					if (existing.getId().equals(auction.getId())) {
						if (!addToFront && AuctionDAO.OPEN.equals(auction.getStatus()))
							updated.add(auction);
					} else if (updated.size() < MAX_RECENT_AUCTIONS) {
						updated.add(existing);
					}
				});
		if (!addToFront && !found && AuctionDAO.OPEN.equals(auction.getStatus()))
			return;
		putAuctionList("recent", updated.toArray(AuctionDAO[]::new));
	}

	public void invalidateAuctionListings() {
		delete("listing:recent");
		delete("listing:popular");
		delete("listing:about-to-close");
	}

	private <T> T get(String key, Class<T> clazz) {
		if (redis == null)
			return null;
		try {
			String value = redis.get(key);
			return value == null ? null : MAPPER.readValue(value, clazz);
		} catch (JsonProcessingException | JedisException e) {
			LOGGER.log(Level.WARNING, "Unable to read Redis cache key " + key, e);
			delete(key);
			return null;
		}
	}

	private void put(String key, Object value, int ttlSeconds) {
		if (redis == null)
			return;
		try {
			redis.setex(key, ttlSeconds, MAPPER.writeValueAsString(value));
		} catch (JsonProcessingException | JedisException e) {
			LOGGER.log(Level.WARNING, "Unable to write Redis cache key " + key, e);
			delete(key);
		}
	}

	private void delete(String key) {
		if (redis == null)
			return;
		try {
			redis.del(key);
		} catch (JedisException e) {
			LOGGER.log(Level.WARNING, "Unable to invalidate Redis cache key " + key, e);
		}
	}

	private static String objectKey(String id, Class<?> clazz) {
		return "object:" + clazz.getSimpleName() + ":" + id;
	}
}
