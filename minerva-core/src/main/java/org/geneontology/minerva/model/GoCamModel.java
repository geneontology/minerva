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
	GoCamModelStats stats;

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
		this.setGoCamModelStats();
	}

	private void addActivities() throws IOException {
		activities = new HashSet<ActivityUnit> ();
		ind_types = go_lego.getSuperCategoryMapForIndividuals(ont.getIndividualsInSignature(), ont);
		for(OWLNamedIndividual ind : ind_types.keySet()) {
			Set<String> types = ind_types.get(ind);
			if(types!=null) {
				if(types.contains(mf.getIRI().toString())) {
					ActivityUnit unit = new ActivityUnit(ind, ont, this);
					activities.add(unit);
					ind_entity.put(ind, unit);
				}
			}
		}
	}

	private void addAnnotations() {
		Set<OWLAnnotation> annos = ont.getAnnotations();
		in_taxon = new HashSet<String>();
		comments = new HashSet<String>();
		notes = new HashSet<String>();
		contributors = new HashSet<String>();;
		provided_by = new HashSet<String>();;
		for(OWLAnnotation anno : annos) {
			if(anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/title")) {
				title = anno.getValue().asLiteral().get().getLiteral();
			}
			if(anno.getProperty().getIRI().toString().equals("http://geneontology.org/lego/modelstate")) {
				modelstate = anno.getValue().asLiteral().get().getLiteral();
			}
			if(anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/contributor")) {
				contributors.add(anno.getValue().asLiteral().get().getLiteral());
			}
			if(anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/date")) {
				date = anno.getValue().asLiteral().get().getLiteral();
			}
			if(anno.getProperty().getIRI().toString().equals("http://purl.org/pav/providedBy")) {
				provided_by.add(anno.getValue().asLiteral().get().getLiteral());
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
		String g = title+"\n"+iri+"\n"+modelstate+"\n"+contributors+"\n"+date+"\n"+provided_by+"\n"+in_taxon+"\n";
		return g;
	}

	public void setGoCamModelStats() {
		this.stats = new GoCamModelStats(this);
	}
	public GoCamModelStats getGoCamModelStats() {
		return this.stats;
	}

	public BlazegraphOntologyManager getGo_lego() {
		return go_lego;
	}

	public void setGo_lego(BlazegraphOntologyManager go_lego) {
		this.go_lego = go_lego;
	}

	public String getModelstate() {
		return modelstate;
	}

	public void setModelstate(String modelstate) {
		this.modelstate = modelstate;
	}

	public Set<String> getIn_taxon() {
		return in_taxon;
	}

	public void setIn_taxon(Set<String> in_taxon) {
		this.in_taxon = in_taxon;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Set<String> getImports() {
		return imports;
	}

	public void setImports(Set<String> imports) {
		this.imports = imports;
	}

	public String getOboinowlid() {
		return oboinowlid;
	}

	public void setOboinowlid(String oboinowlid) {
		this.oboinowlid = oboinowlid;
	}

	public String getIri() {
		return iri;
	}

	public void setIri(String iri) {
		this.iri = iri;
	}

	public OWLOntology getOnt() {
		return ont;
	}

	public void setOnt(OWLOntology ont) {
		this.ont = ont;
	}

	public Set<ActivityUnit> getActivities() {
		return activities;
	}

	public void setActivities(Set<ActivityUnit> activities) {
		this.activities = activities;
	}

	public Map<OWLNamedIndividual, Set<String>> getInd_types() {
		return ind_types;
	}

	public void setInd_types(Map<OWLNamedIndividual, Set<String>> ind_types) {
		this.ind_types = ind_types;
	}

	public Map<OWLNamedIndividual, GoCamEntity> getInd_entity() {
		return ind_entity;
	}

	public void setInd_entity(Map<OWLNamedIndividual, GoCamEntity> ind_entity) {
		this.ind_entity = ind_entity;
	}

	public OWLClass getMf() {
		return mf;
	}

	public void setMf(OWLClass mf) {
		this.mf = mf;
	}

	public OWLClass getBp() {
		return bp;
	}

	public void setBp(OWLClass bp) {
		this.bp = bp;
	}

	public OWLClass getCc() {
		return cc;
	}

	public void setCc(OWLClass cc) {
		this.cc = cc;
	}

	public GoCamModelStats getStats() {
		return stats;
	}

	public void setStats(GoCamModelStats stats) {
		this.stats = stats;
	}

}
