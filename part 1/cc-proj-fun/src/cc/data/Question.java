package cc.data;

public class Question {
	private String id;
	private String auctionId;
	private String user;
	private String text;
	private String reply;

	public Question() {
	}

	public Question(String id, String auctionId, String user, String text, String reply) {
		this.id = id;
		this.auctionId = auctionId;
		this.user = user;
		this.text = text;
		this.reply = reply;
	}

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getAuctionId() {
		return auctionId;
	}
	public void setAuctionId(String auctionId) {
		this.auctionId = auctionId;
	}
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	public String getReply() {
		return reply;
	}
	public void setReply(String reply) {
		this.reply = reply;
	}

	@Override
	public String toString() {
		return "Question [id=" + id + ", auctionId=" + auctionId + ", user=" + user + "]";
	}
}
