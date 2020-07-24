package org.geneontology.minerva.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.search.EntitySearcher;

public class GoCamEntity extends ProvenanceAnnotated{
	String xref;
	String label;
	String exact_match;
	OWLNamedIndividual individual;
	Set<OWLClass> direct_types;
	Set<OWLClass> indirect_types;
	String derived_from;
	
	public GoCamEntity(OWLNamedIndividual ind, OWLOntology ont) {
		addAnnotations(ind, ont);
		individual = ind;
		direct_types = new HashSet<OWLClass>();
		for(OWLClassExpression ce : EntitySearcher.getTypes(ind, ont)) {
			if(!ce.isAnonymous()) {
				direct_types.add(ce.asOWLClass());
			}
		}
	}
	
	private void addAnnotations(OWLNamedIndividual ind, OWLOntology ont) {
		
		Collection<OWLAnnotation> annos = EntitySearcher.getAnnotations(ind, ont);
		comments = new HashSet<String>();
		notes = new HashSet<String>();
		for(OWLAnnotation anno : annos) {
			if(anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/contributor")) {
				contributor = anno.getValue().asLiteral().get().getLiteral();
			}
			else if(anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/date")) {
				date = anno.getValue().asLiteral().get().getLiteral();
			}
			else if(anno.getProperty().getIRI().toString().equals("http://purl.org/pav/providedBy")) {
				provided_by = anno.getValue().asLiteral().get().getLiteral();
			}
			else if(anno.getProperty().getIRI().toString().equals("http://www.w3.org/2000/01/rdf-schema#comment")) {
				String comment = anno.getValue().asLiteral().get().toString();
				comments.add(comment);
			}
			else if(anno.getProperty().getIRI().toString().equals("http://www.w3.org/2004/02/skos/core#note")) {
				String note = anno.getValue().asLiteral().get().toString();
				notes.add(note);
			}
			else if(anno.getProperty().getIRI().toString().equals("http://www.geneontology.org/formats/oboInOwl#hasDbXref")) {
				xref = anno.getValue().asLiteral().get().toString();
			}
			else if(anno.getProperty().getIRI().toString().equals("http://www.w3.org/2000/01/rdf-schema#label")) {
				label = anno.getValue().asLiteral().get().toString();
			}
			else if(anno.getProperty().getIRI().toString().equals("http://www.w3.org/2004/02/skos/core#exactMatch")) {
				exact_match = anno.getValue().asIRI().get().toString();
			}
		}
		
	}
}
