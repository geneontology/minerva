package org.geneontology.minerva.json;

import org.geneontology.minerva.validation.ValidationResultSet;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;

import java.util.Set;

public interface InferenceProvider {

    public boolean isConsistent();

    public Set<OWLClass> getTypes(OWLNamedIndividual i);

    public ValidationResultSet getValidation_results();

    Set<OWLClass> getAllTypes(OWLNamedIndividual i);
}
