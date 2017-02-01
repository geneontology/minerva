package org.geneontology.minerva.legacy.sparql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.log4j.Logger;
import org.geneontology.jena.Explanation;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.semanticweb.owlapi.model.IRI;

import scala.collection.JavaConverters;

public class GPADSPARQLExport {

	private static final Logger LOG = Logger.getLogger(GPADSPARQLExport.class);
	private static String mainQuery;
	static {
		try {
			mainQuery = IOUtils.toString(GPADSPARQLExport.class.getResourceAsStream("gpad-basic.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.error("Could not load SPARQL query from jar", e);
		}
	}
	private static String evidenceQuery;
	static {
		try {
			evidenceQuery = IOUtils.toString(GPADSPARQLExport.class.getResourceAsStream("gpad-relation-evidence.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.error("Could not load SPARQL query from jar", e);
		}
	}

	private final CurieHandler curieHandler;
	private final Map<IRI, String> relationShorthandIndex;
	private final ExternalLookupService lookupService;

	public GPADSPARQLExport(CurieHandler handler, ExternalLookupService lookup, Map<IRI, String> shorthandIndex) {
		this.curieHandler = handler;
		this.relationShorthandIndex = shorthandIndex;
		this.lookupService = lookup;
	}

	public String exportGPAD(InfModel model) {
		QueryExecution qe = QueryExecutionFactory.create(mainQuery, model);
		Set<GPADData> annotations = new HashSet<>();
		String modelID = model.listResourcesWithProperty(RDF.type, OWL.Ontology).mapWith(r -> curieHandler.getCuri(IRI.create(r.getURI()))).next();
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			// When using GROUP_BY Jena may return a single empty result instead of none
			if (qs.get("pr_type") != null) {
				BasicGPADData basicData = new DefaultBasicGPADData(qs);
				Resource gp = qs.getResource("pr");
				Property rel = ResourceFactory.createProperty(qs.getResource("rel").getURI());
				Resource target = qs.getResource("target");
				Set<Explanation> explanations = toJava(Explanation.explain(ResourceFactory.createStatement(gp, rel, target), model));
				for (Explanation explanation : explanations) {
					Set<Statement> requiredFacts = toJava(explanation.facts());
					Map<Statement, Set<GPADEvidence>> evidences = requiredFacts.stream()
							.collect(Collectors.toMap(Function.identity(), f -> evidenceForFact(f, model, modelID)));
					// Every statement in the explanation must have at least one evidence
					if (evidences.values().stream().allMatch(es -> !es.isEmpty())) {
						// The evidence used for the annotation must be on an edge to or from the target node
						Stream<GPADEvidence> annotationEvidences = requiredFacts.stream()
								.filter(f -> (f.getSubject().equals(target) || f.getObject().equals(target)))
								.flatMap(f -> evidences.getOrDefault(f, Collections.emptySet()).stream());
						annotationEvidences.forEach(e -> annotations.add(new EvidencedGPADData(basicData, e)));
					}
				}
			}
		}
		qe.close();
		return new GPADRenderer(curieHandler, lookupService, relationShorthandIndex).renderAll(annotations);
	}

	private Set<GPADEvidence> evidenceForFact(Statement fact, InfModel model, String modelID) {
		Set<GPADEvidence> evidences = new HashSet<>();
		ParameterizedSparqlString filledEvidenceQuery = new ParameterizedSparqlString();
		filledEvidenceQuery.setCommandText(evidenceQuery);
		filledEvidenceQuery.setIri("subject", fact.getSubject().getURI());
		filledEvidenceQuery.setIri("predicate", fact.getPredicate().getURI());
		filledEvidenceQuery.setIri("object", fact.getObject().asResource().getURI());
		QueryExecution evidenceExecution = QueryExecutionFactory.create(filledEvidenceQuery.asQuery(), model);
		ResultSet evidenceResults = evidenceExecution.execSelect();
		while (evidenceResults.hasNext()) {
			QuerySolution eqs = evidenceResults.next();
			if (eqs.get("evidence_type") != null) {
				IRI evidenceType = IRI.create(eqs.getResource("evidence_type").getURI());
				Optional<String> with = Optional.ofNullable(eqs.getLiteral("with")).map(l -> l.getLexicalForm());
				Set<Pair<String, String>> annotationAnnotations = new HashSet<>();
				annotationAnnotations.add(Pair.of("lego-model-id", modelID));
				annotationAnnotations.addAll(getContributors(eqs).stream().map(c -> Pair.of("contributor", c)).collect(Collectors.toSet()));
				String date = eqs.getLiteral("date").getLexicalForm();
				String reference = eqs.getLiteral("source").getLexicalForm();
				evidences.add(new GPADEvidence(evidenceType, reference, with, date, "GO_Noctua", annotationAnnotations, Optional.empty()));
			}
		}
		evidenceExecution.close();
		return evidences;
	}

	private Set<String> getContributors(QuerySolution result) {
		Set<String> contributors = new HashSet<>();
		if (result.getLiteral("contributors") != null) {
			for (String contributor : result.getLiteral("contributors").getLexicalForm().split("\\|")) {
				contributors.add(contributor);
			}
		}
		return Collections.unmodifiableSet(contributors);
	}

	private static <T> Set<T> toJava(scala.collection.Set<T> scalaSet) {
		return JavaConverters.setAsJavaSetConverter(scalaSet).asJava();
	}

}
