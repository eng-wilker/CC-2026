package cc.utils;

import java.util.List;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.fasterxml.jackson.databind.JsonNode;

public class ReadComparison {
	public static void main(String[] args) {
		if (args.length != 4) {
			System.err.println("Usage: ReadComparison <container> <id> <partition-key> <iterations>");
			System.exit(1);
		}
		int iterations = Integer.parseInt(args[3]);
		if (iterations < 1)
			throw new IllegalArgumentException("iterations must be positive");
		try (CosmosClient client = new CosmosClientBuilder()
				.endpoint(System.getenv(AzureProperties.COSMOSDB_URL))
				.key(System.getenv(AzureProperties.COSMOSDB_KEY)).gatewayMode().buildClient()) {
			CosmosContainer container = client.getDatabase(System.getenv(AzureProperties.COSMOSDB_DATABASE))
					.getContainer(args[0]);
			PartitionKey partition = new PartitionKey(args[2]);
			SqlQuerySpec query = new SqlQuerySpec("SELECT * FROM c WHERE c.id = @id",
					List.of(new SqlParameter("@id", args[1])));
			System.out.println("iteration,operation,latency_ms,request_units,documents");
			for (int iteration = 1; iteration <= iterations; iteration++) {
				long start = System.nanoTime();
				CosmosItemResponse<JsonNode> item = container.readItem(args[1], partition, JsonNode.class);
				System.out.printf(java.util.Locale.ROOT, "%d,point,%.3f,%.3f,1%n", iteration,
						(System.nanoTime() - start) / 1_000_000.0, item.getRequestCharge());
				start = System.nanoTime();
				double charge = 0;
				int count = 0;
				CosmosQueryRequestOptions options = new CosmosQueryRequestOptions().setPartitionKey(partition);
				for (FeedResponse<JsonNode> page : container.queryItems(query, options, JsonNode.class).iterableByPage()) {
					charge += page.getRequestCharge();
					count += page.getResults().size();
				}
				System.out.printf(java.util.Locale.ROOT, "%d,query,%.3f,%.3f,%d%n", iteration,
						(System.nanoTime() - start) / 1_000_000.0, charge, count);
			}
		}
	}
}