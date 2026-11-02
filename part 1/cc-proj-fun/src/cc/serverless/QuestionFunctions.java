package cc.serverless;

import java.util.Optional;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import cc.data.*;
import cc.ops.*;

/** HTTP functions for questions: same routes as QuestionsResource in cc-proj. */
public class QuestionFunctions {

	@FunctionName("question-ask")
	public HttpResponseMessage ask(
			@HttpTrigger(name = "req", methods = { HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction/{auctionId}/question") HttpRequestMessage<Optional<String>> req,
			@BindingName("auctionId") String auctionId, final ExecutionContext ctx) {
		return Http.json(req, () -> QuestionOps.getInstance().ask(auctionId, Http.body(req, Question.class)));
	}

	@FunctionName("question-list")
	public HttpResponseMessage list(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction/{auctionId}/question") HttpRequestMessage<Optional<String>> req,
			@BindingName("auctionId") String auctionId, final ExecutionContext ctx) {
		return Http.json(req, () -> QuestionOps.getInstance().list(auctionId, Http.offset(req), Http.limit(req)));
	}

	@FunctionName("question-reply")
	public HttpResponseMessage reply(
			@HttpTrigger(name = "req", methods = { HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction/{auctionId}/question/{questionId}/reply") HttpRequestMessage<Optional<String>> req,
			@BindingName("auctionId") String auctionId, @BindingName("questionId") String questionId,
			final ExecutionContext ctx) {
		return Http.json(req, () -> QuestionOps.getInstance().reply(Http.sessionId(req), auctionId, questionId,
				Http.body(req, Question.class)));
	}
}
