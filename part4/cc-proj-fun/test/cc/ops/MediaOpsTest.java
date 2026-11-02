package cc.ops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import cc.db.BlobStore;
import cc.db.BlobStore.MediaData;
import cc.utils.Hash;
import cc.utils.Result;

class MediaOpsTest {
	@Test
	void rejectsEmptyContent() {
		BlobStore store = mock(BlobStore.class);
		MediaOps ops = new MediaOps(store);
		assertEquals(400, ops.upload(null, new byte[0]).error());
		verifyNoInteractions(store);
	}

	@Test
	void uploadsUsingExistingHashAndDefaultContentType() {
		BlobStore store = mock(BlobStore.class);
		byte[] contents = { 1, 2, 3 };
		String id = Hash.of(contents);
		when(store.upload(eq(id), eq("application/octet-stream"), any(byte[].class))).thenReturn(Result.ok(id));
		MediaOps ops = new MediaOps(store);
		assertEquals(id, ops.upload(null, contents).value());
		assertEquals(id, ops.upload(null, contents).value());
		verify(store, times(2)).upload(id, "application/octet-stream", contents);
	}

	@Test
	void downloadPreservesBytesAndContentType() {
		BlobStore store = mock(BlobStore.class);
		byte[] contents = { 0, 1, -1, 5 };
		when(store.download("image")).thenReturn(Result.ok(new MediaData("image/png", contents)));
		MediaData image = new MediaOps(store).download("image").value();
		assertArrayEquals(contents, image.contents());
		assertEquals(Hash.of(contents), Hash.of(image.contents()));
		assertEquals("image/png", image.contentType());
	}
}