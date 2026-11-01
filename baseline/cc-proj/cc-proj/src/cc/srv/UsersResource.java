package cc.srv;

import cc.data.Auction;
import cc.data.Login;
import cc.data.User;
import cc.ops.UserOps;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoints for users. The logic is in cc.ops.UserOps.
 */
@Path("/user")
public class UsersResource {

	public static final String COOKIE_NAME = "cc:session";

	/** The session id in the request, or null. */
	public static String sessionId(Cookie session) {
		return session == null ? null : session.getValue();
	}

	@POST
	@Path("/auth")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response auth(Login login) {
		String sid = UserOps.getInstance().auth(login).resultOrThrow();
		NewCookie cookie = new NewCookie.Builder(COOKIE_NAME)
				.value(sid).path("/").comment("sessionid").maxAge(3600)
				.secure(false).httpOnly(true).build();
		return Response.ok().cookie(cookie).build();
	}

	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public User create(User user) {
		return UserOps.getInstance().create(user).resultOrThrow();
	}

	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public User get(@PathParam("id") String id) {
		return UserOps.getInstance().get(id).resultOrThrow();
	}

	@PUT
	@Path("/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public User update(@CookieParam(COOKIE_NAME) Cookie session, @PathParam("id") String id, User user) {
		return UserOps.getInstance().update(sessionId(session), id, user).resultOrThrow();
	}

	@GET
	@Path("/{id}/auctions")
	@Produces(MediaType.APPLICATION_JSON)
	public Auction[] auctions(@PathParam("id") String id,
			@DefaultValue("") @QueryParam("status") String status,
			@DefaultValue("0") @QueryParam("st") int offset,
			@DefaultValue("20") @QueryParam("len") int limit) {
		return UserOps.getInstance().auctions(id, status, offset, limit).resultOrThrow();
	}

	@DELETE
	@Path("/{id}")
	public Response delete(@CookieParam(COOKIE_NAME) Cookie session, @PathParam("id") String id) {
		UserOps.getInstance().delete(sessionId(session), id).resultOrThrow();
		return Response.ok().build();
	}
}
