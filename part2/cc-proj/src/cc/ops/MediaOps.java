package cc.ops;

import cc.data.DataLayer;
import cc.data.MediaDAO;
import cc.utils.Hash;
import cc.utils.Result;

/**
 * Media operations. L1: the contents are kept base64 in the document.
 */
public class MediaOps {

	private static MediaOps instance;

	public static synchronized MediaOps getInstance() {
		if (instance == null)
			instance = new MediaOps(DataLayer.getInstance());
		return instance;
	}

	private final DataLayer db;

	public MediaOps(DataLayer db) {
		this.db = db;
	}

	/** The id of an image is its hash, so uploading the same image twice does nothing. */
	public Result<String> upload(String contentType, byte[] contents) {
		if (contents == null || contents.length == 0)
			return Result.error(400);
		String key = Hash.of(contents);
		if (db.getObjById(key, MediaDAO.class).isOK())
			return Result.ok(key);

		// L1: fails at about 1.5MB - base64 of that is over the 2MB document limit
		Result<MediaDAO> r = db.postObj(MediaDAO.of(key, contentType, contents));
		return r.isOK() ? Result.ok(key) : Result.<String>error(r.error());
	}

	public Result<MediaDAO> download(String id) {
		return db.getObjById(id, MediaDAO.class);
	}
}
