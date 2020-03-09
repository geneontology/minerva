/**
 * 
 */
package org.geneontology.minerva;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import com.bigdata.journal.Options;
import com.bigdata.rdf.sail.BigdataSail;
import com.bigdata.rdf.sail.BigdataSailRepository;
import com.bigdata.rdf.sail.BigdataSailRepositoryConnection;

/**
 * @author benjamingood
 *
 */
public class BlazegraphOntologyManager {
	private static Logger LOG = Logger.getLogger(BlazegraphOntologyManager.class);
	private final BigdataSailRepository go_lego_repo;

	public BigdataSailRepository getGo_lego_repo() {
		return go_lego_repo;
	}
	public BlazegraphOntologyManager(String go_lego_repo_file) {
		go_lego_repo = initializeRepository(go_lego_repo_file);
	}

	private BigdataSailRepository initializeRepository(String pathToJournal) {
		try {
			Properties properties = new Properties();
			properties.load(this.getClass().getResourceAsStream("onto-blazegraph.properties"));
			properties.setProperty(Options.FILE, pathToJournal);
			BigdataSail sail = new BigdataSail(properties);
			BigdataSailRepository repository = new BigdataSailRepository(sail);

			repository.initialize();
			return repository;
		} catch (RepositoryException e) {
			LOG.fatal("Could not create Blazegraph sail", e);
			return null;
		} catch (IOException e) {
			LOG.fatal("Could not create Blazegraph sail", e);
			return null;
		}
	}

	public void loadRepositoryFromOWLFile(File file, String iri) throws OWLOntologyCreationException, RepositoryException, IOException, RDFParseException, RDFHandlerException {
		synchronized(go_lego_repo) {
			final BigdataSailRepositoryConnection connection = go_lego_repo.getUnisolatedConnection();
			try {
				connection.begin();
				try {
					URI graph = new URIImpl(iri);
					connection.clear(graph);
					if(file.getName().endsWith(".ttl")) {
						connection.add(file, "", RDFFormat.TURTLE, graph);
					}else if(file.getName().endsWith(".owl")) {
						connection.add(file, "", RDFFormat.RDFXML, graph);
					}
					connection.commit();
				} catch (Exception e) {
					connection.rollback();
					throw e;
				}
			} finally {
				connection.close();
			}
			return ;
		}		
	}

	public Set<String> getSuperClasses(String uri) throws IOException {
		Set<String> supers = new HashSet<String>();
		try {
			BigdataSailRepositoryConnection connection = go_lego_repo.getReadOnlyConnection();
			try {
				String query = "PREFIX owl: <http://www.w3.org/2002/07/owl#> " +
						"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
						+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
						"SELECT ?super " +
						"WHERE { " +
						"<"+uri+"> rdfs:subClassOf* ?super . " +
						"} ";
				TupleQuery tupleQuery = connection.prepareTupleQuery(QueryLanguage.SPARQL, query);
				TupleQueryResult result = tupleQuery.evaluate();
				while (result.hasNext()) {
					BindingSet binding = result.next();
					Value v = binding.getValue("super");
					//ignore anonymous super classes
					if ( v instanceof URI ) {
						String superclass = binding.getValue("super").stringValue();
						supers.add(superclass);		
					}				
				}
			} catch (MalformedQueryException e) {
				throw new IOException(e);
			} catch (QueryEvaluationException e) {
				throw new IOException(e);
			} finally {
				connection.close();
			}
		} catch (RepositoryException e) {
			throw new IOException(e);
		}
		return supers;
	}
	
	public Map<String, Set<String>> getSuperClassMap(Set<String> uris) throws IOException {
		Map<String, Set<String>> sub_supers = new HashMap<String, Set<String>>();
		try {
			BigdataSailRepositoryConnection connection = go_lego_repo.getReadOnlyConnection();
			try {
				String v = "VALUES ?sub {";
				for(String uri : uris) {
					v+="<"+uri+"> ";
				}
				v+="} . " ;
				
				String query = "PREFIX owl: <http://www.w3.org/2002/07/owl#> " +
						"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
						+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
						"SELECT ?sub ?super " +
						"WHERE { " + v
						+ "?sub rdfs:subClassOf* ?super . " +
						"} ";
				TupleQuery tupleQuery = connection.prepareTupleQuery(QueryLanguage.SPARQL, query);
				TupleQueryResult result = tupleQuery.evaluate();
				while (result.hasNext()) {
					BindingSet binding = result.next();
					Value parent = binding.getValue("super");
					Value child = binding.getValue("sub");
					//System.out.println(child +" "+parent);
					//ignore anonymous super classes
					if ( parent instanceof URI && child instanceof URI) {
						String superclass = binding.getValue("super").stringValue();
						String subclass = binding.getValue("sub").stringValue();
						Set<String> supers = sub_supers.get(subclass);
						if(supers==null) {
							supers = new HashSet<String>();
						}
						supers.add(superclass);		
						sub_supers.put(subclass, supers);
					}				
				}
			} catch (MalformedQueryException e) {
				throw new IOException(e);
			} catch (QueryEvaluationException e) {
				throw new IOException(e);
			} finally {
				connection.close();
			}
		} catch (RepositoryException e) {
			throw new IOException(e);
		}
		return sub_supers;
	}

}
