/**
 * 
 */
package org.geneontology.minerva.server.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
import org.geneontology.minerva.BlazegraphOntologyManager;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.openrdf.query.Binding;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryResult;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryException;
import org.semanticweb.owlapi.model.IRI;

/**
 * Respond to queries for models in the running blazegraph instance backing minerva
 * Uses Jersey + JSONP
 *
 */
@Path("/search")
public class ModelSearchHandler {
 
	private final BlazegraphMolecularModelManager<?> m3;
	private final int timeout;
	private final BlazegraphOntologyManager go_lego;
	private Map<String, Set<String>> taxon_models;
	/**
	 * 
	 */
	public ModelSearchHandler(BlazegraphMolecularModelManager<?> m3, int timeout) {
		this.m3 = m3;
		this.timeout = timeout;
		this.go_lego  = m3.getGolego_repo();
		this.taxon_models = m3.getTaxon_models();
	}

	public class ModelSearchResult {
		private Integer n;
		private LinkedHashSet<ModelMeta> models;
		private String message;
		private String error;
		private String sparql;
		public Integer getN() {
			return n;
		}
		public void setN(Integer n) {
			this.n = n;
		}
		public LinkedHashSet<ModelMeta> getModels() {
			return models;
		}
		public void setModels(LinkedHashSet<ModelMeta> models) {
			this.models = models;
		}
		public String getMessage() {
			return message;
		}
		public void setMessage(String message) {
			this.message = message;
		}
		public String getError() {
			return error;
		}
		public void setError(String error) {
			this.error = error;
		}
		public String getSparql() {
			return sparql;
		}
		public void setSparql(String sparql) {
			this.sparql = sparql;
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

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getDate() {
			return date;
		}

		public void setDate(String date) {
			this.date = date;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getState() {
			return state;
		}

		public void setState(String state) {
			this.state = state;
		}

		public Set<String> getContributors() {
			return contributors;
		}

		public void setContributors(Set<String> contributors) {
			this.contributors = contributors;
		}

		public Set<String> getGroups() {
			return groups;
		}

		public void setGroups(Set<String> groups) {
			this.groups = groups;
		}

		public HashMap<String, Set<String>> getQuery_match() {
			return query_match;
		}

		public void setQuery_match(HashMap<String, Set<String>> query_match) {
			this.query_match = query_match;
		}


	}


	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public ModelSearchResult searchGet(
			@QueryParam("taxon") Set<String> taxa, 
			@QueryParam("gp") Set<String> gene_product_class_uris, 
			@QueryParam("term") Set<String> terms,
			@QueryParam("pmid") Set<String> pmids,
			@QueryParam("title") String title,
			@QueryParam("state") Set<String> state,
			@QueryParam("contributor") Set<String> contributor,
			@QueryParam("group") Set<String> group,
			@QueryParam("exactdate") String exactdate,
			@QueryParam("date") String date,
			@QueryParam("dateend") String datend,
			@QueryParam("offset") int offset,
			@QueryParam("limit") int limit,
			@QueryParam("count") String count
			){
		ModelSearchResult result = new ModelSearchResult();
		result = search(taxa, gene_product_class_uris, terms, pmids, title, state, contributor, group, exactdate, date, datend, offset, limit, count);
		return result;
	}

	//examples 
	//http://127.0.0.1:6800/search/?
	//?gp=http://identifiers.org/uniprot/P15822-3
	//?term=http://purl.obolibrary.org/obo/GO_0003677
	//
	//
	//?gp=http://identifiers.org/mgi/MGI:1328355
	//&gp=http://identifiers.org/mgi/MGI:87986
	//&term=http://purl.obolibrary.org/obo/GO_0030968
	//&title=mouse
	//&pmid=PMID:19911006
	//&state=development&state=review {development, production, closed, review, delete} or operator
	//&count
	//127.0.0.1:6800/search/?contributor=http://orcid.org/0000-0002-1706-4196
	public ModelSearchResult search(Set<String> taxa, 
			Set<String> gene_product_ids, Set<String> terms, Set<String>pmids, 
			String title_search,Set<String> state_search, Set<String> contributor_search, Set<String> group_search, 
			String exactdate, String date_search, String datend, 
			int offset, int limit, String count) {
		ModelSearchResult r = new ModelSearchResult();
		Set<String> go_type_ids = new HashSet<String>();
		Set<String> gene_type_ids = new HashSet<String>();
		if(gene_product_ids!=null) {
			gene_type_ids.addAll(gene_product_ids);
		}
		if(terms!=null) {
			go_type_ids.addAll(terms);
		}
		CurieHandler curie_handler = m3.getCuriHandler();
		Set<String> go_type_uris = new HashSet<String>();
		Set<String> gene_type_uris = new HashSet<String>();
		for(String curi : go_type_ids) {
			if(curi.startsWith("http")) {
				go_type_uris.add(curi);
			}else {
				try {
					IRI iri = curie_handler.getIRI(curi);
					if(iri!=null) {
						go_type_uris.add(iri.toString());
					}
				} catch (UnknownIdentifierException e) {
					r.error += e.getMessage()+" \n ";
					e.printStackTrace();
					return r;
				}
			}
		}
		for(String curi : gene_type_ids) {
			if(curi.startsWith("http")) {
				gene_type_uris.add(curi);
			}else {
				try {
					IRI iri = curie_handler.getIRI(curi);
					if(iri!=null) {
						gene_type_uris.add(iri.toString());
					}
				} catch (UnknownIdentifierException e) {
					r.error += e.getMessage()+" \n ";
					e.printStackTrace();
					return r;
				}
			}
		}
		Map<String, ModelMeta> id_model = new LinkedHashMap<String, ModelMeta>();
		String sparql="";
		try {
			sparql = IOUtils.toString(ModelSearchHandler.class.getResourceAsStream("/ModelSearchQueryTemplate.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Map<String, String> ind_return = new HashMap<String, String>();
		String ind_return_list = ""; //<ind_return_list>
		String types = ""; //<types>
		int n = 0;
		for(String type_uri : gene_type_uris) {
			n++;
			ind_return.put("?ind"+n, type_uri);
			ind_return_list = ind_return_list+" ?ind"+n;
			types = types+"?ind"+n+" rdf:type <"+type_uri+"> . \n";
		}
		boolean expand = true;
		if(expand) {
			for(String go_type_uri : go_type_uris) {
				n++;
				ind_return.put("?ind"+n, go_type_uri);
				ind_return_list = ind_return_list+" ?ind"+n;
				String expansion = "VALUES ?term"+n+" { ";
				try {
					Set<String> subclasses = go_lego.getAllSubClasses(go_type_uri);
					for(String sub : subclasses) {
						expansion+="<"+sub+"> \n";
					}
					expansion+= "} . \n";
					types = types+" "+expansion+" ?ind"+n+" rdf:type ?term"+n+" . \n";
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}else {
			for(String go_type_uri : go_type_uris) {
				n++;
				ind_return.put("?ind"+n, go_type_uri);
				ind_return_list = ind_return_list+" ?ind"+n;
				types = types+"?ind"+n+" rdf:type <"+go_type_uri+"> . \n";
			}
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
		String taxa_constraint = "";
		if(taxa!=null&&!taxa.isEmpty()) {
			String model_filter =  " VALUES ?id { \n"; 
			for(String taxon : taxa) {
				if(taxon.startsWith("NCBITaxon:")) {
					taxon = taxon.replace(":", "_");
					taxon = "http://purl.obolibrary.org/obo/"+taxon;
				}
				else if(!taxon.startsWith("http://purl.obolibrary.org/obo/NCBITaxon_")) {
					taxon = "http://purl.obolibrary.org/obo/NCBITaxon_"+taxon;
				} 
				Set<String> models = taxon_models.get(taxon);
				if(models!=null) {
					for(String model : models) {
						model_filter+="<"+model+"> \n";
					}
				}
			}
			model_filter += "} . \n";
			taxa_constraint = model_filter;
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
			contributor_search_constraint = 
					" ?id <http://purl.org/dc/elements/1.1/contributor> ?test_contributor . \n"  
							+ " FILTER (?test_contributor IN ("+allowed_contributors+")) . \n";
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
			contributor_search_constraint = " ?id <http://purl.org/pav/providedBy> ?test_group . \n"
					+ "FILTER (?test_group IN ("+allowed_group+")) . \n";
		}
		String date_constraint = "";
		if(exactdate!=null&&exactdate.length()==10) {
			date_constraint = "FILTER (?date = '"+exactdate+"') \n";
		}else if(date_search!=null&&date_search.length()==10) {
			//e.g. 2019-06-26
			date_constraint = "FILTER (?date > '"+date_search+"') \n";
			if(datend!=null&&datend.length()==10) {
				date_constraint = "FILTER (?date > '"+date_search+"' && ?date < '"+datend+"') \n";
			}
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
		//default group by
		String group_by_constraint = "GROUP BY ?id ?date ?title ?state <ind_return_list> ";
		//default return block
		//TODO investigate need to add DISTINCT to GROUP_CONCAT here
		String return_block = "?id ?date ?title ?state <ind_return_list> (GROUP_CONCAT(DISTINCT ?contributor;separator=\";\") AS ?contributors) (GROUP_CONCAT(DISTINCT ?group;separator=\";\") AS ?groups)";
		if(count!=null) {
			return_block = "(count(distinct ?id) as ?count)";
			limit_constraint = "";
			offset_constraint = "";
			group_by_constraint = "";
		}
		sparql = sparql.replaceAll("<return_block>", return_block);
		sparql = sparql.replaceAll("<group_by_constraint>", group_by_constraint);
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
		sparql = sparql.replaceAll("<taxa_constraint>", taxa_constraint);
		r.sparql = sparql;

		TupleQueryResult result;
		try {
			result = (TupleQueryResult) m3.executeSPARQLQuery(sparql, 10);
		} catch (MalformedQueryException | QueryEvaluationException | RepositoryException e) {
			if(e instanceof MalformedQueryException) {
				r.message = "Malformed Query";
			}else if(e instanceof QueryEvaluationException) {
				r.message = "Query Evaluation Problem - probably a time out";
			}else if(e instanceof RepositoryException) {
				r.message = "Repository Exception";
			}
			r.error = e.getMessage();
			e.printStackTrace();
			return r;
		}
		String n_count = null;
		try {
			while(result.hasNext()) {
				BindingSet bs = result.next();
				if(count!=null) {
					n_count = bs.getBinding("count").getValue().stringValue();
				}else {
					//model meta
					String id = bs.getBinding("id").getValue().stringValue();
					try {
						String curie = curie_handler.getCuri(IRI.create(id));
						if(curie!=null) {
							id = curie;
						}
					} catch (Exception e) {
						r.error += e.getMessage()+" \n ";
						e.printStackTrace();
						return r;
					}
					String date = bs.getBinding("date").getValue().stringValue();
					String title = bs.getBinding("title").getValue().stringValue();
					String contribs = bs.getBinding("contributors").getValue().stringValue();
					//optional values (some are empty)
					Binding state_binding = bs.getBinding("state");
					String state = "";
					if(state_binding!=null) {
						state = state_binding.getValue().stringValue();
					}
					Binding group_binding = bs.getBinding("groups");
					String groups_ = "";
					if(group_binding!=null) {
						groups_ = group_binding.getValue().stringValue();
					}							
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
			}
		} catch (QueryEvaluationException e) {
			r.message = "Query Evaluation Problem - probably a time out";
			r.error = e.getMessage();
			e.printStackTrace();
			return r;
		}		
		if(n_count!=null) {
			r.n = Integer.parseInt(n_count);
		}else {
			r.n = id_model.size();
			r.models = new LinkedHashSet<ModelMeta>(id_model.values());
		}		
		try {
			result.close();
		} catch (QueryEvaluationException e) {
			r.message = "Query Evaluation Problem - can't close result set";
			r.error = e.getMessage();
			e.printStackTrace();
			return r;
		}
		//test
		//http://127.0.0.1:6800/modelsearch/?query=bla
		return r;
	}


	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public ModelSearchResult searchPostForm(
			@FormParam("taxon") Set<String> taxa, 
			@FormParam("gp") Set<String> gene_product_class_uris, 
			@FormParam("term") Set<String> terms,
			@FormParam("pmid") Set<String> pmids,
			@FormParam("title") String title,
			@FormParam("state") Set<String> state,
			@FormParam("contributor") Set<String> contributor,
			@FormParam("group") Set<String> group,
			@FormParam("exactdate") String exactdate,
			@FormParam("date") String date,
			@FormParam("dateend") String datend, 
			@FormParam("offset") int offset,
			@FormParam("limit") int limit,
			@FormParam("count") String count) {
		ModelSearchResult result = new ModelSearchResult();
		result = search(taxa, gene_product_class_uris, terms, pmids, title, state, contributor, group, exactdate, date, datend, offset, limit, count);
		return result;
	}


}
