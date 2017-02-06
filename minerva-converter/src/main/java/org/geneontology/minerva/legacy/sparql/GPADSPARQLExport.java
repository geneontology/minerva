package org.geneontology.minerva.legacy.sparql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingFactory;
import org.apache.jena.sparql.engine.binding.BindingMap;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.log4j.Logger;
import org.geneontology.jena.Explanation;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.legacy.sparql.GPADData.ConjunctiveExpression;
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
	private static String multipleEvidenceQuery;
	static {
		try {
			multipleEvidenceQuery = IOUtils.toString(GPADSPARQLExport.class.getResourceAsStream("gpad-relation-evidence-multiple.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.error("Could not load SPARQL query from jar", e);
		}
	}
	private static String extensionsQuery;
	static {
		try {
			extensionsQuery = IOUtils.toString(GPADSPARQLExport.class.getResourceAsStream("gpad-extensions.rq"), StandardCharsets.UTF_8);
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

	/*
	 * This is a bit convoluted in order to minimize redundant queries, for performance reasons.
	 */
	public String exportGPAD(InfModel model) {
		QueryExecution qe = QueryExecutionFactory.create(mainQuery, model);
		Set<GPADData> annotations = new HashSet<>();
		String modelID = model.listResourcesWithProperty(RDF.type, OWL.Ontology).mapWith(r -> curieHandler.getCuri(IRI.create(r.getURI()))).next();
		ResultSet results = qe.execSelect();
		Set<BasicGPADData> basicAnnotations = new HashSet<>();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			basicAnnotations.add(new BasicGPADData(qs.getResource("pr"), IRI.create(qs.getResource("pr_type").getURI()), IRI.create(qs.getResource("rel").getURI()), qs.getResource("target"), IRI.create(qs.getResource("target_type").getURI())));
		}
		qe.close();
		Set<AnnotationExtension> possibleExtensions = possibleExtensions(basicAnnotations, model);
		Set<Statement> statementsToExplain = new HashSet<>();
		basicAnnotations.forEach(ba -> statementsToExplain.add(ResourceFactory.createStatement(ba.getObjectNode(), ResourceFactory.createProperty(ba.getQualifier().toString()), ba.getOntologyClassNode())));
		possibleExtensions.forEach(ae -> statementsToExplain.add(ae.getStatement()));
		Map<Statement, Set<Explanation>> allExplanations = statementsToExplain.stream().collect(Collectors.toMap(Function.identity(), s -> toJava(Explanation.explain(s, model))));
		Map<Statement, Set<GPADEvidence>> allEvidences = evidencesForFacts(allExplanations.values().stream().flatMap(es -> es.stream()).flatMap(e -> toJava(e.facts()).stream()).collect(Collectors.toSet()), model, modelID);
		for (BasicGPADData annotation : basicAnnotations) {
			for (Explanation explanation : allExplanations.get(ResourceFactory.createStatement(annotation.getObjectNode(), ResourceFactory.createProperty(annotation.getQualifier().toString()), annotation.getOntologyClassNode()))) {
				Set<Statement> requiredFacts = toJava(explanation.facts());
				// Every statement in the explanation must have at least one evidence
				if (requiredFacts.stream().allMatch(f -> !(allEvidences.get(f).isEmpty()))) {
					// The evidence used for the annotation must be on an edge to or from the target node
					Stream<GPADEvidence> annotationEvidences = requiredFacts.stream()
							.filter(f -> (f.getSubject().equals(annotation.getOntologyClassNode()) || f.getObject().equals(annotation.getOntologyClassNode())))
							.flatMap(f -> allEvidences.getOrDefault(f, Collections.emptySet()).stream());
					annotationEvidences.forEach(currentEvidence -> {
						String reference = currentEvidence.getReference();
						Set<ConjunctiveExpression> goodExtensions = new HashSet<>();
						for (AnnotationExtension extension : possibleExtensions) {
							if (extension.getStatement().getSubject().equals(annotation.getOntologyClassNode()) &&
									!(extension.getStatement().getObject().equals(annotation.getObjectNode()))) {
								for (Explanation expl : allExplanations.get(extension.getStatement())) {
									boolean allFactsOfExplanationHaveRefMatchingAnnotation = toJava(expl.facts()).stream().map(fact -> allEvidences.getOrDefault(fact, Collections.emptySet())).allMatch(evidenceSet -> 
									evidenceSet.stream().anyMatch(ev -> ev.getReference().equals(reference)));
									if (allFactsOfExplanationHaveRefMatchingAnnotation) {
										goodExtensions.add(new DefaultConjunctiveExpression(IRI.create(extension.getStatement().getPredicate().getURI()), extension.getValueType()));
									}
								}
							}
						}
						annotations.add(new DefaultGPADData(annotation.getObject(), annotation.getQualifier(), annotation.getOntologyClass(), goodExtensions, 
								reference, currentEvidence.getEvidence(), currentEvidence.getWithOrFrom(), Optional.empty(), currentEvidence.getDate(), "GO_Noctua", currentEvidence.getAnnotations()));
					});
				}
			}
		}
		return new GPADRenderer(curieHandler, lookupService, relationShorthandIndex).renderAll(annotations);
	}

	private Map<Statement, Set<GPADEvidence>> evidencesForFacts(Set<Statement> facts, InfModel model, String modelID) {
		Query query = QueryFactory.create(multipleEvidenceQuery);
		Var subject = Var.alloc("subject");
		Var predicate = Var.alloc("predicate");
		Var object = Var.alloc("object");
		List<Var> variables = new ArrayList<>();
		variables.add(subject);
		variables.add(predicate);
		variables.add(object);
		Stream<Binding> bindings = facts.stream().map(f -> createBinding(Pair.of(subject, f.getSubject().asNode()), Pair.of(predicate, f.getPredicate().asNode()), Pair.of(object, f.getObject().asNode())));
		query.setValuesDataBlock(variables, bindings.collect(Collectors.toList()));
		QueryExecution evidenceExecution = QueryExecutionFactory.create(query, model);
		ResultSet evidenceResults = evidenceExecution.execSelect();
		Map<Statement, Set<GPADEvidence>> allEvidences = facts.stream().collect(Collectors.toMap(Function.identity(), f -> new HashSet<GPADEvidence>()));
		while (evidenceResults.hasNext()) {
			QuerySolution eqs = evidenceResults.next();
			if (eqs.get("evidence_type") != null) {
				Statement statement = ResourceFactory.createStatement(eqs.getResource("subject"), ResourceFactory.createProperty(eqs.getResource("predicate").getURI()), eqs.getResource("object"));
				IRI evidenceType = IRI.create(eqs.getResource("evidence_type").getURI());
				Optional<String> with = Optional.ofNullable(eqs.getLiteral("with")).map(l -> l.getLexicalForm());
				Set<Pair<String, String>> annotationAnnotations = new HashSet<>();
				annotationAnnotations.add(Pair.of("lego-model-id", modelID));
				annotationAnnotations.addAll(getContributors(eqs).stream().map(c -> Pair.of("contributor", c)).collect(Collectors.toSet()));
				String date = eqs.getLiteral("date").getLexicalForm();
				String reference = eqs.getLiteral("source").getLexicalForm();
				allEvidences.get(statement).add(new GPADEvidence(evidenceType, reference, with, date, "GO_Noctua", annotationAnnotations, Optional.empty()));
			}
		}
		evidenceExecution.close();
		return allEvidences;
	}

	@SafeVarargs
	private final Binding createBinding(Pair<Var, Node>... bindings) {
		BindingMap map = BindingFactory.create();
		for (Pair<Var, Node> binding : bindings) {
			map.add(binding.getLeft(), binding.getRight());
		}
		return map;
	}

	private Set<AnnotationExtension> possibleExtensions(Set<BasicGPADData> basicAnnotations, InfModel model) {
		Set<AnnotationExtension> possibleExtensions = new HashSet<>();
		Var targetVar = Var.alloc("target");
		List<Binding> bindings = basicAnnotations.stream().map(ba -> createBinding(Pair.of(targetVar, ba.getOntologyClassNode().asNode()))).collect(Collectors.toList());
		Query query = QueryFactory.create(extensionsQuery);
		query.setValuesDataBlock(Arrays.asList(targetVar), bindings);
		QueryExecution qe = QueryExecutionFactory.create(query, model);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution result =  results.next();
			Statement statement = ResourceFactory.createStatement(result.getResource("target"), ResourceFactory.createProperty(result.getResource("extension_rel").getURI()), result.getResource("extension"));
			IRI extensionType = IRI.create(result.getResource("extension_type").getURI());
			possibleExtensions.add(new AnnotationExtension(statement, extensionType));
		}
		qe.close();
		return possibleExtensions;
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

	private static class DefaultConjunctiveExpression implements ConjunctiveExpression {

		private final IRI relation;
		private final IRI filler;

		public DefaultConjunctiveExpression(IRI rel, IRI fill) {
			this.relation = rel;
			this.filler = fill;
		}

		@Override
		public IRI getRelation() {
			return relation;
		}

		@Override
		public IRI getFiller() {
			return filler;
		}

	}

}
