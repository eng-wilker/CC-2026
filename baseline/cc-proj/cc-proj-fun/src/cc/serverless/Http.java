package cc.serverless;

import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import cc.utils.Result;

/**
 * Helpers shared by the HTTP functions: reading the request (JSON body, session cookie,
 * pagination parameters) and turning a {@link Result} into a response. This is the part that
 * jakarta.ws.rs did for us in cc-proj.
 */
final class Http {

	static final String COOKIE_NAME = "cc:session";

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private Http() {
	}

	/** Thrown for a malformed request; becomes a 400. */
	static class BadRequest extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	/** Case-insensitive header lookup, in case the runtime does not lower-case the names. */
	static String header(HttpRequestMessage<?> req, String name) {
		for (java.util.Map.Entry<String, String> e : req.getHeaders().entrySet())
			if (e.getKey().equalsIgnoreCase(name))
				return e.getValue();
		return null;
	}

	/** The session id from the Cookie header, or null. Header names are lower-cased by the runtime. */
	static String sessionId(HttpRequestMessage<?> req) {
		String header = header(req, "cookie");
		if (header == null)
			return null;
		for (String part : header.split(";")) {
			String p = part.trim();
			if (p.startsWith(COOKIE_NAME + "=")) {
				String v = p.substring(COOKIE_NAME.length() + 1);
				// java.net.CookieManager sends Max-Age cookies back quoted (RFC 2965 style)
				if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\""))
					v = v.substring(1, v.length() - 1);
				return v;
			}
		}
		return null;
	}

	/** Parses the JSON body into the given type. */
	static <T> T body(HttpRequestMessage<Optional<String>> req, Class<T> type) {
		String s = req.getBody().orElse(null);
		if (s == null || s.isBlank())
			throw new BadRequest();
		try {
			return MAPPER.readValue(s, type);
		} catch (Exception e) {
			throw new BadRequest();
		}
	}

	/** An integer query parameter, or the default if it is absent. */
	static int intParam(HttpRequestMessage<?> req, String name, int def) {
		String v = req.getQueryParameters().get(name);
		if (v == null || v.isEmpty())
			return def;
		try {
			return Integer.parseInt(v);
		} catch (NumberFormatException e) {
			throw new BadRequest();
		}
	}

	static int offset(HttpRequestMessage<?> req) {
		return intParam(req, "st", 0);
	}

	static int limit(HttpRequestMessage<?> req) {
		return intParam(req, "len", 20);
	}

	static String param(HttpRequestMessage<?> req, String name, String def) {
		String v = req.getQueryParameters().get(name);
		return v == null ? def : v;
	}

	/** Runs the operation and builds the response; never throws. */
	static <T> HttpResponseMessage json(HttpRequestMessage<?> req, Supplier<Result<T>> op) {
		return respond(req, op, null);
	}

	/** Like json, but also sets a cookie when the result is a session id. */
	static HttpResponseMessage login(HttpRequestMessage<?> req, Supplier<Result<String>> op) {
		return respond(req, op, "session");
	}

	/** For operations whose success has no body (e.g. delete). */
	static <T> HttpResponseMessage empty(HttpRequestMessage<?> req, Supplier<Result<T>> op) {
		return respond(req, op, "empty");
	}

	/** Image bytes with the stored content type. */
	static HttpResponseMessage bytes(HttpRequestMessage<?> req, Supplier<Result<cc.data.MediaDAO>> op) {
		try {
			Result<cc.data.MediaDAO> r = op.get();
			if (!r.isOK())
				return req.createResponseBuilder(status(r.error())).build();
			cc.data.MediaDAO m = r.value();
			return req.createResponseBuilder(HttpStatus.OK)
					.header("Content-Type", m.getContentType()).body(m.decoded()).build();
		} catch (Exception e) {
			return error(req, e);
		}
	}

	private static <T> HttpResponseMessage respond(HttpRequestMessage<?> req, Supplier<Result<T>> op,
			String mode) {
		try {
			Result<T> r = op.get();
			if (!r.isOK())
				return req.createResponseBuilder(status(r.error())).build();
			T v = r.value();
			if ("session".equals(mode))
				return req.createResponseBuilder(HttpStatus.OK)
						.header("Set-Cookie", COOKIE_NAME + "=" + v + "; Path=/; Max-Age=3600; HttpOnly")
						.build();
			if ("empty".equals(mode) || v == null)
				return req.createResponseBuilder(v == null && !"empty".equals(mode)
						? HttpStatus.NO_CONTENT : HttpStatus.OK).build();
			if (v instanceof String) // e.g. the id returned by media upload: plain text, as in cc-proj
				return req.createResponseBuilder(HttpStatus.OK)
						.header("Content-Type", "text/plain").body(v).build();
			return req.createResponseBuilder(HttpStatus.OK)
					.header("Content-Type", "application/json")
					.body(MAPPER.writeValueAsString(v)).build();
		} catch (Exception e) {
			return error(req, e);
		}
	}

	/** Used by the functions to turn exceptions (bad request, anything unexpected) into responses. */
	static HttpResponseMessage error(HttpRequestMessage<?> req, Exception e) {
		if (e instanceof BadRequest)
			return req.createResponseBuilder(HttpStatus.BAD_REQUEST).build();
		e.printStackTrace();
		return req.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
				.header("Content-Type", "text/plain").body(e.toString()).build();
	}

	private static HttpStatus status(int code) {
		try {
			return HttpStatus.valueOf(code);
		} catch (Exception e) {
			return HttpStatus.INTERNAL_SERVER_ERROR;
		}
	}

}
