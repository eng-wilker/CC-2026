package cc.utils;

/**
 * Names of the environment variables with the configuration. These are set as application
 * settings on the App Service and on the Functions app.
 */
public class AzureProperties {
	public static final String BLOB_KEY = "BlobStoreConnection";
	public static final String COSMOSDB_KEY = "COSMOSDB_KEY";
	public static final String COSMOSDB_URL = "COSMOSDB_URL";
	public static final String COSMOSDB_DATABASE = "COSMOSDB_DATABASE";
	public static final String REDIS_HOST = "REDIS_HOST";
	public static final String REDIS_PORT = "REDIS_PORT";
	public static final String REDIS_KEY = "REDIS_KEY";
	public static final String SEARCH_URL = "SEARCH_URL";
	public static final String SEARCH_KEY = "SEARCH_KEY";
	public static final String SEARCH_INDEX = "SEARCH_INDEX";
}
