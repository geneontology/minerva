package org.geneontology.minerva.json;

import java.util.Set;

import org.geneontology.minerva.validation.ValidationResultSet;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;

public interface InferenceProvider {

	public boolean isConsistent();
	
	public Set<OWLClass> getTypes(OWLNamedIndividual i);
	
	public ValidationResultSet  getValidation_results();

	Set<OWLClass> getAllTypes(OWLNamedIndividual i);
}
