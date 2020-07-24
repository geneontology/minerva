package org.geneontology.minerva.model;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.geneontology.minerva.BlazegraphOntologyManager;
import org.semanticweb.owlapi.model.OWLAnnotation;
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
	
	public GoCamModel(OWLOntology abox, BlazegraphOntologyManager go_lego_manager) throws IOException {
		ont = abox;
		go_lego = go_lego_manager;
		iri = abox.getOntologyID().getOntologyIRI().get().toString();
		addAnnotations();
		addActivities();
	}

	private void addActivities() throws IOException {
		activities = new HashSet<ActivityUnit> ();
		Map<OWLNamedIndividual, Set<String>> ind_types = go_lego.getSuperCategoryMapForIndividuals(ont.getIndividualsInSignature(), ont);
		for(OWLNamedIndividual ind : ind_types.keySet()) {
			Set<String> types = ind_types.get(ind);
			if(types.contains("http://purl.obolibrary.org/obo/GO_0003674")) {
				ActivityUnit unit = new ActivityUnit(ind, ont, ind_types);
				activities.add(unit);
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
				String taxon = anno.getValue().asIRI().get().toString();
				in_taxon.add(taxon);
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
		String g = title+"\n"+iri+"\n"+oboinowlid+"\n"+modelstate+"\n"+contributor+"\n"+date+"\n"+provided_by+"\n"+in_taxon+"\n"+notes+"\n";
		int n_activity_units = activities.size();
		int n_complete_activity_units = 0;
		int n_connected_processes = 0;
		int n_causal_relation_assertions = 0;
		for(ActivityUnit a : activities) {
			if((a.containing_processes.size()==1)&&
			   (a.enablers.size()==1)&&
			   (a.locations.size()==1)) {
				n_complete_activity_units++;
			}
			Set<String> p = new HashSet<String>();
			if(a.containing_processes!=null) {
				for(BiologicalProcessUnit bpu : a.containing_processes) {
					p.add(bpu.individual.toString());
				}
			}
			n_connected_processes = p.size();
			for(OWLObjectProperty prop : a.causal_out.keySet()) {
				Set<GoCamOccurent> ocs = a.causal_out.get(prop);
				for(GoCamOccurent oc : ocs ) {
					n_causal_relation_assertions++;
				}
			}
		}
		return g;
	}
}
