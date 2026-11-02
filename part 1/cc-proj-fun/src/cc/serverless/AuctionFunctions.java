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

/** HTTP functions for auctions: same routes as AuctionsResource in cc-proj. */
public class AuctionFunctions {

	@FunctionName("auction-create")
	public HttpResponseMessage create(
			@HttpTrigger(name = "req", methods = { HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction") HttpRequestMessage<Optional<String>> req,
			final ExecutionContext ctx) {
		return Http.json(req, () -> AuctionOps.getInstance().create(Http.sessionId(req), Http.body(req, Auction.class)));
	}

	@FunctionName("auction-get")
	public HttpResponseMessage get(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction/{id}") HttpRequestMessage<Optional<String>> req,
			@BindingName("id") String id, final ExecutionContext ctx) {
		// "auction/search" and "auction/{id}" have the same shape; if the host routes search
		// here, dispatch it ourselves.
		if ("search".equals(id))
			return Http.json(req, () -> AuctionOps.getInstance().search(Http.param(req, "q", null),
					Http.offset(req), Http.limit(req)));
		return Http.json(req, () -> AuctionOps.getInstance().get(id));
	}

	@FunctionName("auction-update")
	public HttpResponseMessage update(
			@HttpTrigger(name = "req", methods = { HttpMethod.PUT }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction/{id}") HttpRequestMessage<Optional<String>> req,
			@BindingName("id") String id, final ExecutionContext ctx) {
		return Http.json(req, () -> AuctionOps.getInstance().update(Http.sessionId(req), id, Http.body(req, Auction.class)));
	}

	@FunctionName("auction-list")
	public HttpResponseMessage list(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction") HttpRequestMessage<Optional<String>> req,
			final ExecutionContext ctx) {
		return Http.json(req, () -> AuctionOps.getInstance().list(Http.offset(req), Http.limit(req)));
	}

	@FunctionName("auction-recent")
	public HttpResponseMessage recent(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction/any/recent") HttpRequestMessage<Optional<String>> req,
			final ExecutionContext ctx) {
		return Http.json(req, () -> AuctionOps.getInstance().recent(Http.offset(req), Http.limit(req)));
	}

	@FunctionName("auction-about-to-close")
	public HttpResponseMessage aboutToClose(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction/any/about-to-close") HttpRequestMessage<Optional<String>> req,
			final ExecutionContext ctx) {
		return Http.json(req, () -> AuctionOps.getInstance().aboutToClose(Http.offset(req), Http.limit(req)));
	}

	@FunctionName("auction-popular")
	public HttpResponseMessage popular(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction/any/popular") HttpRequestMessage<Optional<String>> req,
			final ExecutionContext ctx) {
		return Http.json(req, () -> AuctionOps.getInstance().popular(Http.offset(req), Http.limit(req)));
	}

	@FunctionName("auction-search")
	public HttpResponseMessage search(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction/search") HttpRequestMessage<Optional<String>> req,
			final ExecutionContext ctx) {
		return Http.json(req, () -> AuctionOps.getInstance().search(Http.param(req, "q", null),
				Http.offset(req), Http.limit(req)));
	}
}
