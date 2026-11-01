package cc.utils;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * 
 * Utility class for catching server exceptions and showing the stacktrace.
 * 
 * Requires the following dependency:
 * 
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-lang3</artifactId>
    <version>3.12.0</version>
</dependency>
 * 
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

    
    @Override
    public Response toResponse(Throwable ex) {

        if (ex instanceof WebApplicationException) {
            Response r = ((WebApplicationException) ex).getResponse();
            
            if( r.getStatus() != Status.OK.getStatusCode() && r.getStatus() != Status.NO_CONTENT.getStatusCode()) {
                ex.printStackTrace();
                return Response.fromResponse(r).entity(ExceptionUtils.getStackTrace(ex)).type(MediaType.TEXT_PLAIN).build();
            }

            return r;
        }

        ex.printStackTrace();

        return Response.status(Status.INTERNAL_SERVER_ERROR).entity(ExceptionUtils.getStackTrace(ex)).type(MediaType.TEXT_PLAIN).build();
    }
}