package org.geneontology.minerva.server.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

public class BeforeSaveModelValidator {
	
	public List<String> validateBeforeSave(ModelContainer model) throws OWLOntologyCreationException {
		// get model
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
