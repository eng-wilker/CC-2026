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

/** HTTP functions for media: same routes as MediaResource in cc-proj. */
public class MediaFunctions {

	/**
	 * The Java worker only delivers a request body as raw bytes when the Content-Type is
	 * application/octet-stream; for image/* the host decodes it as text, which corrupts it. So on
	 * this deployment clients upload images as octet-stream, and the real type is detected from
	 * the first bytes of the image.
	 */
	@FunctionName("media-upload")
	public HttpResponseMessage upload(
			@HttpTrigger(name = "req", methods = { HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS, route = "media") HttpRequestMessage<Optional<byte[]>> req,
			final ExecutionContext ctx) {
		byte[] body = req.getBody().orElse(null);
		String contentType = detectType(body, Http.header(req, "content-type"));
		return Http.json(req, () -> MediaOps.getInstance().upload(contentType, body));
	}

	static String detectType(byte[] b, String declared) {
		if (b != null && b.length >= 12) {
			if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF)
				return "image/jpeg";
			if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G')
				return "image/png";
			if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8')
				return "image/gif";
			if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E'
					&& b[10] == 'B' && b[11] == 'P')
				return "image/webp";
		}
		return declared == null ? "application/octet-stream" : declared;
	}

	@FunctionName("media-download")
	public HttpResponseMessage download(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS, route = "media/{id}") HttpRequestMessage<Optional<String>> req,
			@BindingName("id") String id, final ExecutionContext ctx) {
		return Http.bytes(req, () -> MediaOps.getInstance().download(id));
	}
}
