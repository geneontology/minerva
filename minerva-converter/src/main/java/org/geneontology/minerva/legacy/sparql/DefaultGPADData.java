package org.geneontology.minerva.legacy.sparql;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.semanticweb.owlapi.model.IRI;

public class DefaultGPADData implements GPADData {

	private final IRI object;
	private final IRI qualifier;
	private final IRI ontologyClass;
	private final Set<ConjunctiveExpression> annotationExtensions;
	private final String reference;
	private final IRI evidence;
	private final Optional<String> withOrFrom;
	private final Optional<IRI> interactingTaxon;
	private final String date;
	private final String assignedBy;
	private final Set<Pair<String, String>> annotations;

	public DefaultGPADData(IRI object, IRI qualifier, IRI ontologyClass, Set<ConjunctiveExpression> annotationExtensions, 
			String reference, IRI evidence, Optional<String> withOrFrom, Optional<IRI> interactingTaxon, 
			String date, String assignedBy, Set<Pair<String, String>> annotations) {
		this.object = object;
		this.qualifier = qualifier;
		this.ontologyClass = ontologyClass;
		this.annotationExtensions = annotationExtensions;
		this.reference = reference;
		this.evidence = evidence;
		this.withOrFrom = withOrFrom;
		this.interactingTaxon = interactingTaxon;
		this.date = date;
		this.assignedBy = assignedBy;
		this.annotations = annotations;
	}

	@Override
	public IRI getObject() {
		return this.object;
	}

	@Override
	public IRI getQualifier() {
		return this.qualifier;
	}

	@Override
	public IRI getOntologyClass() {
		return this.ontologyClass;
	}

	@Override
	public Set<ConjunctiveExpression> getAnnotationExtensions() {
		return this.annotationExtensions;
	}


	@Override
	public String getReference() {
		return this.reference;
	}

	@Override
	public IRI getEvidence() {
		return this.evidence;
	}

	@Override
	public Optional<String> getWithOrFrom() {
		return this.withOrFrom;
	}

	@Override
	public Optional<IRI> getInteractingTaxonID() {
		return this.interactingTaxon;
	}

	@Override
	public String getDate() {
		return this.date;
	}

	@Override
	public String getAssignedBy() {
		return this.assignedBy;
	}

	@Override
	public Set<Pair<String, String>> getAnnotations() {
		return this.annotations;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) { return true; }
		else if (!(other instanceof DefaultGPADData)) { return false; }
		else {
			DefaultGPADData otherData = (DefaultGPADData)other;
			return this.getObject().equals(otherData.getObject())
					&& this.getQualifier().equals(otherData.getQualifier())
					&& this.getOntologyClass().equals(otherData.getOntologyClass())
					&& this.getAnnotationExtensions().equals(otherData.getAnnotationExtensions())
					&& this.getReference().equals(otherData.getReference())
					&& this.getEvidence().equals(otherData.getEvidence())
					&& this.getWithOrFrom().equals(otherData.getWithOrFrom())
					&& this.getInteractingTaxonID().equals(otherData.getInteractingTaxonID())
					&& this.getDate().equals(otherData.getDate())
					&& this.getAssignedBy().equals(otherData.getAssignedBy())
					&& this.getAnnotations().equals(otherData.getAnnotations());
		}
	}

	@Override
	public int hashCode() {
		int result = 17;
		result = 37 * result + this.getObject().hashCode();
		result = 37 * result + this.getQualifier().hashCode();
		result = 37 * result + this.getOntologyClass().hashCode();
		result = 37 * result + this.getAnnotationExtensions().hashCode();
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
