package cc.data;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.util.Context;
import com.azure.search.documents.SearchDocument;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.models.SearchMode;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.SearchResult;

import cc.utils.AzureProperties;
import cc.utils.Result;

public final class AzureAuctionSearch implements AuctionSearch {
	private static final Logger LOGGER = Logger.getLogger(AzureAuctionSearch.class.getName());
	private static volatile AzureAuctionSearch instance;

	interface SearchBackend {
		String[] search(String query, int offset, int limit);
	}

	private final SearchBackend backend;

	public static AzureAuctionSearch getInstance() {
		if (instance == null) {
			synchronized (AzureAuctionSearch.class) {
				if (instance == null)
					instance = new AzureAuctionSearch(createBackend());
			}
		}
		return instance;
	}

	AzureAuctionSearch(SearchBackend backend) {
		this.backend = backend;
	}

	private static SearchBackend createBackend() {
		String endpoint = System.getenv(AzureProperties.SEARCH_URL);
		String key = System.getenv(AzureProperties.SEARCH_KEY);
		String index = System.getenv(AzureProperties.SEARCH_INDEX);
		if (endpoint == null || endpoint.isBlank() || key == null || key.isBlank()
				|| index == null || index.isBlank())
			throw new IllegalStateException("Azure AI Search configuration is incomplete");

		SearchClient client = new SearchClientBuilder()
				.endpoint(endpoint)
				.credential(new AzureKeyCredential(key))
				.indexName(index)
				.buildClient();
		return (query, offset, limit) -> {
			SearchOptions options = new SearchOptions()
					.setSearchFields("title", "description")
					.setSearchMode(SearchMode.ALL)
					.setSkip(offset)
					.setTop(limit);
			List<String> ids = new ArrayList<>();
			for (SearchResult result : client.search(query, options, Context.NONE)) {
				Object id = result.getDocument(SearchDocument.class).get("id");
				if (!(id instanceof String))
					throw new IllegalStateException("Azure AI Search returned a document without a string id");
				ids.add((String) id);
			}
			return ids.toArray(String[]::new);
		};
	}

	@Override
	public Result<String[]> search(String query, int offset, int limit) {
		try {
			return Result.ok(backend.search(query, offset, limit));
		} catch (HttpResponseException e) {
			int status = e.getResponse() == null ? 503 : e.getResponse().getStatusCode();
			LOGGER.log(Level.WARNING, "Azure AI Search request failed with status " + status, e);
			return Result.error(status);
		}
	}
}
