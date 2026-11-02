package cc.data;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.JsonNode;

import cc.db.CosmosDBLayer;
import cc.db.Filter;
import cc.utils.Result;

/**
 * Access to the stored data, used by cc.ops.
 *
 * Turns CosmosException into a Result with the status code, so that cc.ops never needs to
 * catch anything. Errors here are always Result.error(int), never an exception: the Functions
 * version reads the code, the REST version turns it into a response.
 */
public class DataLayer {
	private static DataLayer instance;

	public static synchronized DataLayer getInstance() {
		if (instance != null)
			return instance;
		instance = new DataLayer(CosmosDBLayer.getInstance());
		return instance;
	}

	private final CosmosDBLayer cosmos;

	public DataLayer(CosmosDBLayer cosmos) {
		this.cosmos = cosmos;
	}

	public <T extends DBTypeDAO> Result<T> postObj(T obj) {
		try {
			CosmosItemResponse<T> r = cosmos.postObj(obj);
			return r.getStatusCode() < 300 ? Result.ok(r.getItem()) : Result.error(r.getStatusCode());
		} catch (CosmosException e) {
			return Result.error(e.getStatusCode());
		}
	}

	public <T extends DBTypeDAO> Result<T> updateObj(T obj) {
		try {
			CosmosItemResponse<T> r = cosmos.updateObj(obj);
			return r.getStatusCode() < 300 ? Result.ok(r.getItem()) : Result.error(r.getStatusCode());
		} catch (CosmosException e) {
			return Result.error(e.getStatusCode());
		}
	}

	public <T extends DBTypeDAO> Result<Void> delObj(String id, String partKey, Class<T> clazz) {
		try {
			CosmosItemResponse<Object> r = cosmos.deleteObj(id, partKey, clazz);
			return r.getStatusCode() < 300 ? Result.ok() : Result.error(r.getStatusCode());
		} catch (CosmosException e) {
			return Result.error(e.getStatusCode());
		}
	}

	public <T extends DBTypeDAO> Result<T> getObjById(String id, Class<T> clazz) {
		try {
			for (T obj : cosmos.getObjById(id, clazz))
				return Result.ok(obj);
		} catch (CosmosException e) {
			return Result.error(e.getStatusCode());
		}
		return Result.error(404);
	}

	public <T extends DBTypeDAO> Result<T[]> getObjs(int offset, int limit, Filter filter, Class<T> clazz) {
		return collect(() -> cosmos.getObjs(offset, limit, filter, clazz), clazz);
	}

	public <T extends DBTypeDAO> Result<T[]> searchObjs(String[] fields, String query, int offset, int limit,
			Class<T> clazz) {
		return collect(() -> cosmos.searchObjs(fields, query, offset, limit, clazz), clazz);
	}

	/** Number of stored objects for each distinct value of a field, in a single query. */
	public <T extends DBTypeDAO> Result<Map<String, Integer>> countByField(String field, Class<T> clazz) {
		try {
			Map<String, Integer> counts = new HashMap<>();
			for (JsonNode n : cosmos.countByField(field, clazz))
				if (n.hasNonNull("k"))
					counts.put(n.get("k").asText(), n.get("n").asInt());
			return Result.ok(counts);
		} catch (CosmosException e) {
			return Result.error(e.getStatusCode());
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends DBTypeDAO> Result<T[]> collect(java.util.function.Supplier<CosmosPagedIterable<T>> q,
			Class<T> clazz) {
		try {
			List<T> res = new ArrayList<>();
			for (T obj : q.get())
				res.add(obj);
			return Result.ok(res.toArray((T[]) Array.newInstance(clazz, 0)));
		} catch (CosmosException e) {
			return Result.error(e.getStatusCode());
		} catch (Exception e) {
			e.printStackTrace();
			return Result.error(500);
		}
	}

	/** The session with this id, or 401. A String, not a Cookie: cc.ops has no web types. */
	public Result<Session> checkSession(String sessionId) {
		if (sessionId == null || sessionId.isEmpty())
			return Result.error(401);
		Result<SessionDAO> r = getObjById(sessionId, SessionDAO.class);
		// As in UserOps.auth: an unknown session is a 401, but a database error is not.
		if (!r.isOK())
			return Result.error(r.error() == 404 ? 401 : r.error());
		if (r.value().getUser() == null || r.value().getUser().isEmpty())
			return Result.error(401);
		return Result.ok(r.value().toSession());
	}

	/** As above, but the session must belong to userId. */
	public Result<Session> checkSession(String sessionId, String userId) {
		Result<Session> s = checkSession(sessionId);
		if (!s.isOK())
			return s;
		return s.value().getUser().equals(userId) ? s : Result.<Session>error(403);
	}
}
