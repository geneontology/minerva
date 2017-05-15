package org.geneontology.minerva.legacy.sparql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.legacy.sparql.GPADData.ConjunctiveExpression;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.semanticweb.owlapi.model.IRI;

public class GPADRenderer {

	private final CurieHandler curieHandler;
	private final Map<IRI, String> relationShorthandIndex;
	//private final ExternalLookupService lookupService;

	public static final String HEADER = "!gpa-version: 1.2";

	public GPADRenderer(CurieHandler handler, ExternalLookupService lookup, Map<IRI, String> shorthandIndex) {
		this.curieHandler = handler;
		this.relationShorthandIndex = shorthandIndex;
		//this.lookupService = lookup;
	}

	public String renderAll(Collection<GPADData> data) {
		StringBuilder sb = new StringBuilder();
		sb.append(HEADER);
		sb.append("\n");
		for (GPADData annotation : data) {
			sb.append(render(annotation));
			sb.append("\n");
		}
		return sb.toString();
	}

	public String render(GPADData data) {
		try {
			List<String> columns = new ArrayList<>();
			columns.add(dbForObject(data.getObject()));
			columns.add(localIDForObject(data.getObject()));
			columns.add(symbolForRelation(data.getQualifier()));
			columns.add(curieHandler.getCuri(data.getOntologyClass()));
			columns.add(data.getReference());
			columns.add(curieHandler.getCuri(data.getEvidence()));
			columns.add(data.getWithOrFrom().orElse(""));
			columns.add(""); // not using interacting taxon in LEGO models
			columns.add(formatDate(data.getDate()));
			columns.add(data.getAssignedBy());
			columns.add(formatAnnotationExtensions(data.getAnnotationExtensions()));
			columns.add(data.getAnnotations().stream()
					.map(a -> a.getLeft() + "=" + a.getRight())
					.collect(Collectors.joining("|")));
			return String.join("\t", columns);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	private String localIDForObject(IRI iri) {
		String curie = curieHandler.getCuri(iri);
		if (curie.startsWith("http")) {
			return curie;
		} else {
			return curie.split(":", 2)[1]; //TODO temporary?
		}
	}

	private String dbForObject(IRI iri) {
		String curie = curieHandler.getCuri(iri);
		if (curie.startsWith("http")) {
			return "";
		} else {
			return curie.split(":", 2)[0]; //TODO temporary?
		}
	}

	private String symbolForRelation(IRI iri) {
		// Property labels don't seem to be in external lookup service?
//		Optional<String> labelOpt = Optional.ofNullable(lookupService.lookup(iri)).orElse(Collections.emptyList()).stream()
//				.filter(e -> e.label != null).findAny().map(e -> e.label.replaceAll(" ", "_"));
//		return labelOpt.orElse(curieHandler.getCuri(iri));

		if (relationShorthandIndex.containsKey(iri)) {
			return relationShorthandIndex.get(iri);
		} else {
			return curieHandler.getCuri(iri);
		}
	}

	/**
	 * Convert "2016-12-26" to "20161226"
	 */
	private String formatDate(String date) {
		return date.replaceAll("-", "");
	}

	private String formatAnnotationExtensions(Set<ConjunctiveExpression> extensions) {
		return extensions.stream()
				.sorted(extensionComparator)
				.map(ce -> this.renderConjunctiveExpression(ce))
				.collect(Collectors.joining(","));
	}

	private static Comparator<ConjunctiveExpression> extensionComparator = new Comparator<ConjunctiveExpression>() {
		@Override
		public int compare(ConjunctiveExpression a, ConjunctiveExpression b) {
			return (a.getRelation().toString() + a.getFiller().toString()).compareTo(b.getRelation().toString() + b.getFiller().toString());
		}
	};

	private String renderConjunctiveExpression(ConjunctiveExpression ce) {
		String relation = symbolForRelation(ce.getRelation());
		String filler = curieHandler.getCuri(ce.getFiller());
		return relation + "(" + filler + ")";
	}

}