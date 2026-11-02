package cc.data;

public class UserDAO extends DBTypeDAO {
	private String name;
	private String pwd;
	private String photoId;

	public UserDAO() {
	}

	public UserDAO(User u) {
		this(u.getId(), u.getName(), u.getPwd(), u.getPhotoId());
	}

	public UserDAO(String id, String name, String pwd, String photoId) {
		super(id);
		this.name = name;
		this.pwd = pwd;
		this.photoId = photoId;
	}

	/** The password hash is not sent to clients. */
	public User toUser() {
		return new User(getId(), name, "", photoId);
	}

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getPwd() {
		return pwd;
	}
	public void setPwd(String pwd) {
		this.pwd = pwd;
	}
	public String getPhotoId() {
		return photoId;
	}
	public void setPhotoId(String photoId) {
		this.photoId = photoId;
	}

	@Override
	public String toString() {
		return "UserDAO [" + super.toString() + ", name=" + name + ", photoId=" + photoId + "]";
	}
}
