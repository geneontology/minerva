/**
 * 
 */
package org.geneontology.minerva.server.handler;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryResult;
import org.openrdf.repository.RepositoryException;

/**
 * Respond to queries for models in the running blazegraph instance backing minerva
 *
 */
@Path("/modelsearch")
public class ModelSearchHandler {

    private final BlazegraphMolecularModelManager m3;
    private final int timeout;
	/**
	 * 
	 */
	public ModelSearchHandler(BlazegraphMolecularModelManager m3, int timeout) {
        this.m3 = m3;
        this.timeout = timeout;
	}
	
	public class Result {
	    private Integer id;
	    private String name;
	}
	
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Result searchGet(@QueryParam("query") String queryText)  {
       // return m3.executeModelSearch(queryText, timeout);
    //test
    //http://127.0.0.1:6800/modelsearch/?query=bla
    	Result r = new Result();
    	r.id = 9; r.name = "fred";
    	return r;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String searchPostForm(@FormParam("query") String queryText) {
       // return m3.executeSPARQLQuery(queryText, timeout);
    	return "post pong";
    }

}
