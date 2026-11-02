package cc.data;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

public class Auction {
	private String id;
	private String title;
	private String imageId;
	private String description;
	private String owner;
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
	private Date endTime;
	private float minimumPrice;
	private String status;
	private Bid winnerBid;

	public Auction() {
	}

	public Auction(String id, String title, String imageId, String description, String owner,
			Date endTime, float minimumPrice, String status, Bid winnerBid) {
		this.id = id;
		this.title = title;
		this.imageId = imageId;
		this.description = description;
		this.owner = owner;
		this.endTime = endTime;
		this.minimumPrice = minimumPrice;
		this.status = status;
		this.winnerBid = winnerBid;
	}

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getImageId() {
		return imageId;
	}
	public void setImageId(String imageId) {
		this.imageId = imageId;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getOwner() {
		return owner;
	}
	public void setOwner(String owner) {
		this.owner = owner;
	}
	public Date getEndTime() {
		return endTime;
	}
	public void setEndTime(Date endTime) {
		this.endTime = endTime;
	}
	public float getMinimumPrice() {
		return minimumPrice;
	}
	public void setMinimumPrice(float minimumPrice) {
		this.minimumPrice = minimumPrice;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public Bid getWinnerBid() {
		return winnerBid;
	}
	public void setWinnerBid(Bid winnerBid) {
		this.winnerBid = winnerBid;
	}

	@Override
	public String toString() {
		return "Auction [id=" + id + ", title=" + title + ", owner=" + owner + ", endTime=" + endTime
				+ ", minimumPrice=" + minimumPrice + ", status=" + status + "]";
	}
}
