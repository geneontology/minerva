package org.geneontology.minerva.model;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.Set;


public class BiologicalProcessUnit extends GoCamOccurent {
    public BiologicalProcessUnit(OWLNamedIndividual enabler_ind, OWLOntology ont, GoCamModel model) {
        super(enabler_ind, ont, model);
    }

    Set<PhysicalEntity> transports;
    AnatomicalEntity start_location;
    AnatomicalEntity end_location;
}
