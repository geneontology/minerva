package org.geneontology.minerva.model;

import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;

public class GoCamOccurent extends GoCamEntity{
	public GoCamOccurent(OWLNamedIndividual ind, OWLOntology ont, GoCamModel model) {
		super(ind, ont, model);
	}
	Set<PhysicalEntity> outputs;
	Set<PhysicalEntity> inputs;
	Set<PhysicalEntity> regulating_entities;
	Set<AnatomicalEntity> locations;
	Set<AnatomicalEntity> transport_locations;
	//all causal links to other activities or processes 
	Map<OWLObjectProperty, Set<GoCamOccurent>> causal_out;
	Map<OWLObjectProperty, Set<GoCamOccurent>> causal_in;
	public Set<PhysicalEntity> getOutputs() {
		return outputs;
	}
	public void setOutputs(Set<PhysicalEntity> outputs) {
		this.outputs = outputs;
	}
	public Set<PhysicalEntity> getInputs() {
		return inputs;
	}
	public void setInputs(Set<PhysicalEntity> inputs) {
		this.inputs = inputs;
	}
	public Set<AnatomicalEntity> getLocations() {
		return locations;
	}
	public void setLocations(Set<AnatomicalEntity> locations) {
		this.locations = locations;
	}
	public Set<AnatomicalEntity> getTransport_locations() {
		return transport_locations;
	}
	public void setTransport_locations(Set<AnatomicalEntity> transport_locations) {
		this.transport_locations = transport_locations;
	}
	public Map<OWLObjectProperty, Set<GoCamOccurent>> getCausal_out() {
		return causal_out;
	}
	public void setCausal_out(Map<OWLObjectProperty, Set<GoCamOccurent>> causal_out) {
		this.causal_out = causal_out;
	}
	public Map<OWLObjectProperty, Set<GoCamOccurent>> getCausal_in() {
		return causal_in;
	}
	public void setCausal_in(Map<OWLObjectProperty, Set<GoCamOccurent>> causal_in) {
		this.causal_in = causal_in;
	}
	public Set<PhysicalEntity> getRegulating_entities() {
		return regulating_entities;
	}
	public void setRegulating_entities(Set<PhysicalEntity> regulating_entities) {
		this.regulating_entities = regulating_entities;
	}
	
}
