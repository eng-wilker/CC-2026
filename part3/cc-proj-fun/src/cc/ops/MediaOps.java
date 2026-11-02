package cc.ops;

import cc.db.BlobStore;
import cc.db.BlobStore.MediaData;
import cc.utils.Hash;
import cc.utils.Result;

public class MediaOps {

	private static MediaOps instance;

	public static synchronized MediaOps getInstance() {
		if (instance == null)
			instance = new MediaOps(BlobStore.getInstance());
		return instance;
	}

	private final BlobStore blobs;

	public MediaOps(BlobStore blobs) {
		this.blobs = blobs;
	}

	/** The id of an image is its hash, so uploading the same image twice does nothing. */
	public Result<String> upload(String contentType, byte[] contents) {
		if (contents == null || contents.length == 0)
			return Result.error(400);
		String key = Hash.of(contents);
		String type = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
		return blobs.upload(key, type, contents);
	}

	public Result<MediaData> download(String id) {
		return blobs.download(id);
	}
}
