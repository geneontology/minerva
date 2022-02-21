package org.geneontology.minerva.curie;

import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.semanticweb.owlapi.model.*;

import java.util.Map;

public interface CurieHandler {

    public String getCuri(OWLClass cls);

    public String getCuri(OWLNamedIndividual i);

    public String getCuri(OWLObjectProperty p);

    public String getCuri(OWLDataProperty p);

    public String getCuri(OWLAnnotationProperty p);

    public String getCuri(IRI iri);

    public IRI getIRI(String curi) throws UnknownIdentifierException;

    public Map<String, String> getMappings();

}
