package org.geneontology.minerva.json;

import java.util.Set;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;

public interface InferenceProvider {

	public boolean isConsistent();
	
	public Set<OWLClass> getTypes(OWLNamedIndividual i);
	
	public boolean isConformant();
	
	public Set<String> getNonconformant_uris();
}
