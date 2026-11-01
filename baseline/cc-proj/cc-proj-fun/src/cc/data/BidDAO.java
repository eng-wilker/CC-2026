package cc.data;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

public class BidDAO extends DBTypeDAO {
	private String auctionId;
	private String user;
	private float value;
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
	private Date time;

	public BidDAO() {
	}

	public BidDAO(Bid b) {
		this(b.getId(), b.getAuctionId(), b.getUser(), b.getValue(), b.getTime());
	}

	public BidDAO(String id, String auctionId, String user, float value, Date time) {
		super(id);
		this.auctionId = auctionId;
		this.user = user;
		this.value = value;
		this.time = time;
	}

	public Bid toBid() {
		return new Bid(getId(), auctionId, user, value, time);
	}

	@Override
	public String getPartKey() {
		return auctionId;
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
		return "BidDAO [" + super.toString() + ", auctionId=" + auctionId + ", user=" + user
				+ ", value=" + value + "]";
	}
}
