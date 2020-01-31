package org.geneontology.minerva.server.inferences;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.log4j.Logger;
import org.geneontology.minerva.json.InferenceProvider;
import org.geneontology.minerva.server.validation.MinervaShexValidator;
import org.geneontology.minerva.util.JenaOwlTool;
import org.geneontology.minerva.validation.OWLValidationReport;
import org.geneontology.minerva.validation.ShexValidationReport;
import org.geneontology.minerva.validation.ValidationResultSet;
import org.geneontology.minerva.validation.Violation;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class MapInferenceProvider implements InferenceProvider {
	private static final Logger LOGGER = Logger.getLogger(InferenceProvider.class);
	private final boolean isConsistent;
	private final Map<OWLNamedIndividual, Set<OWLClass>> inferredTypes;
	private final Map<OWLNamedIndividual, Set<OWLClass>> inferredTypesWithIndirects;

	//for shex and other validation
	private ValidationResultSet validation_results;

	MapInferenceProvider(boolean isConsistent, Map<OWLNamedIndividual, Set<OWLClass>> inferredTypes, Map<OWLNamedIndividual, Set<OWLClass>> inferredTypesWithIndirects, ValidationResultSet validation_reports) {
		this.isConsistent = isConsistent;
		this.inferredTypes = inferredTypes;
		this.inferredTypesWithIndirects = inferredTypesWithIndirects;
		this.validation_results = validation_reports;
	}
	
	public static InferenceProvider create(OWLReasoner r, OWLOntology ont, MinervaShexValidator shex) throws OWLOntologyCreationException {
		Map<OWLNamedIndividual, Set<OWLClass>> inferredTypes = new HashMap<>();
		Map<OWLNamedIndividual, Set<OWLClass>> inferredTypesWithIndirects = new HashMap<>();
		boolean isConsistent = r.isConsistent();
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
				//adding the rest of the types
				//TODO consider filtering down to root types - depending on use cases
				Set<OWLClass> all_inferred = new HashSet<>();
				Set<OWLClass> all_flattened = r.getTypes(individual, false).getFlattened();
				for (OWLClass cls : all_flattened) {
					if (cls.isBuiltIn() == false) {
						all_inferred.add(cls);
					}
				}
				inferredTypesWithIndirects.put(individual, all_inferred);
			}
		}
		//reasoner
		OWLValidationReport reasoner_validation = new OWLValidationReport();
		reasoner_validation.setConformant(isConsistent);
		if(!isConsistent) {
			Violation i_v = new Violation("id of inconsistent node");
			reasoner_validation.addViolation(i_v);
		}
		//shex
		ShexValidationReport shex_validation = null;	
		if(shex.isActive()) {
			//generate an RDF model
			Model model = JenaOwlTool.getJenaModel(ont);
			//add superclasses to types used in model 
			model = shex.enrichSuperClasses(model);	
			try {
				boolean stream_output_for_debug = false;
				shex_validation = shex.runShapeMapValidation(model, stream_output_for_debug);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
		}
		ValidationResultSet all_validations = new ValidationResultSet(reasoner_validation, shex_validation);
		return new MapInferenceProvider(isConsistent, inferredTypes, inferredTypesWithIndirects, all_validations);
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
	public Set<OWLClass> getAllTypes(OWLNamedIndividual i) {
		Set<OWLClass> result = Collections.emptySet();
		if (isConsistent && i != null) {
			Set<OWLClass> inferences = inferredTypesWithIndirects.get(i);
			if (inferences != null) {
				result = Collections.unmodifiableSet(inferences);
			}
		}
		return result;
	}

	public ValidationResultSet getValidation_results() {
		return validation_results;
	}



}
