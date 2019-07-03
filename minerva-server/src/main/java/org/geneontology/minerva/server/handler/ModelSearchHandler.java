/**
 * 
 */
package org.geneontology.minerva.server.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
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
 * Uses Jersey + JSONP
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
		private void makeModelsDistinct() {

		}
	}

	public class ModelMeta{
		private String id;
		private String date;
		private String title;
		private String state;
		private Set<String> contributors;
		private Set<String> groups;
		private HashMap<String, Set<String>> query_match;

		public ModelMeta(String id, String date, String title, String state, Set<String> contributors, Set<String> groups) {
			this.id = id;
			this.date = date;
			this.title = title;
			this.state = state;
			this.contributors = contributors;
			this.groups = groups;
			query_match = new HashMap<String, Set<String>>();
		}
	}


	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public ModelSearchResult searchGet(
			@QueryParam("gene_product_class_uri") Set<String> gene_product_class_uris, 
			@QueryParam("goterm") Set<String> goterms,
			@QueryParam("pmid") Set<String> pmids,
			@QueryParam("title") String title,
			@QueryParam("state") Set<String> state,
			@QueryParam("contributor") Set<String> contributor,
			@QueryParam("group") Set<String> group,
			@QueryParam("date") String date,
			@QueryParam("offset") int offset,
			@QueryParam("limit") int limit
			) throws MalformedQueryException, QueryEvaluationException, RepositoryException, IOException  {
		if(gene_product_class_uris!=null
				||goterms!=null
				||pmids!=null
				||title!=null
				||state!=null
				||contributor!=null
				||group!=null
				||date!=null) {
			return search(gene_product_class_uris, goterms, pmids, title, state, contributor, group, date, offset, limit);
		}else {
			return getAll(offset, limit);
		}
	}

	//examples 
	//http://127.0.0.1:6800/search/?
	//?gene_product_class_uri=http://identifiers.org/mgi/MGI:1328355
	//&gene_product_class_uri=http://identifiers.org/mgi/MGI:87986
	//&goterm=http://purl.obolibrary.org/obo/GO_0030968
	//&title=mouse
	//&pmid=PMID:19911006
	//&state=development&state=review {development, production, closed, review, delete} or operator
	public ModelSearchResult search(
			Set<String> gene_product_class_uris, Set<String> goterms, Set<String>pmids, 
			String title_search,Set<String> state_search, Set<String> contributor_search, Set<String> group_search, String date_search,
			int offset, int limit) 
					throws MalformedQueryException, QueryEvaluationException, RepositoryException, IOException  {
		Set<String> type_uris = new HashSet<String>();
		if(gene_product_class_uris!=null) {
			type_uris.addAll(gene_product_class_uris);
		}
		if(goterms!=null) {
			type_uris.addAll(goterms);
		}
		ModelSearchResult r = new ModelSearchResult();
		Map<String, ModelMeta> id_model = new HashMap<String, ModelMeta>();
		String sparql = IOUtils.toString(ModelSearchHandler.class.getResourceAsStream("/ModelSearchQueryTemplate.rq"), StandardCharsets.UTF_8);
		Map<String, String> ind_return = new HashMap<String, String>();
		String ind_return_list = ""; //<ind_return_list>
		String types = ""; //<types>
		int n = 0;
		for(String type_uri : type_uris) {
			n++;
			ind_return.put("?ind"+n, type_uri);
			ind_return_list = ind_return_list+" ?ind"+n;
			types = types+"?ind"+n+" rdf:type <"+type_uri+"> . \n";
		}
		String pmid_constraints = ""; //<pmid_constraints>
		if(pmids!=null) {
			for(String pmid : pmids) {
				n++;
				ind_return.put("?ind"+n, pmid);
				ind_return_list = ind_return_list+" ?ind"+n;
				pmid_constraints = pmid_constraints+"?ind"+n+" <http://purl.org/dc/elements/1.1/source> ?pmid FILTER (?pmid=\""+pmid+"\"^^xsd:string) .\n";  		
			}
		}
		String title_search_constraint = "";
		if(title_search!=null) {
			title_search_constraint = "?title <http://www.bigdata.com/rdf/search#search> \""+title_search+"\" .\n";
		}
		String state_search_constraint = "";
		if(state_search!=null&&state_search.size()>0) {
			String allowed_states = "";
			int c = 0;
			for(String s : state_search) {
				c++;
				allowed_states+="\""+s+"\"";
				if(c<state_search.size()) {
					allowed_states+=",";
				}
			}
			// FILTER (?state IN ("production", , "development", "review", "closed", "delete" ))
			state_search_constraint = "FILTER (?state IN ("+allowed_states+")) . \n";
		}
		String contributor_search_constraint = "";
		if(contributor_search!=null&&contributor_search.size()>0) {
			String allowed_contributors = "";
			int c = 0;
			for(String contributor : contributor_search) {
				c++;
				allowed_contributors+="\""+contributor+"\"";
				if(c<contributor_search.size()) {
					allowed_contributors+=",";
				}
			}
			contributor_search_constraint = "FILTER (?contributor IN ("+allowed_contributors+")) . \n";
		}
		String group_search_constraint = "";
		if(group_search!=null&&group_search.size()>0) {
			String allowed_group = "";
			int c = 0;
			for(String group : group_search) {
				c++;
				allowed_group+="\""+group+"\"";
				if(c<group_search.size()) {
					allowed_group+=",";
				}
			}
			contributor_search_constraint = "FILTER (?group IN ("+allowed_group+")) . \n";
		}
		String date_constraint = "";
		if(date_search!=null&&date_search.length()==10) {
			//e.g. 2019-06-26
			date_constraint = "FILTER (?date > '"+date_search+"') \n";
		}
		String offset_constraint = "";
		if(offset!=0) {
			offset_constraint = "OFFSET "+offset+"\n";
		}		
		String limit_constraint = "";
		if(limit!=0) {
			limit_constraint = "LIMIT "+limit+"\n";
		}
		if(offset==0&&limit==0) {
			limit_constraint = "LIMIT 1000\n";
		}
		
		sparql = sparql.replaceAll("<ind_return_list>", ind_return_list);
		sparql = sparql.replaceAll("<types>", types);
		sparql = sparql.replaceAll("<pmid_constraints>", pmid_constraints);
		sparql = sparql.replaceAll("<title_constraint>", title_search_constraint);
		sparql = sparql.replaceAll("<state_constraint>", state_search_constraint);
		sparql = sparql.replaceAll("<contributor_constraint>", contributor_search_constraint);
		sparql = sparql.replaceAll("<group_constraint>", group_search_constraint);
		sparql = sparql.replaceAll("<date_constraint>", date_constraint);
		sparql = sparql.replaceAll("<limit_constraint>", limit_constraint);
		sparql = sparql.replaceAll("<offset_constraint>", offset_constraint);
		
		TupleQueryResult result = (TupleQueryResult) m3.executeSPARQLQuery(sparql, 10);
		while(result.hasNext()) {
			BindingSet bs = result.next();
			//model meta
			String id = bs.getBinding("id").getValue().stringValue();
			String date = bs.getBinding("date").getValue().stringValue();
			String title = bs.getBinding("title").getValue().stringValue();
			String state = bs.getBinding("state").getValue().stringValue();
			String contribs = bs.getBinding("contributors").getValue().stringValue();
			String groups_ = bs.getBinding("groups").getValue().stringValue();
			Set<String> contributors = new HashSet<String>();
			if(contributors!=null) {
				for(String c : contribs.split(";")) {
					contributors.add(c);
				}
			}
			Set<String> groups = new HashSet<String>();
			if(groups_!=null) {
				for(String c : groups_.split(";")) {
					groups.add(c);
				}
			}
			ModelMeta mm = id_model.get(id);
			if(mm==null) {
				mm = new ModelMeta(id, date, title, state, contributors, groups);
			}
			//matching     		
			for(String ind : ind_return.keySet()) {
				String ind_class_match = bs.getBinding(ind.replace("?", "")).getValue().stringValue();
				Set<String> matching_inds = mm.query_match.get(ind_return.get(ind));
				if(matching_inds==null) {
					matching_inds = new HashSet<String>();
				}
				matching_inds.add(ind_class_match);
				mm.query_match.put(ind_return.get(ind), matching_inds);
			}
			id_model.put(id, mm);
		}
		r.n = id_model.size();
		r.models = new HashSet<ModelMeta>(id_model.values());
		result.close();
		//test
		//http://127.0.0.1:6800/modelsearch/?query=bla
		return r;
	}

	public ModelSearchResult getAll(int offset, int limit) throws MalformedQueryException, QueryEvaluationException, RepositoryException, IOException  {
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
			String groups_ = bs.getBinding("groups").getValue().stringValue();
			Set<String> contributors = new HashSet<String>();
			if(contributors!=null) {
				for(String c : contribs.split(";")) {
					contributors.add(c);
				}
			}
			Set<String> groups = new HashSet<String>();
			if(groups_!=null) {
				for(String c : groups_.split(";")) {
					groups.add(c);
				}
			}
			ModelMeta mm = new ModelMeta(id, date, title, state, contributors, groups);
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
