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

/** HTTP functions for bids: same routes as BidsResource in cc-proj. */
public class BidFunctions {

	@FunctionName("bid-create")
	public HttpResponseMessage create(
			@HttpTrigger(name = "req", methods = { HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction/{auctionId}/bid") HttpRequestMessage<Optional<String>> req,
			@BindingName("auctionId") String auctionId, final ExecutionContext ctx) {
		return Http.json(req, () -> BidOps.getInstance().create(Http.sessionId(req), auctionId, Http.body(req, Bid.class)));
	}

	@FunctionName("bid-list")
	public HttpResponseMessage list(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction/{auctionId}/bid") HttpRequestMessage<Optional<String>> req,
			@BindingName("auctionId") String auctionId, final ExecutionContext ctx) {
		return Http.json(req, () -> BidOps.getInstance().list(auctionId, Http.offset(req), Http.limit(req)));
	}

	@FunctionName("bid-get")
	public HttpResponseMessage get(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS, route = "auction/{auctionId}/bid/{bidId}") HttpRequestMessage<Optional<String>> req,
			@BindingName("auctionId") String auctionId, @BindingName("bidId") String bidId,
			final ExecutionContext ctx) {
		return Http.json(req, () -> BidOps.getInstance().get(auctionId, bidId));
	}
}
