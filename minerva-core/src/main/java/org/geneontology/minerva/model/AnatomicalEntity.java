package org.geneontology.minerva.model;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;

public class AnatomicalEntity extends GoCamEntity{

	public AnatomicalEntity(OWLNamedIndividual loc_ind, OWLOntology ont, GoCamModel model) {
		super(loc_ind, ont, model);
	}

}
