package cc.db;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;

import cc.utils.AzureProperties;
import cc.utils.Result;

public class BlobStore {

	public record MediaData(String contentType, byte[] contents) {}

	private static BlobStore instance;
	private final BlobContainerClient container;

	public static synchronized BlobStore getInstance() {
		if (instance == null) {
			String connection = System.getenv(AzureProperties.BLOB_KEY);
			if (connection == null || connection.isBlank())
				throw new IllegalStateException("BlobStoreConnection is not configured");
			instance = new BlobStore(new BlobServiceClientBuilder().connectionString(connection)
					.buildClient().getBlobContainerClient("media"));
		}
		return instance;
	}

	public BlobStore(BlobContainerClient container) {
		this.container = container;
	}

	public Result<String> upload(String id, String contentType, byte[] contents) {
		try {
			BlobParallelUploadOptions options = new BlobParallelUploadOptions(BinaryData.fromBytes(contents))
					.setHeaders(new BlobHttpHeaders().setContentType(contentType))
					.setRequestConditions(new BlobRequestConditions().setIfNoneMatch("*"));
			container.getBlobClient(id).uploadWithResponse(options, Context.NONE);
			return Result.ok(id);
		} catch (BlobStorageException e) {
			if (e.getStatusCode() == 412 || BlobErrorCode.BLOB_ALREADY_EXISTS.equals(e.getErrorCode()))
				return Result.ok(id);
			return Result.error(e.getStatusCode());
		}
	}

	public Result<MediaData> download(String id) {
		try {
			BlobClient blob = container.getBlobClient(id);
			String contentType = blob.getProperties().getContentType();
			if (contentType == null || contentType.isBlank())
				contentType = "application/octet-stream";
			return Result.ok(new MediaData(contentType, blob.downloadContent().toBytes()));
		} catch (BlobStorageException e) {
			return Result.error(e.getStatusCode());
		}
	}
}