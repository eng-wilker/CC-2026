package cc.data;

public class Media {
	private String id;
	private String contentType;

	public Media() {
	}

	public Media(String id, String contentType) {
		this.id = id;
		this.contentType = contentType;
	}

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getContentType() {
		return contentType;
	}
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}
}
