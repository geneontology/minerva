package org.geneontology.minerva.server.inferences;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.geneontology.minerva.json.InferenceProvider;
import org.geneontology.minerva.server.validation.*;
import org.geneontology.minerva.util.JenaOwlTool;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class MapInferenceProvider implements InferenceProvider {
 
	private final boolean isConsistent;
	private final Map<OWLNamedIndividual, Set<OWLClass>> inferredTypes;
	private final Map<OWLNamedIndividual, Set<OWLClass>> inferredTypesWithIndirects;
	//for shex and other validation
	private Set<ModelValidationReport> validation_reports;
	
	public static InferenceProvider create(OWLReasoner r, OWLOntology ont, ShexValidator shex) {
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
				//TODO consider filtering down to root types - depending on uses cases
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
		//Aim to support multiple validation regimes - e.g. reasoner, shex, gorules
		Set<ModelValidationReport> all_validation_results = new HashSet<ModelValidationReport>();
		//reasoner
		ModelValidationReport reasoner_validation_report = new OWLValidationReport();
		reasoner_validation_report.setConformant(isConsistent);
		if(!isConsistent) {
			Violation i_v = new Violation("id of inconsistent node");
			i_v.setCommentary("comment about why");
			reasoner_validation_report.addViolation(i_v);
		}
		all_validation_results.add(reasoner_validation_report);
		//shex
		//generate an RDF model
		Model model = JenaOwlTool.getJenaModel(ont);
		//add superclasses to types used in model 
		model = shex.enrichSuperClasses(model);
		ModelValidationReport validation_report = null;
		try {
			validation_report = shex.createValidationReport(model);
			if(validation_report!=null) {
				all_validation_results.add(validation_report);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
		return new MapInferenceProvider(isConsistent, inferredTypes, inferredTypesWithIndirects, all_validation_results);
	}

	MapInferenceProvider(boolean isConsistent, Map<OWLNamedIndividual, Set<OWLClass>> inferredTypes, Map<OWLNamedIndividual, Set<OWLClass>> inferredTypesWithIndirects, Set<ModelValidationReport> validation_reports) {
		this.isConsistent = isConsistent;
		this.inferredTypes = inferredTypes;
		this.inferredTypesWithIndirects = inferredTypesWithIndirects;
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

	public Set<ModelValidationReport> getValidation_reports() {
		return validation_reports;
	}
}
