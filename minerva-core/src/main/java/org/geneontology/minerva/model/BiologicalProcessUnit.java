package org.geneontology.minerva.model;

import java.util.Set;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;


public class BiologicalProcessUnit extends GoCamOccurent{
	public BiologicalProcessUnit(OWLNamedIndividual enabler_ind, OWLOntology ont) {
		super(enabler_ind, ont);
	}
	Set<PhysicalEntity>  transports;
	AnatomicalEntity start_location;
	AnatomicalEntity end_location;
}
