package cc.data;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

public class Bid {
	private String id;
	private String auctionId;
	private String user;
	private float value;
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
	private Date time;

	public Bid() {
	}

	public Bid(String id, String auctionId, String user, float value, Date time) {
		this.id = id;
		this.auctionId = auctionId;
		this.user = user;
		this.value = value;
		this.time = time;
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
	public float getValue() {
		return value;
	}
	public void setValue(float value) {
		this.value = value;
	}
	public Date getTime() {
		return time;
	}
	public void setTime(Date time) {
		this.time = time;
	}

	@Override
	public String toString() {
		return "Bid [id=" + id + ", auctionId=" + auctionId + ", user=" + user + ", value=" + value + "]";
	}
}
