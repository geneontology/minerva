package org.geneontology.minerva.legacy.sparql;

import javax.annotation.Nonnull;

import org.apache.jena.rdf.model.Statement;
import org.semanticweb.owlapi.model.IRI;

public class AnnotationExtension {
	
	private final Statement statement;
	private final IRI valueType;
	
	public AnnotationExtension(Statement statement, IRI valueType) {
		this.statement = statement;
		this.valueType = valueType;
	}

	@Nonnull
	public Statement getStatement() {
		return statement;
	}

	@Nonnull
	public IRI getValueType() {
		return valueType;
	}
	

}
