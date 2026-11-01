package cc.data;

public class SessionDAO extends DBTypeDAO {
	private String user;

	public SessionDAO() {
	}

	public SessionDAO(String id, String user) {
		super(id);
		this.user = user;
	}

	public Session toSession() {
		return new Session(getId(), user);
	}

	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}

	@Override
	public String toString() {
		return "SessionDAO [" + super.toString() + ", user=" + user + "]";
	}
}
