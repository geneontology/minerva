package org.geneontology.minerva.curie;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;

public interface CurieHandler {

	public String getCuri(OWLClass cls);
	
	public String getCuri(OWLNamedIndividual i);
	
	public String getCuri(OWLObjectProperty p);
	
	public String getCuri(OWLDataProperty p);
	
	public String getCuri(OWLAnnotationProperty p);
	
	public String getCuri(IRI iri);
	
	public IRI getIRI(String curi);

}
