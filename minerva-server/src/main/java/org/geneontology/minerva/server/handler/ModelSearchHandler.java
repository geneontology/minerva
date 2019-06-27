/**
 * 
 */
package org.geneontology.minerva.server.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
@Path("/search")
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
		private Set<String> contributors;
		private HashMap<String, String> query_match;
		
		public ModelMeta(String id, String date, String title, String state, Set<String> contributors) {
			this.id = id;
			this.date = date;
			this.title = title;
			this.state = state;
			this.contributors = contributors;
			query_match = new HashMap<String, String>();
		}
	}
	
	
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ModelSearchResult searchGet(@QueryParam("gene_product_class_uri") Set<String> gene_product_class_uris) throws MalformedQueryException, QueryEvaluationException, RepositoryException, IOException  {
    	if(gene_product_class_uris!=null) {
    		return searchByGenes(gene_product_class_uris);
    	}else {
    		return getAll();
    	}
    }
    	 
  //examples ?gene_product_class_uri=http://identifiers.org/mgi/MGI:1328355&gene_product_class_uri=http://identifiers.org/mgi/MGI:87986  
    public ModelSearchResult searchByGenes(Set<String> gene_product_class_uris) throws MalformedQueryException, QueryEvaluationException, RepositoryException, IOException  {
    	ModelSearchResult r = new ModelSearchResult();
    	Set<ModelMeta> models = new HashSet<ModelMeta>();
    	String sparql = IOUtils.toString(ModelSearchHandler.class.getResourceAsStream("/QueryByGeneUriAND.rq"), StandardCharsets.UTF_8);
    	Map<String, String> gp_return = new HashMap<String, String>();
    	String gp_return_list = ""; //<gp_return_list>
    	String gp_and_constraints = ""; //<gp_and_constraints>
    	int gp_n = 0;
    	for(String gp_uri : gene_product_class_uris) {
    		gp_n++;
    		gp_return.put("?gp"+gp_n, gp_uri);
    		gp_return_list = gp_return_list+" ?gp"+gp_n;
    		gp_and_constraints = gp_and_constraints+"?gp"+gp_n+" rdf:type <"+gp_uri+"> . \n";
    	}
    	sparql = sparql.replaceAll("<gp_return_list>", gp_return_list);
    	sparql = sparql.replaceAll("<gp_and_constraints>", gp_and_constraints);
    	TupleQueryResult result = (TupleQueryResult) m3.executeSPARQLQuery(sparql, 10);
    	int n_models = 0;
    	while(result.hasNext()) {
    		BindingSet bs = result.next();
    		//model meta
    		String id = bs.getBinding("id").getValue().stringValue();
    		String date = bs.getBinding("date").getValue().stringValue();
    		String title = bs.getBinding("title").getValue().stringValue();
    		String state = bs.getBinding("state").getValue().stringValue();
    		String contribs = bs.getBinding("contributors").getValue().stringValue();
    		Set<String> contributors = new HashSet<String>();
    		if(contributors!=null) {
    			for(String c : contribs.split(";")) {
    				contributors.add(c);
    			}
    		}
    		ModelMeta mm = new ModelMeta(id, date, title, state, contributors);
    		//matching 
    		for(String gp : gp_return.keySet()) {
    			String gp_ind = bs.getBinding(gp.replace("?", "")).getValue().stringValue();
    			mm.query_match.put(gp_return.get(gp), gp_ind);
    		}
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

    public ModelSearchResult getAll() throws MalformedQueryException, QueryEvaluationException, RepositoryException, IOException  {
    	ModelSearchResult r = new ModelSearchResult();
    	Set<ModelMeta> models = new HashSet<ModelMeta>();
    	String sparql = IOUtils.toString(ModelSearchHandler.class.getResourceAsStream("/GetAllModels.rq"), StandardCharsets.UTF_8);
    	TupleQueryResult result = (TupleQueryResult) m3.executeSPARQLQuery(sparql, 100);
    	int n_models = 0;
    	while(result.hasNext()) {
    		BindingSet bs = result.next();
    		//model meta
    		String id = bs.getBinding("id").getValue().stringValue();
    		String date = bs.getBinding("date").getValue().stringValue();
    		String title = bs.getBinding("title").getValue().stringValue();
    		String state = bs.getBinding("state").getValue().stringValue();
    		String contribs = bs.getBinding("contributors").getValue().stringValue();
    		Set<String> contributors = new HashSet<String>();
    		if(contributors!=null) {
    			for(String c : contribs.split(";")) {
    				contributors.add(c);
    			}
    		}
    		ModelMeta mm = new ModelMeta(id, date, title, state, contributors);
    		models.add(mm);
    		n_models++;
    	}
    	System.out.println("n models "+n_models);
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
