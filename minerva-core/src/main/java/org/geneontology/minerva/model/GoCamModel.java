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

	public GoCamModel(OWLOntology abox, BlazegraphOntologyManager go_lego_manager) throws IOException {
		ont = abox;
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
			if(types.contains("http://purl.obolibrary.org/obo/GO_0003674")) {
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
		String g = title+"\n"+iri+"\n"+modelstate+"\n"+contributor+"\n"+date+"\n"+provided_by+"\n"+in_taxon+"\n";
		int n_activity_units = activities.size();
		int n_complete_activity_units = 0;
		int n_connected_processes = 0;
		int n_causal_out_relation_assertions = 0;
		int n_unconnected = 0;
		int n_unconnected_out = 0;
		int n_unconnected_in = 0;
		int n_raw_mf = 0;
		int n_no_enabler = 0;
		int n_no_location = 0;
		int n_no_bp = 0;
		int max_connected_graph = 0;
		OWLClass mol_fun = ont.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0003674"));
		DescriptiveStatistics mf_depth = new DescriptiveStatistics();
		DescriptiveStatistics cc_depth = new DescriptiveStatistics();
		for(ActivityUnit a : activities) {
			Set<GoCamOccurent> downstream = a.getDownstream(a);
			for(OWLClass oc : a.direct_types) {
				try {
					//this is a little slow.  Could cache it to speed it up.  
					int depth = go_lego.getClassDepth(oc.getIRI().toString(), "http://purl.obolibrary.org/obo/GO_0003674");
					if(depth!=-1) {
						mf_depth.addValue(depth+1); //measure starts at 0
					}
				} catch (IOException e) {
					//TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if(downstream.size()>max_connected_graph) {
				max_connected_graph = downstream.size();
			}
			if(a.direct_types.contains(mol_fun)) {
				n_raw_mf++;
			}
			if(a.enablers.size()==0) {
				n_no_enabler++;
			}
			if(a.locations.size()==0) {
				n_no_location++;
			}
			for(AnatomicalEntity ae : a.locations) {
				for(OWLClass oc : ae.direct_types) {
					try {
						//this is a little slow.  Could cache it to speed it up.  
						int depth = go_lego.getClassDepth(oc.getIRI().toString(), "http://purl.obolibrary.org/obo/GO_0005575");
						if(depth!=-1) {
							cc_depth.addValue(depth+1); //measure starts at 0
						}
					} catch (IOException e) {
						//TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			if(a.containing_processes.size()==0) {
				n_no_bp++;
			}
			if(a.causal_out.size()==0) {
				n_unconnected_out++;
			}
			if(a.causal_in.size()==0) {
				n_unconnected_in++;
			}
			if(a.causal_in.size()==0&&a.causal_out.size()==0) {
				n_unconnected++;
			}
			if((a.containing_processes.size()==1)&&
					(a.enablers.size()==1)&&
					(a.locations.size()==1)&&
					(!a.direct_types.contains(mol_fun))) {
				n_complete_activity_units++;
			}
			Set<String> p = new HashSet<String>();
			if(a.containing_processes!=null) {
				for(BiologicalProcessUnit bpu : a.containing_processes) {
					p.add(bpu.individual.toString());
				}
			}
			n_connected_processes = p.size();
			if(a.causal_out!=null) {
				for(OWLObjectProperty prop : a.causal_out.keySet()) {
					Set<GoCamOccurent> ocs = a.causal_out.get(prop);
					for(GoCamOccurent oc : ocs ) {
						n_causal_out_relation_assertions++;
					}
				}
			}
		}
		g+=" activity units "+n_activity_units+"\n";
		g+=" n complete activity units "+n_complete_activity_units+"\n";
		g+=" n root MF activity units "+n_raw_mf+"\n";
		g+=" n unenabled activity units "+n_no_enabler+"\n";
		g+=" n unlocated activity units "+n_no_location+"\n";
		g+=" n activity units unconnected to a BP "+n_no_bp+"\n";
		g+=" n connected biological processes "+n_connected_processes+"\n";
		g+=" n causal relation assertions "+n_causal_out_relation_assertions+"\n";
		g+=" n unconnected activities "+n_unconnected+"\n";
		g+=" n activities with no outgoing connections "+n_unconnected_out+"\n";
		g+=" n activities with no incoming connections "+n_unconnected_in+"\n";
		g+=" max length of connected causal subgraph "+max_connected_graph+"\n";
		g+=" descriptive statistics for depth in ontology for MF terms in activity units \n";
		g+="\t mean:"+mf_depth.getMean()+"\n";
		g+="\t median:"+mf_depth.getPercentile(50)+"\n";
		g+="\t max:"+mf_depth.getMax()+"\n";
		g+="\t min:"+mf_depth.getMin()+"\n";
		g+=" descriptive statistics for depth in ontology for CC terms in activity units \n";
		g+="\t mean:"+cc_depth.getMean()+"\n";
		g+="\t median:"+cc_depth.getPercentile(50)+"\n";
		g+="\t max:"+cc_depth.getMax()+"\n";
		g+="\t min:"+cc_depth.getMin()+"\n";
		return g;
	}
}
