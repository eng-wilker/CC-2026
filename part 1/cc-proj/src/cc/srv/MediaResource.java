package cc.srv;

import cc.data.MediaDAO;
import cc.ops.MediaOps;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoints for media. The logic is in cc.ops.MediaOps.
 */
@Path("/media")
public class MediaResource {

	@POST
	@Path("/")
	@Consumes("image/*")
	@Produces(MediaType.TEXT_PLAIN)
	public String upload(@HeaderParam("Content-Type") String contentType, byte[] contents) {
		return MediaOps.getInstance().upload(contentType, contents).resultOrThrow();
	}

	@GET
	@Path("/{id}")
	public Response download(@PathParam("id") String id) {
		MediaDAO m = MediaOps.getInstance().download(id).resultOrThrow();
		return Response.ok(m.decoded(), m.getContentType()).build();
	}
}
