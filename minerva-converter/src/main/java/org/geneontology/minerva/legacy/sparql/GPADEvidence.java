package org.geneontology.minerva.legacy.sparql;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.semanticweb.owlapi.model.IRI;

public class GPADEvidence {

	private final IRI evidenceType;
	private final String reference;
	private final Optional<String> withOrFrom;
	private final String date;
	private final String assignedBy;
	private final Set<Pair<String, String>> annotations;
	private final Optional<IRI> interactingTaxon;


	public GPADEvidence(IRI evidenceType, String ref, Optional<String> withOrFrom, String date, String assignedBy, Set<Pair<String, String>> annotations, Optional<IRI> interactingTaxon) {
		this.evidenceType = evidenceType;
		this.reference = ref;
		this.withOrFrom = withOrFrom;
		this.date = date;
		this.assignedBy = assignedBy;
		this.annotations = annotations;
		this.interactingTaxon = interactingTaxon;
	}

	public String getReference() {
		return reference;
	}

	public IRI getEvidence() {
		return evidenceType;
	}

	public Optional<String> getWithOrFrom() {
		return withOrFrom;
	}

	public Optional<IRI> getInteractingTaxonID() {
		return interactingTaxon;
	}

	public String getDate() {
		return this.date;
	}

	public String getAssignedBy() {
		return this.assignedBy;
	}

	public Set<Pair<String, String>> getAnnotations() {
		return this.annotations;
	}

}
