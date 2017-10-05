package org.geneontology.minerva.legacy.sparql;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.system.JenaSystem;
import org.geneontology.jena.OWLtoRules;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.rules.engine.RuleEngine;
import org.geneontology.rules.engine.Triple;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;

import scala.collection.JavaConverters;

public class GPADSPARQLTest {

	private static RuleEngine arachne;
	private static GPADSPARQLExport exporter;

	@BeforeClass
	public static void setupRules() throws OWLOntologyCreationException {
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ont = manager.loadOntologyFromOntologyDocument(GPADSPARQLTest.class.getResourceAsStream("/ro-merged-2017-10-02.ofn"));
		Set<Rule> rules = new HashSet<>();
		rules.addAll(JavaConverters.setAsJavaSetConverter(OWLtoRules.translate(ont, Imports.INCLUDED, true, true, true, true)).asJava());
		rules.addAll(JavaConverters.setAsJavaSetConverter(OWLtoRules.indirectRules(ont)).asJava());
		arachne = new RuleEngine(Bridge.rulesFromJena(JavaConverters.asScalaSetConverter(rules).asScala()), true);
	}

	@BeforeClass
	public static void setupExporter() {
		JenaSystem.init();
		exporter = new GPADSPARQLExport(DefaultCurieHandler.getDefaultHandler(), new HashMap<IRI, String>());
	}

	@Test
	public void testGPADOutput() throws Exception {
		Model model = ModelFactory.createDefaultModel();
		model.read(this.getClass().getResourceAsStream("/581e072c00000473.ttl"), "", "ttl");
		Set<Triple> triples = model.listStatements().toList().stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet());
		WorkingMemory mem = arachne.processTriples(JavaConverters.asScalaSetConverter(triples).asScala());
		String gpad = exporter.exportGPAD(mem);
		int lines = gpad.split("\n", -1).length;
		//TODO test contents of annotations; dumb test for now
		Assert.assertTrue("Should produce annotations", lines > 2);
	}

}
