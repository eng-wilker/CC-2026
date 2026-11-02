package cc.srv;

import cc.data.Bid;
import cc.ops.BidOps;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;

/**
 * REST endpoints for bids. The logic is in cc.ops.BidOps.
 */
@Path("/auction/{auctionId}/bid")
public class BidsResource {

	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Bid create(@CookieParam(UsersResource.COOKIE_NAME) Cookie session,
			@PathParam("auctionId") String auctionId, Bid bid) {
		return BidOps.getInstance()
				.create(UsersResource.sessionId(session), auctionId, bid).resultOrThrow();
	}

	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public Bid[] list(@PathParam("auctionId") String auctionId,
			@DefaultValue("0") @QueryParam("st") int offset,
			@DefaultValue("20") @QueryParam("len") int limit) {
		return BidOps.getInstance().list(auctionId, offset, limit).resultOrThrow();
	}

	@GET
	@Path("/{bidId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Bid get(@PathParam("auctionId") String auctionId, @PathParam("bidId") String bidId) {
		return BidOps.getInstance().get(auctionId, bidId).resultOrThrow();
	}
}
