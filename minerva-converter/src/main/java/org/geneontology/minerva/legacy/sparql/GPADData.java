package org.geneontology.minerva.legacy.sparql;

import java.util.Optional;
import java.util.Set;

import org.semanticweb.owlapi.model.IRI;

/**
 * Standard data needed to render a GPAD file at IRI level.
 * Adding labels or curie transformations can build from this.
 * This is not meant to be a fully general represenation of GPAD; 
 * just the information expected to be provided for a GPAD annotation
 * extraction from a LEGO model.
 */
public interface GPADData {

	public IRI getObject();

	public IRI getQualifier();

	public IRI getOntologyClass();

	public String getReference();
	
	public IRI getEvidence();

	public Optional<String> getWithOrFrom();

	public Optional<IRI> getInteractingTaxonID();

	public String getDate();

	public String getAssignedBy();

	public Set<ConjunctiveExpression> getAnnotationExtensions();

	public interface ConjunctiveExpression {

		public IRI getRelation();

		public IRI getFiller();

	}
	
	// Extra annotation; perhaps model annotations should be generic map
	public String getContributor();

}
