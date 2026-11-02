package cc.srv;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Auxiliary endpoint.
 */
@Path("/ctrl")
public class ControlResource {

	@Path("/version")
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public String version() {
		return "cc2627-proj v1";
	}
}
