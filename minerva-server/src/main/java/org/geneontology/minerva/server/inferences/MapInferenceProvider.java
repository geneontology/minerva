package org.geneontology.minerva.server.inferences;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
import org.geneontology.minerva.server.validation.*;
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
	private Set<ModelValidationReport> validation_reports;
	
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
		//Aim to support multiple validation regimes - e.g. reasoner, shex, gorules
		Set<ModelValidationReport> all_validation_results = new HashSet<ModelValidationReport>();
		//reasoner
		ModelValidationReport reasoner_validation_report = new ModelValidationReport(
				"GORULE:OWL_REASONER",
				"https://github.com/geneontology/helpdesk/issues", 
				"https://github.com/geneontology/go-ontology",
				isConsistent);
		if(!isConsistent) {
			Violation i_v = new Violation("id of inconsistent node");
			i_v.setCommentary("comment about why");
			reasoner_validation_report.addViolation(i_v);
		}
		all_validation_results.add(reasoner_validation_report);
		//shex
		//generate an RDF model
		Model model = getModel(ont);
		//add superclasses to types used in model
		//TODO examine how to get this done with reasoner here, avoiding external call 
		model = shex.enrichSuperClasses(model);
		ModelValidationReport validation_report = null;
		try {
			ShexValidationResult result = shex.runShapeMapValidation(model, true);
			validation_report = new ModelValidationReport(
					"GORULE:SHEX_SCHEMA",
					"https://github.com/geneontology/go-shapes/issues", 
					"https://github.com/geneontology/go-shapes/blob/master/shapes/go-cam-shapes.shex",
					result.model_is_valid);
			for(String bad_node : result.node_is_valid.keySet()) {
				if(!(result.node_is_valid.get(bad_node))) {
					ShexViolation violation = new ShexViolation(bad_node);
					violation.setCommentary("Some explanatory text would go here");
					ShexExplanation explanation = new ShexExplanation();
					explanation.setShape_id("the shape id that this node should fit here");
					ShexConstraint constraint = new ShexConstraint("unmatched_property_id", "Range of property id");
					explanation.addConstraint(constraint);
					violation.addExplanation(explanation);
					validation_report.addViolation(violation);
				}
			}
			if(validation_report!=null) {
				all_validation_results.add(validation_report);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return new MapInferenceProvider(isConsistent, inferredTypes, all_validation_results);
	}

	
	


	public static class ShexValidationResult {
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
		public ShexValidationResult(Model model) {
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

	MapInferenceProvider(boolean isConsistent, Map<OWLNamedIndividual, Set<OWLClass>> inferredTypes, Set<ModelValidationReport> validation_reports) {
		this.isConsistent = isConsistent;
		this.inferredTypes = inferredTypes;
		this.validation_reports = validation_reports;
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

	public Set<ModelValidationReport> getValidation_reports() {
		return validation_reports;
	}
}
