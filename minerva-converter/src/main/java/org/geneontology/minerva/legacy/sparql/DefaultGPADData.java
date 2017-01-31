package org.geneontology.minerva.legacy.sparql;

import java.util.Optional;

import org.apache.jena.query.QuerySolution;
import org.semanticweb.owlapi.model.IRI;

public class DefaultGPADData extends DefaultBasicGPADData implements GPADData {

	public DefaultGPADData(QuerySolution qs) {
		super(qs);
	}

	@Override
	public String getReference() {
		return result.getLiteral("source").getLexicalForm();
	}

	@Override
	public IRI getEvidence() {
		return IRI.create(result.getResource("evidence_type").getURI());
	}

	@Override
	public Optional<String> getWithOrFrom() {
		return Optional.ofNullable(result.getLiteral("with")).map(l -> l.getLexicalForm());
	}

	@Override
	public Optional<IRI> getInteractingTaxonID() {
		return Optional.empty();
	}

	@Override
	public String getDate() {
		return result.getLiteral("date").getLexicalForm();
	}

	@Override
	public String getAssignedBy() {
		return "GO_Noctua";
	}

	@Override
	public String getContributor() {
		return result.getLiteral("contributor").getLexicalForm();
	}

}
