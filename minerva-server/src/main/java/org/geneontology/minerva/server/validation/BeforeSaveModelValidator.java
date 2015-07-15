package org.geneontology.minerva.server.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.MolecularModelManager;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.util.IdStringManager.AnnotationShorthand;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class BeforeSaveModelValidator {
	
	static boolean USE_CONSISTENCY_CHECKS = false;

	public List<String> validateBeforeSave(String modelId, MolecularModelManager<?> modelManager, boolean useModuleReasoner) throws UnknownIdentifierException, OWLOntologyCreationException {
		// get model
		ModelContainer model = modelManager.checkModelId(modelId);
		List<String> errors = new ArrayList<String>(3);
		// check that model has required meta data
		OWLOntology aboxOntology = model.getAboxOntology();
		boolean hasTitle = false;
		boolean hasContributor = false;
		
		// get ontology annotations
		Set<OWLAnnotation> annotations = aboxOntology.getAnnotations();
		for (OWLAnnotation annotation : annotations) {
			OWLAnnotationProperty p = annotation.getProperty();
			AnnotationShorthand legoType = AnnotationShorthand.getShorthand(p.getIRI());
			if (legoType != null) {
				// check for title
				if (AnnotationShorthand.title.equals(legoType)) {
					hasTitle = true;
				}
				// check for contributor
				else if (AnnotationShorthand.contributor.equals(legoType)) {
					hasContributor = true;
				}
			}
		}

		if (hasTitle == false) {
			errors.add("The model has no title. All models must have a human readable title.");
		}
		if (hasContributor == false) {
			errors.add("The model has no contributors. All models must have an association with their contributors.");
		}
		
		// check that model is consistent
		if (USE_CONSISTENCY_CHECKS) {
			OWLReasoner reasoner;
			if (useModuleReasoner) {
				reasoner = model.getModuleReasoner();
			}
			else {
				reasoner = model.getReasoner();
			}
			if (reasoner.isConsistent() == false) {
				errors.add("The model is inconsistent. A Model must be consistent to be saved.");
			}
		}
		
		// require at least one declared instance
		Set<OWLNamedIndividual> individuals = aboxOntology.getIndividualsInSignature();
		if (individuals.isEmpty()) {
			errors.add("The model has no individuals. Empty models should not be saved.");
		}
		
		// avoid returning empty list
		if (errors.isEmpty()) {
			errors = null;
		}
		return errors;
	}
}
