package org.geneontology.minerva.legacy.sparql;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.apache.jena.query.QuerySolution;
import org.semanticweb.owlapi.model.IRI;

public class DefaultGPADData implements GPADData {

	private final QuerySolution result;

	public DefaultGPADData(QuerySolution qs) {
		result = qs;
	}

	@Override
	public IRI getObject() {
		return IRI.create(result.getResource("pr_type").getURI());
	}

	@Override
	public IRI getQualifier() {
		return IRI.create(result.getResource("rel").getURI());
	}

	@Override
	public IRI getOntologyClass() {
		return IRI.create(result.getResource("target_type").getURI());
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
	public Set<ConjunctiveExpression> getAnnotationExtensions() {
		Set<ConjunctiveExpression> extensions = new HashSet<>();
		if (result.getLiteral("extensions") != null) {
			for (String extension : result.getLiteral("extensions").getLexicalForm().split("\\|")) {
				String[] parts = extension.split("@@");
				if (parts.length == 2) {
					final String rel = parts[0];
					final String filler = parts[1];
					extensions.add(new DefaultConjunctiveExpression(rel, filler));
				}
			}
		}
		return Collections.unmodifiableSet(extensions);
	}

	@Override
	public String getContributor() {
		return result.getLiteral("contributor").getLexicalForm();
	}

	private static class DefaultConjunctiveExpression implements ConjunctiveExpression {

		private final String relation;
		private final String filler;

		public DefaultConjunctiveExpression(String rel, String fill) {
			this.relation = rel;
			this.filler = fill;
		}

		@Override
		public IRI getRelation() {
			return IRI.create(relation);
		}

		@Override
		public IRI getFiller() {
			return IRI.create(filler);
		}

	}

}
