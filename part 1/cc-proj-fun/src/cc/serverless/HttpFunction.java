package cc.serverless;

import java.util.Optional;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

/**
 * Functions with an HTTP trigger, reachable at {Server_URL}/rest/{route}. host.json sets the
 * prefix to "rest" instead of the default "api", so both deployments have the same paths and
 * the artillery scripts run against either. The full URL is printed when you deploy.
 *
 * There is one function here, matching /rest/ctrl/version in the main project, so that you
 * have the same endpoint on both deployments to compare. The rest of the project is for you
 * to add: put an HTTP trigger in front of each operation in cc.ops.
 *
 * cc.data, cc.db, cc.ops and cc.utils are copies of the ones in cc-proj, all but
 * GenericExceptionMapper, which is JAX-RS. If you change them there, copy them again.
 *
 * The operations need nothing from JAX-RS, which is the point of keeping them in cc.ops -
 * a session is a String and errors come back in a Result, so a function reads the status
 * code and builds its own response:
 *
 *   Result<User> r = UserOps.getInstance().get(id);
 *   return r.isOK()
 *       ? request.createResponseBuilder(HttpStatus.OK).body(r.value()).build()
 *       : request.createResponseBuilder(HttpStatus.valueOf(r.error())).build();
 */
public class HttpFunction {

	@FunctionName("ctrl-version")
	public HttpResponseMessage version(@HttpTrigger(name = "req",
										methods = { HttpMethod.GET },
										authLevel = AuthorizationLevel.ANONYMOUS,
										route = "ctrl/version")
			HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {
		context.getLogger().info("ctrl/version called");
		return request.createResponseBuilder(HttpStatus.OK).body("cc2627-proj-fun v1").build();
	}
}
