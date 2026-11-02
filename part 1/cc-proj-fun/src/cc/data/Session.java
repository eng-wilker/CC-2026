package cc.data;

public class Session {
	private String id;
	private String user;

	public Session() {
	}

	public Session(String id, String user) {
		this.id = id;
		this.user = user;
	}

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}
}
