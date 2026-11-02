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

/** HTTP functions for users: same routes as UsersResource in cc-proj. */
public class UsersFunctions {

	@FunctionName("user-auth")
	public HttpResponseMessage auth(
			@HttpTrigger(name = "req", methods = { HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS, route = "user/auth") HttpRequestMessage<Optional<String>> req,
			final ExecutionContext ctx) {
		return Http.login(req, () -> UserOps.getInstance().auth(Http.body(req, Login.class)));
	}

	@FunctionName("user-create")
	public HttpResponseMessage create(
			@HttpTrigger(name = "req", methods = { HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS, route = "user") HttpRequestMessage<Optional<String>> req,
			final ExecutionContext ctx) {
		return Http.json(req, () -> UserOps.getInstance().create(Http.body(req, User.class)));
	}

	@FunctionName("user-get")
	public HttpResponseMessage get(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS, route = "user/{id}") HttpRequestMessage<Optional<String>> req,
			@BindingName("id") String id, final ExecutionContext ctx) {
		return Http.json(req, () -> UserOps.getInstance().get(id));
	}

	@FunctionName("user-update")
	public HttpResponseMessage update(
			@HttpTrigger(name = "req", methods = { HttpMethod.PUT }, authLevel = AuthorizationLevel.ANONYMOUS, route = "user/{id}") HttpRequestMessage<Optional<String>> req,
			@BindingName("id") String id, final ExecutionContext ctx) {
		return Http.json(req, () -> UserOps.getInstance().update(Http.sessionId(req), id, Http.body(req, User.class)));
	}

	@FunctionName("user-delete")
	public HttpResponseMessage delete(
			@HttpTrigger(name = "req", methods = { HttpMethod.DELETE }, authLevel = AuthorizationLevel.ANONYMOUS, route = "user/{id}") HttpRequestMessage<Optional<String>> req,
			@BindingName("id") String id, final ExecutionContext ctx) {
		return Http.empty(req, () -> UserOps.getInstance().delete(Http.sessionId(req), id));
	}

	@FunctionName("user-auctions")
	public HttpResponseMessage auctions(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS, route = "user/{id}/auctions") HttpRequestMessage<Optional<String>> req,
			@BindingName("id") String id, final ExecutionContext ctx) {
		return Http.json(req, () -> UserOps.getInstance().auctions(id, Http.param(req, "status", ""),
				Http.offset(req), Http.limit(req)));
	}
}
