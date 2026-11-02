package cc.db;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.JsonNode;

import cc.data.AuctionDAO;
import cc.data.BidDAO;
import cc.data.DBTypeDAO;
import cc.data.MediaDAO;
import cc.data.QuestionDAO;
import cc.data.SessionDAO;
import cc.data.UserDAO;
import cc.utils.AzureProperties;

public class CosmosDBLayer {
	private static final String CONNECTION_URL = System.getenv(AzureProperties.COSMOSDB_URL);
	private static final String DB_KEY = System.getenv(AzureProperties.COSMOSDB_KEY);
	private static final String DB_NAME = System.getenv(AzureProperties.COSMOSDB_DATABASE);

	private static CosmosDBLayer instance;

	public static synchronized CosmosDBLayer getInstance() {
		if (instance != null)
			return instance;
		CosmosClient client = new CosmosClientBuilder()
				.endpoint(CONNECTION_URL)
				.key(DB_KEY)
				.gatewayMode()
				// L2: the same consistency level for every operation
				.consistencyLevel(ConsistencyLevel.SESSION)
				.connectionSharingAcrossClientsEnabled(true)
				.contentResponseOnWriteEnabled(true)
				.buildClient();
		instance = new CosmosDBLayer(client);
		return instance;
	}

	private final CosmosClient client;
	private CosmosDatabase db;
	private Map<Class<?>, CosmosContainer> containersClass;

	public CosmosDBLayer(CosmosClient client) {
		this.client = client;
	}

	private synchronized void init() {
		if (db != null)
			return;
		db = client.getDatabase(DB_NAME);
		containersClass = new HashMap<>();
		containersClass.put(UserDAO.class, db.getContainer("users"));
		containersClass.put(MediaDAO.class, db.getContainer("media"));
		containersClass.put(AuctionDAO.class, db.getContainer("auctions"));
		containersClass.put(BidDAO.class, db.getContainer("bids"));
		containersClass.put(QuestionDAO.class, db.getContainer("questions"));
		containersClass.put(SessionDAO.class, db.getContainer("sessions"));
	}

	private CosmosContainer container(Class<?> clazz) {
		init();
		CosmosContainer c = containersClass.get(clazz);
		if (c == null)
			throw new IllegalArgumentException("Unknown class type: " + clazz);
		return c;
	}

	public <T extends DBTypeDAO> CosmosItemResponse<T> postObj(T obj) {
		if (obj.getId() == null)
			obj.setId(UUID.randomUUID().toString());
		return container(obj.getClass()).createItem(obj, new PartitionKey(obj.getPartKey()),
				new CosmosItemRequestOptions());
	}

	public <T extends DBTypeDAO> CosmosItemResponse<T> updateObj(T obj) {
		return container(obj.getClass()).replaceItem(obj, obj.getId(),
				new PartitionKey(obj.getPartKey()), new CosmosItemRequestOptions());
	}

	public <T extends DBTypeDAO> CosmosItemResponse<Object> deleteObj(String id, String partKey, Class<T> clazz) {
		return container(clazz).deleteItem(id, new PartitionKey(partKey), new CosmosItemRequestOptions());
	}

	public <T extends DBTypeDAO> CosmosPagedIterable<T> getObjById(String id, Class<T> clazz) {
		CosmosContainer c = container(clazz);
		String t = c.getId();
		return c.queryItems("SELECT * FROM " + t + " WHERE " + t + ".id=\"" + id + "\"",
				new CosmosQueryRequestOptions(), clazz);
	}

	public <T extends DBTypeDAO> CosmosPagedIterable<T> getObjs(int offset, int limit, Class<T> clazz) {
		return getObjs(offset, limit, null, clazz);
	}

	/**
	 * List of elemnts, with a given filter - paged using offset and limit.	 */
	public <T extends DBTypeDAO> CosmosPagedIterable<T> getObjs(int offset, int limit, Filter filter, Class<T> clazz) {
		CosmosContainer c = container(clazz);
		String t = c.getId();
		StringBuilder q = new StringBuilder("SELECT * FROM " + t);
		if (filter != null)
			q.append(" WHERE ").append(filter.toQueryString(t));
		// Newest first, with or without a filter. Callers that read just the first element -
		// the top bid of an auction, say - depend on this: an unindexed order gives back the
		// oldest item instead, which for bids is the lowest one.
		q.append(" ORDER BY ").append(t).append("._ts DESC");
		if (offset >= 0 && limit > 0)
			q.append(" OFFSET ").append(offset).append(" LIMIT ").append(limit);
		return c.queryItems(q.toString(), new CosmosQueryRequestOptions(), clazz);
	}

	/**
	 * How many items there are for each distinct value of a field, counted by the database in a
	 * single query. Cosmos DB has no ORDER BY for a GROUP BY, so the caller sorts the result.
	 */
	public <T extends DBTypeDAO> CosmosPagedIterable<JsonNode> countByField(String field, Class<T> clazz) {
		CosmosContainer c = container(clazz);
		String t = c.getId();
		return c.queryItems("SELECT " + t + "." + field + " AS k, COUNT(1) AS n FROM " + t
				+ " GROUP BY " + t + "." + field, new CosmosQueryRequestOptions(), JsonNode.class);
	}

	/**
	 * Search using CONTAINS. This will be revisited in lab 5.
	 */
	public <T extends DBTypeDAO> CosmosPagedIterable<T> searchObjs(String[] fields, String query, int offset,
			int limit, Class<T> clazz) {
		CosmosContainer c = container(clazz);
		String t = c.getId();
		String safe = query.replace("\"", "");
		StringBuilder q = new StringBuilder("SELECT * FROM " + t + " WHERE ");
		for (int i = 0; i < fields.length; i++) {
			if (i > 0)
				q.append(" OR ");
			q.append("CONTAINS(").append(t).append(".").append(fields[i]).append(", \"").append(safe).append("\", true)");
		}
		if (offset >= 0 && limit > 0)
			q.append(" OFFSET ").append(offset).append(" LIMIT ").append(limit);
		return c.queryItems(q.toString(), new CosmosQueryRequestOptions(), clazz);
	}

	public void close() {
		client.close();
	}
}
