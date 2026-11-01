package cc.db;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import org.junit.jupiter.api.Test;

class BlobStoreTest {
	@Test
	void downloadReturnsBytesAndContentType() {
		BlobContainerClient container = mock(BlobContainerClient.class);
		BlobClient blob = mock(BlobClient.class);
		BlobProperties properties = mock(BlobProperties.class);
		byte[] contents = new byte[2048];
		contents[1023] = 42;
		when(container.getBlobClient("id")).thenReturn(blob);
		when(blob.getProperties()).thenReturn(properties);
		when(properties.getContentType()).thenReturn("image/jpeg");
		when(blob.downloadContent()).thenReturn(BinaryData.fromBytes(contents));
		BlobStore.MediaData media = new BlobStore(container).download("id").value();
		assertArrayEquals(contents, media.contents());
		assertEquals("image/jpeg", media.contentType());
	}

	@Test
	void missingBlobReturns404() {
		BlobContainerClient container = mock(BlobContainerClient.class);
		BlobClient blob = mock(BlobClient.class);
		BlobStorageException exception = mock(BlobStorageException.class);
		when(container.getBlobClient("missing")).thenReturn(blob);
		when(blob.getProperties()).thenThrow(exception);
		when(exception.getStatusCode()).thenReturn(404);
		assertEquals(404, new BlobStore(container).download("missing").error());
	}

	@Test
	void duplicateUploadSucceedsWithoutOverwriting() {
		BlobContainerClient container = mock(BlobContainerClient.class);
		BlobClient blob = mock(BlobClient.class);
		BlobStorageException exception = mock(BlobStorageException.class);
		when(container.getBlobClient("id")).thenReturn(blob);
		when(blob.uploadWithResponse(any(BlobParallelUploadOptions.class), eq(Context.NONE))).thenThrow(exception);
		when(exception.getStatusCode()).thenReturn(412);
		assertEquals("id", new BlobStore(container).upload("id", "image/png", new byte[] { 1 }).value());
	}
}