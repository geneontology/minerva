package org.geneontology.minerva.model;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.geneontology.minerva.BlazegraphOntologyManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;

public class GoCamModel extends ProvenanceAnnotated{
	BlazegraphOntologyManager go_lego;
	String modelstate;
	Set<String> in_taxon;
	String title;
	Set<String> imports;
	String oboinowlid;
	String iri;
	//the whole thing
	OWLOntology ont;
	//the discretized bits of activity flow
	Set<ActivityUnit> activities;
	Map<OWLNamedIndividual, Set<String>> ind_types;
	Map<OWLNamedIndividual, GoCamEntity> ind_entity;
	OWLClass mf; OWLClass bp; OWLClass cc;

	public GoCamModel(OWLOntology abox, BlazegraphOntologyManager go_lego_manager) throws IOException {
		ont = abox;
		mf =  ont.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0003674"));
		bp =  ont.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0008150"));
		cc =  ont.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0005575"));
		go_lego = go_lego_manager;
		iri = abox.getOntologyID().getOntologyIRI().get().toString();
		ind_entity = new HashMap<OWLNamedIndividual, GoCamEntity>();
		addAnnotations();
		addActivities();
	}

	private void addActivities() throws IOException {
		activities = new HashSet<ActivityUnit> ();
		ind_types = go_lego.getSuperCategoryMapForIndividuals(ont.getIndividualsInSignature(), ont);
		for(OWLNamedIndividual ind : ind_types.keySet()) {
			Set<String> types = ind_types.get(ind);
			if(types.contains(mf.getIRI().toString())) {
				ActivityUnit unit = new ActivityUnit(ind, ont, this);
				activities.add(unit);
				ind_entity.put(ind, unit);
			}
		}
	}

	private void addAnnotations() {
		Set<OWLAnnotation> annos = ont.getAnnotations();
		in_taxon = new HashSet<String>();
		comments = new HashSet<String>();
		notes = new HashSet<String>();
		for(OWLAnnotation anno : annos) {
			if(anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/title")) {
				title = anno.getValue().asLiteral().get().getLiteral();
			}
			if(anno.getProperty().getIRI().toString().equals("http://geneontology.org/lego/modelstate")) {
				modelstate = anno.getValue().asLiteral().get().getLiteral();
			}
			if(anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/contributor")) {
				contributor = anno.getValue().asLiteral().get().getLiteral();
			}
			if(anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/date")) {
				date = anno.getValue().asLiteral().get().getLiteral();
			}
			if(anno.getProperty().getIRI().toString().equals("http://purl.org/pav/providedBy")) {
				provided_by = anno.getValue().asLiteral().get().getLiteral();
			}
			if(anno.getProperty().getIRI().toString().equals("https://w3id.org/biolink/vocab/in_taxon")) {
				if(anno.getValue().asIRI().isPresent()) {
					String taxon = anno.getValue().toString();
					in_taxon.add(taxon);
				}	
			}
			if(anno.getProperty().getIRI().toString().equals("http://www.w3.org/2000/01/rdf-schema#comment")) {
				String comment = anno.getValue().asLiteral().get().toString();
				comments.add(comment);
			}
			if(anno.getProperty().getIRI().toString().equals("http://www.w3.org/2004/02/skos/core#note")) {
				String note = anno.getValue().asLiteral().get().toString();
				notes.add(note);
			}
		}

	}
	
	public String toString() {
		String g = title+"\n"+iri+"\n"+modelstate+"\n"+contributor+"\n"+date+"\n"+provided_by+"\n"+in_taxon+"\n";
		return g;
	}
	
	public GoCamModelStats getStats() {
		return new GoCamModelStats(this);
	}
	
}
