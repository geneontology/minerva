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
	Set<AnatomicalEntity> locations;
	//all causal links to other activities or processes 
	Map<OWLObjectProperty, Set<GoCamOccurent>> causal_out;
	Map<OWLObjectProperty, Set<GoCamOccurent>> causal_in;
}
