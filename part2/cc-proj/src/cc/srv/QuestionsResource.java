package cc.srv;

import cc.data.Question;
import cc.ops.QuestionOps;
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
 * REST endpoints for questions. The logic is in cc.ops.QuestionOps.
 */
@Path("/auction/{auctionId}/question")
public class QuestionsResource {

	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Question ask(@PathParam("auctionId") String auctionId, Question question) {
		return QuestionOps.getInstance().ask(auctionId, question).resultOrThrow();
	}

	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public Question[] list(@PathParam("auctionId") String auctionId,
			@DefaultValue("0") @QueryParam("st") int offset,
			@DefaultValue("20") @QueryParam("len") int limit) {
		return QuestionOps.getInstance().list(auctionId, offset, limit).resultOrThrow();
	}

	@POST
	@Path("/{questionId}/reply")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Question reply(@CookieParam(UsersResource.COOKIE_NAME) Cookie session,
			@PathParam("auctionId") String auctionId, @PathParam("questionId") String questionId,
			Question body) {
		return QuestionOps.getInstance()
				.reply(UsersResource.sessionId(session), auctionId, questionId, body).resultOrThrow();
	}
}
