package org.geneontology.minerva.legacy.sparql;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.semanticweb.owlapi.model.IRI;

public class EvidencedGPADData implements GPADData {

	private final BasicGPADData basicData;
	private final GPADEvidence evidence;

	EvidencedGPADData(BasicGPADData basicData, GPADEvidence evidence) {
		this.basicData = basicData;
		this.evidence = evidence;
	}

	@Override
	public IRI getObject() {
		return this.basicData.getObject();
	}

	@Override
	public IRI getQualifier() {
		return this.basicData.getQualifier();
	}

	@Override
	public IRI getOntologyClass() {
		return this.basicData.getOntologyClass();
	}

	@Override
	public Set<ConjunctiveExpression> getAnnotationExtensions() {
		return this.basicData.getAnnotationExtensions();
	}

	@Override
	public String getReference() {
		return this.evidence.getReference();
	}

	@Override
	public IRI getEvidence() {
		return this.evidence.getEvidence();
	}

	@Override
	public Optional<String> getWithOrFrom() {
		return this.evidence.getWithOrFrom();
	}

	@Override
	public Optional<IRI> getInteractingTaxonID() {
		return this.evidence.getInteractingTaxonID();
	}

	@Override
	public String getDate() {
		return this.evidence.getDate();
	}

	@Override
	public String getAssignedBy() {
		return this.evidence.getAssignedBy();
	}

	@Override
	public Set<Pair<String, String>> getAnnotations() {
		return this.evidence.getAnnotations();
	}

}
