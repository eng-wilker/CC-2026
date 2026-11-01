package cc.srv;

import cc.data.Auction;
import cc.ops.AuctionOps;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;

/**
 * REST endpoints for auctions. The logic is in cc.ops.AuctionOps.
 */
@Path("/auction")
public class AuctionsResource {

	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Auction create(@CookieParam(UsersResource.COOKIE_NAME) Cookie session, Auction auction) {
		return AuctionOps.getInstance()
				.create(UsersResource.sessionId(session), auction).resultOrThrow();
	}

	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Auction get(@PathParam("id") String id) {
		return AuctionOps.getInstance().get(id).resultOrThrow();
	}

	@PUT
	@Path("/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Auction update(@CookieParam(UsersResource.COOKIE_NAME) Cookie session,
			@PathParam("id") String id, Auction auction) {
		return AuctionOps.getInstance()
				.update(UsersResource.sessionId(session), id, auction).resultOrThrow();
	}

	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public Auction[] list(@DefaultValue("0") @QueryParam("st") int offset,
			@DefaultValue("20") @QueryParam("len") int limit) {
		return AuctionOps.getInstance().list(offset, limit).resultOrThrow();
	}

	@GET
	@Path("/any/recent")
	@Produces(MediaType.APPLICATION_JSON)
	public Auction[] recent(@DefaultValue("0") @QueryParam("st") int offset,
			@DefaultValue("20") @QueryParam("len") int limit) {
		return AuctionOps.getInstance().recent(offset, limit).resultOrThrow();
	}

	@GET
	@Path("/any/about-to-close")
	@Produces(MediaType.APPLICATION_JSON)
	public Auction[] aboutToClose(@DefaultValue("0") @QueryParam("st") int offset,
			@DefaultValue("20") @QueryParam("len") int limit) {
		return AuctionOps.getInstance().aboutToClose(offset, limit).resultOrThrow();
	}

	@GET
	@Path("/any/popular")
	@Produces(MediaType.APPLICATION_JSON)
	public Auction[] popular(@DefaultValue("0") @QueryParam("st") int offset,
			@DefaultValue("20") @QueryParam("len") int limit) {
		return AuctionOps.getInstance().popular(offset, limit).resultOrThrow();
	}

	@GET
	@Path("/search")
	@Produces(MediaType.APPLICATION_JSON)
	public Auction[] search(@QueryParam("q") String query,
			@DefaultValue("0") @QueryParam("st") int offset,
			@DefaultValue("20") @QueryParam("len") int limit) {
		return AuctionOps.getInstance().search(query, offset, limit).resultOrThrow();
	}
}
