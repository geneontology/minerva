package org.geneontology.minerva.legacy.sparql;

import javax.annotation.Nonnull;

import org.apache.jena.graph.Triple;
import org.semanticweb.owlapi.model.IRI;

public class AnnotationExtension {
	
	private final Triple triple;
	private final IRI valueType;
	
	public AnnotationExtension(Triple triple, IRI valueType) {
		this.triple = triple;
		this.valueType = valueType;
	}

	@Nonnull
	public Triple getTriple() {
		return triple;
	}

	@Nonnull
	public IRI getValueType() {
		return valueType;
	}
	
	@Override
	public String toString() {
		return this.getTriple() + " " + this.getValueType();
	}

}
