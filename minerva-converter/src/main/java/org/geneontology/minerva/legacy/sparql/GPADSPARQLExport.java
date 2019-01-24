package org.geneontology.minerva.legacy.sparql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingFactory;
import org.apache.jena.sparql.engine.binding.BindingMap;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.log4j.Logger;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.legacy.sparql.GPADData.ConjunctiveExpression;
import org.geneontology.rules.engine.Explanation;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException;

import scala.collection.JavaConverters;

/* 	 Note: the example GPAD files are available at this link: http://www.informatics.jax.org/downloads/reports/mgi.gpa.gz */
public class GPADSPARQLExport {
	private static final Logger LOG = Logger.getLogger(GPADSPARQLExport.class);
	private static final String ND = "http://purl.obolibrary.org/obo/ECO_0000307";
	private static final String MF = "http://purl.obolibrary.org/obo/GO_0003674";
	private static final String BP = "http://purl.obolibrary.org/obo/GO_0008150";
	private static final String CC = "http://purl.obolibrary.org/obo/GO_0005575";
	private static final Set<String> rootTerms = new HashSet<>(Arrays.asList(MF, BP, CC));
	private static final String inconsistentQuery = 
			"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" + 
					"PREFIX owl: <http://www.w3.org/2002/07/owl#>" +
					"ASK WHERE { ?s rdf:type owl:Nothing . } ";

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
	private static String modelAnnotationsQuery;
	static {
		try {
			modelAnnotationsQuery = IOUtils.toString(GPADSPARQLExport.class.getResourceAsStream("gpad-model-level-annotations.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.error("Could not load SPARQL query from jar", e);
		}
	}
	private final CurieHandler curieHandler;
	private final Map<IRI, String> relationShorthandIndex;
	private final Map<IRI, String> tboxShorthandIndex;
	private final Set<IRI> doNotAnnotateSubset;

	public GPADSPARQLExport(CurieHandler handler, Map<IRI, String> shorthandIndex, Map<IRI, String> tboxShorthandIndex, Set<IRI> doNotAnnotateSubset) {
		this.curieHandler = handler;
		this.relationShorthandIndex = shorthandIndex;
		this.tboxShorthandIndex = tboxShorthandIndex;
		this.doNotAnnotateSubset = doNotAnnotateSubset;
	}

	/* This is a bit convoluted in order to minimize redundant queries, for performance reasons. */
	public String exportGPAD(WorkingMemory wm) throws InconsistentOntologyException {
		Model model = ModelFactory.createDefaultModel();
		model.add(JavaConverters.setAsJavaSetConverter(wm.facts()).asJava().stream().map(t -> model.asStatement(Bridge.jenaFromTriple(t))).collect(Collectors.toList()));
		if (!isConsistent(model)) throw new InconsistentOntologyException();
		Map<String, String> modelLevelAnnotations = getModelAnnotations(model);
		/* The first step of constructing GPAD records is to construct candidate/basic GPAD records by running gpad-basic.rq. */
		QueryExecution qe = QueryExecutionFactory.create(mainQuery, model);
		Set<GPADData> annotations = new HashSet<>();
		String modelID = model.listResourcesWithProperty(RDF.type, OWL.Ontology).mapWith(r -> curieHandler.getCuri(IRI.create(r.getURI()))).next();
		ResultSet results = qe.execSelect();
		Set<BasicGPADData> basicAnnotations = new HashSet<>();		
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			BasicGPADData basicGPADData = new BasicGPADData(qs.getResource("pr").asNode(), IRI.create(qs.getResource("pr_type").getURI()), IRI.create(qs.getResource("rel").getURI()), qs.getResource("target").asNode(), IRI.create(qs.getResource("target_type").getURI()));			

			/* See whether the query answer contains not-null blank nodes, which are only set if the matching subgraph 
			 * contains the property ComplementOf.  If we see such cases, we set the operator field as NOT so that NOT value 
			 * can be printed in GPAD. */ 
			if (qs.getResource("blank_comp") != null) basicGPADData.setOperator(GPADOperatorStatus.NOT);
			basicAnnotations.add(basicGPADData);
		}
		qe.close();

		/* The bindings of ?pr_type, ?rel, ?target_type are candidate mappings or values for the final GPAD records 
		 * (i.e. not every mapping is used for building the final records of GPAD file; many of them are filtered out later).
		 * The mappings are 
		 * 		?pr_type: DB Object ID (2nd in GPAD), ?rel: Qualifier(3rd), ?target_type: GO ID(4th) 
		 * The rest of fields in GPAD are then constructed by joining the candidate mappings with mappings describing evidences and so on.
		 * If the output of this exporter (i.e. GPAD files) does not contain the values you expect, 
		 * dump the above "QuerySolution qs" variable and see whether they are included in the dump. */
		Set<AnnotationExtension> possibleExtensions = possibleExtensions(basicAnnotations, model);
		Set<Triple> statementsToExplain = new HashSet<>();
		basicAnnotations.forEach(ba -> statementsToExplain.add(Triple.create(ba.getObjectNode(), NodeFactory.createURI(ba.getQualifier().toString()), ba.getOntologyClassNode())));
		possibleExtensions.forEach(ae -> statementsToExplain.add(ae.getTriple()));
		Map<Triple, Set<Explanation>> allExplanations = statementsToExplain.stream().collect(Collectors.toMap(Function.identity(), s -> toJava(wm.explain(Bridge.tripleFromJena(s)))));

		Map<Triple, Set<GPADEvidence>> allEvidences = evidencesForFacts(allExplanations.values().stream().flatMap(es -> es.stream()).flatMap(e -> toJava(e.facts()).stream().map(t -> Bridge.jenaFromTriple(t))).collect(Collectors.toSet()), model, modelID, modelLevelAnnotations);
		for (BasicGPADData annotation : basicAnnotations) {
			for (Explanation explanation : allExplanations.get(Triple.create(annotation.getObjectNode(), NodeFactory.createURI(annotation.getQualifier().toString()), annotation.getOntologyClassNode()))) {
				Set<Triple> requiredFacts = toJava(explanation.facts()).stream().map(t -> Bridge.jenaFromTriple(t)).collect(Collectors.toSet());
				// Every statement in the explanation must have at least one evidence, unless the statement is a class assertion
				if (requiredFacts.stream().filter(t -> !t.getPredicate().getURI().equals(RDF.type.getURI())).allMatch(f -> !(allEvidences.get(f).isEmpty()))) {
					// The evidence used for the annotation must be on an edge to or from the target node
					Stream<GPADEvidence> annotationEvidences = requiredFacts.stream()
							.filter(f -> (f.getSubject().equals(annotation.getOntologyClassNode()) || f.getObject().equals(annotation.getOntologyClassNode())))
							.flatMap(f -> allEvidences.getOrDefault(f, Collections.emptySet()).stream());
					annotationEvidences.forEach(currentEvidence -> {
						String reference = currentEvidence.getReference();
						Set<ConjunctiveExpression> goodExtensions = new HashSet<>();
						for (AnnotationExtension extension : possibleExtensions) {
							if (extension.getTriple().getSubject().equals(annotation.getOntologyClassNode()) && !(extension.getTriple().getObject().equals(annotation.getObjectNode()))) {
								for (Explanation expl : allExplanations.get(extension.getTriple())) {
									boolean allFactsOfExplanationHaveRefMatchingAnnotation = toJava(expl.facts()).stream().map(fact -> allEvidences.getOrDefault(Bridge.jenaFromTriple(fact), Collections.emptySet())).allMatch(evidenceSet -> 
									evidenceSet.stream().anyMatch(ev -> ev.getReference().equals(reference)));
									if (allFactsOfExplanationHaveRefMatchingAnnotation) {
										goodExtensions.add(new DefaultConjunctiveExpression(IRI.create(extension.getTriple().getPredicate().getURI()), extension.getValueType()));
									}
								}
							}
						}
						final boolean rootViolation;
						if (rootTerms.contains(annotation.getOntologyClass().toString())) {
							rootViolation = !ND.equals(currentEvidence.getEvidence().toString());
						} else { rootViolation = false; }
						final boolean doNotAnnotateViolation;
						if (doNotAnnotateSubset.contains(annotation.getOntologyClass())) {
							doNotAnnotateViolation = true;
						} else { doNotAnnotateViolation = false; }

						if (!rootViolation && !doNotAnnotateViolation) {
							DefaultGPADData defaultGPADData = new DefaultGPADData(annotation.getObject(), annotation.getQualifier(), annotation.getOntologyClass(), goodExtensions, 
									reference, currentEvidence.getEvidence(), currentEvidence.getWithOrFrom(), Optional.empty(), currentEvidence.getDate(), currentEvidence.getAssignedBy(), currentEvidence.getAnnotations());
							defaultGPADData.setOperator(annotation.getOperator());
							annotations.add(defaultGPADData);
						}
					});
				}
			}
		}
		return new GPADRenderer(curieHandler, relationShorthandIndex).renderAll(annotations);
	}

	private Map<String, String> getModelAnnotations(Model model) {
		QueryExecution qe = QueryExecutionFactory.create(modelAnnotationsQuery, model);
		ResultSet result = qe.execSelect();
		Map<String, String> modelAnnotations = new HashMap<>();
		while (result.hasNext()) {
			QuerySolution qs = result.next();
			if (qs.get("model_state") != null) {
				String modelState = qs.getLiteral("model_state").getLexicalForm();
				modelAnnotations.put("model-state", modelState);
			}
			if (qs.get("provided_by") != null) {
				String providedBy = qs.getLiteral("provided_by").getLexicalForm();
				modelAnnotations.put("assigned-by", providedBy);
			}
			break;
		}
		return modelAnnotations;
	}

	/**
	 * Given a set of triples extracted/generated from the result/answer of query gpad-basic.rq, we find matching evidence subgraphs. 
	 * In other words, if there are no matching evidence (i.e. no bindings for evidence_type), we discard (basic) GPAD instance.
	 * 
	 * The parameter "facts" consists of triples <?subject, ?predicate, ?object> constructed from a binding of ?pr, ?rel, ?target in gpad_basic.rq. 
	 * (The codes that constructing these triples are executed right before this method is called).
	 * 
	 * These triples are then decomposed into values used as the parameters/bindings for objects of the following patterns.
	 * 		?axiom owl:annotatedSource   ?subject (i.e. ?pr in gpad_basic.rq) 
	 * 		?axiom owl:annotatedProperty ?predicate (i.e., ?rel in gpad_basic.rq, which denotes qualifier in GPAD) 
	 * 		?axiom owl:annotatedTarget    ?object (i.e., ?target in gpad_basic.rq)
	 * 
	 * If we find the bindings of ?axioms and the values of these bindings have some rdf:type triples, we proceed. (If not, we discard).
	 * The bindings of the query gpad-relation-evidence-multiple.rq are then used for filling up fields in GPAD records/tuples.
	 */
	private Map<Triple, Set<GPADEvidence>> evidencesForFacts(Set<Triple> facts, Model model, String modelID, Map<String, String> modelLevelAnnotations) {
		Query query = QueryFactory.create(multipleEvidenceQuery);
		Var subject = Var.alloc("subject");
		Var predicate = Var.alloc("predicate");
		Var object = Var.alloc("object");
		List<Var> variables = new ArrayList<>();
		variables.add(subject);
		variables.add(predicate);
		variables.add(object);
		Stream<Binding> bindings = facts.stream().map(f -> createBinding(Pair.of(subject, f.getSubject()), Pair.of(predicate, f.getPredicate()), Pair.of(object, f.getObject())));
		query.setValuesDataBlock(variables, bindings.collect(Collectors.toList()));
		QueryExecution evidenceExecution = QueryExecutionFactory.create(query, model);
		ResultSet evidenceResults = evidenceExecution.execSelect();
		Map<Triple, Set<GPADEvidence>> allEvidences = facts.stream().collect(Collectors.toMap(Function.identity(), f -> new HashSet<GPADEvidence>()));
		while (evidenceResults.hasNext()) {
			QuerySolution eqs = evidenceResults.next();
			if (eqs.get("evidence_type") != null) {
				Triple statement = Triple.create(eqs.getResource("subject").asNode(), eqs.getResource("predicate").asNode(), eqs.getResource("object").asNode());
				IRI evidenceType = IRI.create(eqs.getResource("evidence_type").getURI());
				Optional<String> with = Optional.ofNullable(eqs.getLiteral("with")).map(l -> l.getLexicalForm());
				Set<Pair<String, String>> annotationAnnotations = new HashSet<>();
				annotationAnnotations.add(Pair.of("noctua-model-id", modelID));
				annotationAnnotations.addAll(getContributors(eqs).stream().map(c -> Pair.of("contributor", c)).collect(Collectors.toSet()));
				String date = eqs.getLiteral("date").getLexicalForm();
				String reference = eqs.getLiteral("source").getLexicalForm();
				final String usableAssignedBy;
				Optional<String> assignedByIRIOpt = getAnnotationAssignedBy(eqs);
				if (assignedByIRIOpt.isPresent()) {
					String usableAssignedByIRI = assignedByIRIOpt.get();
					usableAssignedBy = this.tboxShorthandIndex.getOrDefault(IRI.create(usableAssignedByIRI), usableAssignedByIRI);
				} else if (modelLevelAnnotations.containsKey("assigned-by")) {
					String usableAssignedByIRI = modelLevelAnnotations.get("assigned-by");
					usableAssignedBy = this.tboxShorthandIndex.getOrDefault(IRI.create(usableAssignedByIRI), usableAssignedByIRI);
				} else { usableAssignedBy = "GO_Noctua"; }
				if (modelLevelAnnotations.containsKey("model-state")) {
					annotationAnnotations.add(Pair.of("model-state", modelLevelAnnotations.get("model-state")));
				}
				allEvidences.get(statement).add(new GPADEvidence(evidenceType, reference, with, date, usableAssignedBy, annotationAnnotations, Optional.empty()));
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

	private Set<AnnotationExtension> possibleExtensions(Set<BasicGPADData> basicAnnotations, Model model) {
		Set<AnnotationExtension> possibleExtensions = new HashSet<>();
		Var targetVar = Var.alloc("target");
		List<Binding> bindings = basicAnnotations.stream().map(ba -> createBinding(Pair.of(targetVar, ba.getOntologyClassNode()))).collect(Collectors.toList());
		Query query = QueryFactory.create(extensionsQuery);
		query.setValuesDataBlock(Arrays.asList(targetVar), bindings);
		QueryExecution qe = QueryExecutionFactory.create(query, model);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution result = results.next();
			Triple statement = Triple.create(result.getResource("target").asNode(), result.getResource("extension_rel").asNode(), result.getResource("extension").asNode());
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

	private Optional<String> getAnnotationAssignedBy(QuerySolution result) {
		if (result.getLiteral("provided_bys") != null) {
			for (String group : result.getLiteral("provided_bys").getLexicalForm().split("\\|")) {
				return Optional.of(group);
			}
		}
		return Optional.empty();
	}

	private boolean isConsistent(Model model) {
		QueryExecution qe = QueryExecutionFactory.create(inconsistentQuery, model);
		boolean inconsistent = qe.execAsk();
		qe.close();
		return !inconsistent;
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

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((filler == null) ? 0 : filler.hashCode());
			result = prime * result + ((relation == null) ? 0 : relation.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DefaultConjunctiveExpression other = (DefaultConjunctiveExpression) obj;
			if (filler == null) {
				if (other.filler != null)
					return false;
			} else if (!filler.equals(other.filler))
				return false;
			if (relation == null) {
				if (other.relation != null)
					return false;
			} else if (!relation.equals(other.relation))
				return false;
			return true;
		}

	}

}