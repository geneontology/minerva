package org.geneontology.minerva.legacy.sparql;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.query.QuerySolution;
import org.semanticweb.owlapi.model.IRI;

@Deprecated
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
	
	//FIXME fix query to format annotations this way
	@Override
	public Set<Pair<String, String>> getAnnotations() {
		Set<Pair<String, String>> annotations = new HashSet<>();
		if (result.getLiteral("contributors") != null) {
			for (String extension : result.getLiteral("contributors").getLexicalForm().split("\\|")) {
				String[] parts = extension.split("@@");
				if (parts.length == 2) {
					final String rel = parts[0];
					final String value = parts[1];
					annotations.add(Pair.of(rel, value));
				}
			}
		}
		return Collections.unmodifiableSet(annotations);
	}

}
