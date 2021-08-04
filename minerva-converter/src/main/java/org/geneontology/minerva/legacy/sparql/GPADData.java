package org.geneontology.minerva.legacy.sparql;

import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.tuple.Pair;
import org.semanticweb.owlapi.model.IRI;

/**
 * Standard data needed to render a GPAD file at IRI level.
 * Adding labels or curie transformations can build from this.
 * This is not meant to be a fully general representation of GPAD; 
 * just the information expected to be provided for a GPAD annotation
 * extraction from a LEGO model.
 */
public interface GPADData {

	@Nonnull
	public IRI getObject();

	@Nonnull
	public GPADOperatorStatus getOperator();
	
	@Nonnull
	public IRI getQualifier();

	@Nonnull
	public IRI getOntologyClass();

	@Nonnull
	public Set<ConjunctiveExpression> getAnnotationExtensions();

	@Nonnull
	public String getReference();
	
	@Nonnull
	public IRI getEvidence();

	@Nonnull
	public Optional<String> getWithOrFrom();

	@Nonnull
	public Optional<IRI> getInteractingTaxonID();
	
	@Nonnull
	public String getModificationDate();

	@Nonnull
	public String getAssignedBy();
	
	@Nonnull
	public Set<Pair<String, String>> getAnnotations();
	
	public static interface ConjunctiveExpression {

		public IRI getRelation();

		public IRI getFiller();

	}
}