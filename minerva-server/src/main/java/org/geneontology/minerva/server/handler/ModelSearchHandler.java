/**
 * 
 */
package org.geneontology.minerva.server.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryResult;
import org.openrdf.query.TupleQueryResult;
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
	
	public class ModelSearchResult {
	    private Integer n;
	    private Set<ModelMeta> models;
	}
	
	public class ModelMeta{
		private String id;
		private String date;
		private String title;
		private String state;
		private String contributors;
		
		public ModelMeta(String id, String date, String title, String state, String contributors) {
			this.id = id;
			this.date = date;
			this.title = title;
			this.state = state;
			this.contributors = contributors;
		}
	}
	
	
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ModelSearchResult searchGet(@QueryParam("query") String queryText) throws MalformedQueryException, QueryEvaluationException, RepositoryException, IOException  {
    	ModelSearchResult r = new ModelSearchResult();
    	Set<ModelMeta> models = new HashSet<ModelMeta>();
    	String sparql = IOUtils.toString(ModelSearchHandler.class.getResourceAsStream("/GetAllModels.rq"), StandardCharsets.UTF_8);
		//?id ?date ?title ?state (GROUP_CONCAT(?contributor;separator=";") AS ?contributors
    	TupleQueryResult result = (TupleQueryResult) m3.executeSPARQLQuery(sparql, 10);
    	int n_models = 0;
    	while(result.hasNext()) {
    		BindingSet bs = result.next();
    		String id = bs.getBinding("id").getValue().stringValue();
    		String date = bs.getBinding("date").getValue().stringValue();
    		String title = bs.getBinding("title").getValue().stringValue();
    		String state = bs.getBinding("state").getValue().stringValue();
    		String contributors = bs.getBinding("date").getValue().stringValue();
    		ModelMeta mm = new ModelMeta(id, date, title, state, contributors);
    		models.add(mm);
    		n_models++;
    	}
    	r.n = n_models;
    	r.models = models;
    	result.close();
    //test
    //http://127.0.0.1:6800/modelsearch/?query=bla
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
