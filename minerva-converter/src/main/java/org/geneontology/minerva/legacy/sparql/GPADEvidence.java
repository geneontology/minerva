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

	@Override
	public boolean equals(Object other) {
		if (this == other) { return true; }
		else if (!(other instanceof GPADEvidence)) { return false; }
		else {
			GPADEvidence otherEvidence = (GPADEvidence)other;
			return  this.getReference().equals(otherEvidence.getReference())
					&& this.getEvidence().equals(otherEvidence.getEvidence())
					&& this.getWithOrFrom().equals(otherEvidence.getWithOrFrom())
					&& this.getInteractingTaxonID().equals(otherEvidence.getInteractingTaxonID())
					&& this.getDate().equals(otherEvidence.getDate())
					&& this.getAssignedBy().equals(otherEvidence.getAssignedBy())
					&& this.getAnnotations().equals(otherEvidence.getAnnotations());
		}
	}

	@Override
	public int hashCode() {
		int result = 17;
		result = 37 * result + this.getReference().hashCode();
		result = 37 * result + this.getEvidence().hashCode();
		result = 37 * result + this.getReference().hashCode();
		result = 37 * result + this.getWithOrFrom().hashCode();
		result = 37 * result + this.getInteractingTaxonID().hashCode();
		result = 37 * result + this.getDate().hashCode();
		result = 37 * result + this.getAssignedBy().hashCode();
		result = 37 * result + this.getAnnotations().hashCode();
		return result;
	}

}
