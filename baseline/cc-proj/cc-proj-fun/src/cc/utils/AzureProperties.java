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
}
