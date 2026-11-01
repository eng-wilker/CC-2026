package cc.data;

import java.util.Base64;

/**
 * Images encoded in base64 for storage in the DB.
 * This will be replaced in lab 3 for a better solution.
 */
public class MediaDAO extends DBTypeDAO {
	private String contentType;
	private String contents;

	public MediaDAO() {
	}

	public MediaDAO(String id, String contentType, String contents) {
		super(id);
		this.contentType = contentType;
		this.contents = contents;
	}

	public static MediaDAO of(String id, String contentType, byte[] raw) {
		return new MediaDAO(id, contentType, Base64.getEncoder().encodeToString(raw));
	}

	public byte[] decoded() {
		return Base64.getDecoder().decode(contents);
	}

	public Media toMedia() {
		return new Media(getId(), contentType);
	}

	public String getContentType() {
		return contentType;
	}
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}
	public String getContents() {
		return contents;
	}
	public void setContents(String contents) {
		this.contents = contents;
	}

	@Override
	public String toString() {
		return "MediaDAO [" + super.toString() + ", contentType=" + contentType
				+ ", base64Length=" + (contents == null ? 0 : contents.length()) + "]";
	}
}
