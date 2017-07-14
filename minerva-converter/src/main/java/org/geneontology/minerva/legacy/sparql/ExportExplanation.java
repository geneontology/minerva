package org.geneontology.minerva.legacy.sparql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.log4j.Logger;
import org.geneontology.minerva.json.explanations.ExplanationRule;
import org.geneontology.minerva.json.explanations.ExplanationTerm;
import org.geneontology.minerva.json.explanations.ExplanationTriple;
import org.geneontology.minerva.json.explanations.ModelExplanation;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.geneontology.rules.engine.Explanation;
import org.geneontology.rules.engine.Node;
import org.geneontology.rules.engine.Rule;
import org.geneontology.rules.engine.Triple;
import org.geneontology.rules.engine.TriplePattern;
import org.geneontology.rules.engine.URI;
import org.geneontology.rules.engine.Variable;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;
import org.semanticweb.owlapi.model.IRI;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import scala.collection.JavaConverters;

public class ExportExplanation {

	private static final Logger LOG = Logger.getLogger(ExportExplanation.class);
	private static String mainQuery;
	static {
		try {
			mainQuery = IOUtils.toString(ExportExplanation.class.getResourceAsStream("ExplanationTriples.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.error("Could not load SPARQL query from jar", e);
		}
	}

	public static String exportExplanation(WorkingMemory wm, ExternalLookupService lookup) {
		Model model = ModelFactory.createDefaultModel();
		model.add(toJava(wm.facts()).stream().map(t -> model.asStatement(Bridge.jenaFromTriple(t))).collect(Collectors.toList()));
		QueryExecution qe = QueryExecutionFactory.create(mainQuery, model);
		ResultSet results = qe.execSelect();
		Set<Triple> triples = new HashSet<>();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			triples.add(new Triple(new URI(qs.getResource("s").getURI()), new URI(qs.getResource("p").getURI()), new URI(qs.getResource("o").getURI())));
		}
		qe.close();
		Set<Triple> asserted = triples.stream().filter(t -> wm.asserted().contains(t)).collect(Collectors.toSet());
		Set<Triple> inferred = triples.stream().filter(t -> !wm.asserted().contains(t)).collect(Collectors.toSet());
		Map<Triple, Set<Explanation>> allExplanations = inferred.stream().collect(Collectors.toMap(Function.identity(), s -> toJava(wm.explain(s))));
		Set<Rule> allRules = allExplanations.values().stream().flatMap(es -> es.stream().flatMap(e -> toJava(e.rules()).stream())).collect(Collectors.toSet());
		Stream<URI> subjects = triples.stream().map(t -> (URI)(t.s()));
		Stream<URI> predicates = triples.stream().map(t -> (URI)(t.p()));
		Stream<URI> objects = triples.stream().map(t -> (URI)(t.o()));
		Set<URI> allTerms = new HashSet<>();
		for (Rule rule : allRules) {
			for (TriplePattern tp : toJavaList(rule.body())) {
				if (tp.s() instanceof URI) allTerms.add((URI)tp.s());
				if (tp.p() instanceof URI) allTerms.add((URI)tp.p());
				if (tp.o() instanceof URI) allTerms.add((URI)tp.o());
			}
			for (TriplePattern tp : toJavaList(rule.head())) {
				if (tp.s() instanceof URI) allTerms.add((URI)tp.s());
				if (tp.p() instanceof URI) allTerms.add((URI)tp.p());
				if (tp.o() instanceof URI) allTerms.add((URI)tp.o());
			}
		}
		allTerms.addAll(subjects.collect(Collectors.toSet()));
		allTerms.addAll(predicates.collect(Collectors.toSet()));
		allTerms.addAll(objects.collect(Collectors.toSet()));
		Map<URI, String> labels = findLabels(allTerms, lookup, asserted);		
		int currentBlankNode = 0;
		Map<Triple, ExplanationTriple> assertedForJSON = new HashMap<>();
		for (Triple t : asserted) {
			ExplanationTriple et = new ExplanationTriple();
			et.id = "_:" + currentBlankNode++;
			et.subject = ((URI)(t.s())).uri();
			et.predicate = ((URI)(t.p())).uri();
			et.object = ((URI)(t.o())).uri();
			assertedForJSON.put(t, et);
		}
		Map<Rule, ExplanationRule> rulesForJSON = new HashMap<>();
		for (Rule r : allRules) {
			ExplanationRule er = new ExplanationRule();
			er.id = "_:" + currentBlankNode++;
			List<ExplanationTriple> body = new ArrayList<>();
			List<ExplanationTriple> head = new ArrayList<>();
			for (TriplePattern t : toJavaList(r.body())) {
				ExplanationTriple et = new ExplanationTriple();
				et.subject = patternNodeToString(t.s());
				et.predicate = patternNodeToString(t.p());
				et.object = patternNodeToString(t.o());
				body.add(et);
			}
			for (TriplePattern t : toJavaList(r.head())) {
				ExplanationTriple et = new ExplanationTriple();
				et.subject = patternNodeToString(t.s());
				et.predicate = patternNodeToString(t.p());
				et.object = patternNodeToString(t.o());
				head.add(et);
			}
			er.body = body.toArray(new ExplanationTriple[] {});
			er.head = head.toArray(new ExplanationTriple[] {});
			rulesForJSON.put(r, er);
		}
		Map<Triple, ExplanationTriple> inferredForJSON = new HashMap<>();
		for (Triple t : inferred) {
			ExplanationTriple et = new ExplanationTriple();
			et.subject = ((URI)(t.s())).uri();
			et.predicate = ((URI)(t.p())).uri();
			et.object = ((URI)(t.o())).uri();
			Explanation explanation = allExplanations.get(t).iterator().next();
			org.geneontology.minerva.json.explanations.Explanation ex = new org.geneontology.minerva.json.explanations.Explanation();
			ex.triples = toJava(explanation.facts()).stream().map(f -> assertedForJSON.get(f).id).toArray(String[]::new);
			ex.rules = toJava(explanation.rules()).stream().map(r -> rulesForJSON.get(r).id).toArray(String[]::new);
			et.explanation = ex;
			inferredForJSON.put(t, et);
		}
		ModelExplanation me = new ModelExplanation();
		me.terms = labels.keySet().stream().map(uri -> {
			ExplanationTerm et = new ExplanationTerm();
			et.id = uri.uri();
			et.label = labels.get(uri);
			return et;
		}).toArray(ExplanationTerm[]::new);
		me.assertions = assertedForJSON.values().toArray(new ExplanationTriple[] {});
		me.rules = rulesForJSON.values().toArray(new ExplanationRule[] {});
		me.inferences = inferredForJSON.values().toArray(new ExplanationTriple[] {});
		GsonBuilder builder = new GsonBuilder();
		builder = builder.setPrettyPrinting();
		Gson gson = builder.create();
		String json = gson.toJson(me);
		return json;
	}

	private static String patternNodeToString(Node node) {
		if (node instanceof URI) {
			return ((URI)node).uri();
		} else {
			return ((Variable)node).name();
		}
	}

	private static Map<URI, String> findLabels(Set<URI> uris, ExternalLookupService lookup, Set<Triple> assertions) {
		URI rdfType = new URI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
		Map<URI, String> labels = new HashMap<>();
		for (URI uri : uris) {
			List<LookupEntry> lookups = lookup.lookup(IRI.create(uri.uri()));
			if (null == lookups || lookups.isEmpty()) {
				Optional<URI> type = assertions.stream().filter(t -> t.s().equals(uri) && t.p().equals(rdfType)).map(t -> (URI)(t.o())).findAny();
				if (type.isPresent()) {
					List<LookupEntry> typeLookups = lookup.lookup(IRI.create(type.get().uri()));
					if (null == typeLookups ||  typeLookups.isEmpty()) {
						labels.put(uri, uri.uri());
					} else {
						labels.put(uri, typeLookups.get(0).label + "/" + uri.uri().substring(uri.uri().lastIndexOf("/")));
					}
				} else {
					labels.put(uri, uri.uri());
				}
			} else {
				labels.put(uri, lookups.get(0).label);
			}
		}
		return labels;
	}

	private static <T> Set<T> toJava(scala.collection.Set<T> scalaSet) {
		return JavaConverters.setAsJavaSetConverter(scalaSet).asJava();
	}

	private static <T> List<T> toJavaList(scala.collection.Seq<T> scalaList) {
		return JavaConverters.seqAsJavaListConverter(scalaList).asJava();
	}

}
