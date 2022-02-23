package org.geneontology.minerva.server;

import org.geneontology.minerva.server.handler.JsonOrJsonpBatchHandler;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;

/**
 * Replace the accepted request type, if there is a 'json.wrf' query parameter.<br>
 * <br>
 * This filter will be called before, the Jersey framework does the request
 * matching. This allows us to modify the request.
 */
@PreMatching
public class RequireJsonpFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        UriInfo uriInfo = requestContext.getUriInfo();
        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
        for (String param : queryParameters.keySet()) {
            if (JsonOrJsonpBatchHandler.JSONP_DEFAULT_OVERWRITE.equals(param)) {
                MultivaluedMap<String, String> headers = requestContext.getHeaders();
                headers.putSingle("Accept", "application/javascript");
            }
        }
    }

}
