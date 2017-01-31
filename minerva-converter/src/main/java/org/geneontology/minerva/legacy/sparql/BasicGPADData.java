package org.geneontology.minerva.legacy.sparql;

import java.util.Set;

import org.semanticweb.owlapi.model.IRI;

public interface BasicGPADData {

	public IRI getObject();

	public IRI getQualifier();

	public IRI getOntologyClass();

	public Set<ConjunctiveExpression> getAnnotationExtensions();

	public interface ConjunctiveExpression {

		public IRI getRelation();

		public IRI getFiller();

	}

}
