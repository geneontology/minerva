package org.geneontology.minerva.server.inferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.rdf.simple.SimpleRDF;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.geneontology.minerva.json.InferenceProvider;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class MapInferenceProvider implements InferenceProvider {

	private final boolean isConsistent;
	private final Map<OWLNamedIndividual, Set<OWLClass>> inferredTypes;
	//for shex-based validation
	private final boolean isConformant;
	private Set<String> nonconformant_uris; 
	public static final String endpoint = "http://rdf.geneontology.org/blazegraph/sparql";
	
	public static InferenceProvider create(OWLReasoner r, OWLOntology ont, ShexController shex) {
		Map<OWLNamedIndividual, Set<OWLClass>> inferredTypes = new HashMap<>();
		boolean isConsistent = r.isConsistent();
		//TODO
		//Could get all inferred super types here (e.g. MF, BP) and return in response.
		if (isConsistent) {
			Set<OWLNamedIndividual> individuals = ont.getIndividualsInSignature();
			for (OWLNamedIndividual individual : individuals) {
				Set<OWLClass> inferred = new HashSet<>();
				Set<OWLClass> flattened = r.getTypes(individual, true).getFlattened();
				for (OWLClass cls : flattened) {
					if (cls.isBuiltIn() == false) {
						inferred.add(cls);
					}
				}
				inferredTypes.put(individual, inferred);
			}
		}
		//shex
		boolean isConformant = true;
		Set<String> nonconformant_uris = new HashSet<String>();
		//generate an RDF model
		Model model = getModel(ont);
		//add superclasses to types used in model
		//TODO examine how to get this done with reasoner here, avoiding external call 
		model = enrichSuperClasses(model);
		
		//TODO get this from a cache
		//refactor all these methods into new class
		//store on startup and re-use

		try {
			ModelValidationResult result = shex.runShapeMapValidation(model, true);
			isConformant = result.model_is_valid;
			for(String bad_node : result.node_is_valid.keySet()) {
				if(!(result.node_is_valid.get(bad_node))) {
					nonconformant_uris.add(bad_node);
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return new MapInferenceProvider(isConsistent, inferredTypes, isConformant, nonconformant_uris);
	}

	public static Model enrichSuperClasses(Model model) {
		String getOntTerms = 
				"PREFIX owl: <http://www.w3.org/2002/07/owl#> "
						+ "SELECT DISTINCT ?term " + 
						"        WHERE { " + 
						"        ?ind a owl:NamedIndividual . " + 
						"        ?ind a ?term . " + 
						"        FILTER(?term != owl:NamedIndividual)" + 
						"        FILTER(isIRI(?term)) ." + 
						"        }";
		String terms = "";
		try{
			QueryExecution qe = QueryExecutionFactory.create(getOntTerms, model);
			ResultSet results = qe.execSelect();

			while (results.hasNext()) {
				QuerySolution qs = results.next();
				Resource term = qs.getResource("term");
				terms+=("<"+term.getURI()+"> ");
			}
			qe.close();
		} catch(QueryParseException e){
			e.printStackTrace();
		}
		String superQuery = ""
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#> "
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
				+ "CONSTRUCT { " + 
				"        ?term rdfs:subClassOf ?superclass ." + 
				"        ?term a owl:Class ." + 
				"        }" + 
				"        WHERE {" + 
				"        VALUES ?term { "+terms+" } " + 
				"        ?term rdfs:subClassOf* ?superclass ." + 
				"        FILTER(isIRI(?superclass)) ." + 
				"        }";

		Query query = QueryFactory.create(superQuery); 
		try ( 
				QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, query) ) {
			qexec.execConstruct(model);
			qexec.close();
		} catch(QueryParseException e){
			e.printStackTrace();
		}
		return model;
	}
	


	public static class ModelValidationResult {
		boolean model_is_valid; 
		boolean model_is_consistent;
		Map<String, Set<String>> node_shapes;
		Map<String, Set<String>> node_types;
		Map<String, String> node_report;
		Map<String, Boolean> node_is_valid = new HashMap<String, Boolean>();
		Map<String, Boolean> node_is_consistent;
		String model_report;
		String model_id;
		String model_title;
		/**
		 * 
		 */
		public ModelValidationResult(Model model) {
			String q = "select ?cam ?title where {"
					+ "?cam <http://purl.org/dc/elements/1.1/title> ?title }";
			//	+ "?cam <"+DC.description.getURI()+"> ?title }";
			QueryExecution qe = QueryExecutionFactory.create(q, model);
			ResultSet results = qe.execSelect();
			if (results.hasNext()) {
				QuerySolution qs = results.next();
				Resource id = qs.getResource("cam");
				Literal title = qs.getLiteral("title");
				model_id = id.getURI();
				model_title = title.getString();
			}
			qe.close();
			model_report = "shape id\tnode uri\tvalidation status\n";
		}

	}
	
	/**
	 * From https://stackoverflow.com/questions/46866783/conversion-from-owlontology-to-jena-model-in-java
	 * Converts an OWL API ontology into a JENA API model.
	 * @param ontology the OWL API ontology
	 * @return the JENA API model
	 */
	public static Model getModel(final OWLOntology ontology) {
		Model model = ModelFactory.createDefaultModel();

		try (PipedInputStream is = new PipedInputStream(); PipedOutputStream os = new PipedOutputStream(is)) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						ontology.getOWLOntologyManager().saveOntology(ontology, new TurtleDocumentFormat(), os);
						os.close();
					} catch (OWLOntologyStorageException | IOException e) {
						e.printStackTrace();
					}
				}
			}).start();
			model.read(is, null, "TURTLE");
			return model;
		} catch (Exception e) {
			throw new RuntimeException("Could not convert OWL API ontology to JENA API model.", e);
		}
	}

	MapInferenceProvider(boolean isConsistent, Map<OWLNamedIndividual, Set<OWLClass>> inferredTypes, boolean isConformant, Set<String> nonconformant_uris) {
		this.isConsistent = isConsistent;
		this.inferredTypes = inferredTypes;
		this.isConformant = isConformant;
		this.nonconformant_uris = nonconformant_uris;
	}

	@Override
	public boolean isConsistent() {
		return isConsistent;
	}

	@Override
	public Set<OWLClass> getTypes(OWLNamedIndividual i) {
		Set<OWLClass> result = Collections.emptySet();
		if (isConsistent && i != null) {
			Set<OWLClass> inferences = inferredTypes.get(i);
			if (inferences != null) {
				result = Collections.unmodifiableSet(inferences);
			}
		}
		return result;
	}

	@Override
	public boolean isConformant() {
		return isConformant;
	}

	@Override
	public Set<String> getNonconformant_uris() {
		return nonconformant_uris;
	}
}
