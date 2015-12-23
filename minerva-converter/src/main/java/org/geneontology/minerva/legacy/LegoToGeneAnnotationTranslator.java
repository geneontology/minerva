package org.geneontology.minerva.legacy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;

import owltools.gaf.BioentityDocument;
import owltools.gaf.GafDocument;
import owltools.gaf.eco.SimpleEcoMapper;
import owltools.graph.OWLGraphWrapper;

public class LegoToGeneAnnotationTranslator extends AbstractLegoTranslator {

	public LegoToGeneAnnotationTranslator(OWLOntology model, CurieHandler curieHandler, SimpleEcoMapper mapper) {
		super(model, curieHandler, mapper);
	}

	@Override
	protected boolean isEco(OWLClass cls) {
		String identifier = curieHandler.getCuri(cls);
		return identifier != null && identifier.startsWith("ECO:");
	}

	@Override
	public void translate(OWLOntology modelAbox, ExternalLookupService lookup, GafDocument annotations, BioentityDocument entities, List<String> additionalRefs) {
		Set<Summary> summaries = new HashSet<Summary>();
		walkModel(modelAbox, lookup, summaries);
		
		final OWLGraphWrapper modelGraph = new OWLGraphWrapper(modelAbox);
		for(Summary summary : summaries) {
			if (summary.entity != null) {
				addAnnotations(modelGraph, lookup, summary, additionalRefs, annotations, entities);
			}
		}
	}

	@Override
	protected Summary initPayload(OWLNamedIndividual object,
			OWLClass objectType, OWLOntology model, OWLGraphWrapper modelGraph, ExternalLookupService lookup) {
		Summary summary = new Summary();
		summary.entity = objectType;
		summary.entityTaxon = getEntityTaxon(objectType, model);
		summary.entityType = getEntityType(objectType, object, modelGraph, lookup);
		return summary;
	}

	@Override
	protected boolean handleCC(Summary payload, OWLClass cls,
			Metadata metadata,
			Set<OWLObjectSomeValuesFrom> expressions) {
		boolean added = false;
		if (isCc(cls)) {
			added = payload.addCc(cls, metadata, expressions);
		}
		return added;
	}

	@Override
	protected boolean handleMF(Summary payload, OWLClass cls,
			Metadata metadata,
			Set<OWLObjectSomeValuesFrom> expressions) {
		if (isMf(cls)) {
			payload.addMf(cls, metadata, expressions);
		}
		return true;
	}

	@Override
	protected boolean handleBP(Summary payload, OWLClass cls,
			Metadata metadata,
			Set<OWLObjectSomeValuesFrom> expressions) {
		boolean added = false;
		if (isBp(cls)) {
			added = payload.addBp(cls, metadata, expressions);
		}
		return added;
	}

}
