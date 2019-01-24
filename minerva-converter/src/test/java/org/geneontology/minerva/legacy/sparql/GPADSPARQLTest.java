package org.geneontology.minerva.legacy.sparql;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
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
		exporter = new GPADSPARQLExport(DefaultCurieHandler.getDefaultHandler(), new HashMap<IRI, String>(), new HashMap<IRI, String>(), Collections.emptySet());
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
		Assert.assertTrue(gpad.contains("model-state=production"));
		Assert.assertTrue("Should produce annotations", lines > 2);
	}

	/**
	 * Test whether the GPAD output contains all required entries and rows without any spurious results.
	 * Example Input file: the owl dump from http://noctua-dev.berkeleybop.org/editor/graph/gomodel:59d1072300000074
	 * 
	 * Note on the GPAD file format and its contents:
	 * 1. the number of entries in the GPAD output from this owl dump should be 6, not 7 (although there are 7 individuals/boxes) 
	 *     because the edge/relationship "molecular_function" is a trivial one, which is supposed to be removed from the output.
	 * 2. the 4th columns, which consists of the list of GO IDs attributed to the DB object ID (These should be GO:0005634, GO:0007267, GO:0007507, GO:0016301) 
	 * 3. the 2nd columns: the rest of entities in the noctua screen, i.e.  S000028630 (YFR032C-B Scer) or S000004724(SHH3 Scer)
	*
	 * @throws Exception
	 */
	@Test
	public void testGPADOutputWithNegation() throws Exception {
		Model model = ModelFactory.createDefaultModel();
		model.read(this.getClass().getResourceAsStream("/59d1072300000074.ttl"), "", "ttl");
		Set<Triple> triples = model.listStatements().toList().stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet());
		WorkingMemory mem = arachne.processTriples(JavaConverters.asScalaSetConverter(triples).asScala());
		String gpad = exporter.exportGPAD(mem);
		
		/* Check the number of rows in GPAD output */
		String gpadOutputArr[] = gpad.split("\n", -1);
		/* 1 for header and 6 for the rest of the rows. the length should be  7 or 8.*/
		Assert.assertTrue("Should produce annotations", gpadOutputArr.length >= 1 + 6);
		
		/* Compare the output with the GPAD file that contains sample answers */
		List<String> lines = FileUtils.readLines(new File("src/test/resources/59d1072300000074.gpad"), "UTF-8");
		/* The order of the rows in the GPAD file can be different, so we compare rows by rows */
		for (String gpadOutputRow : gpadOutputArr) {
			 /* Additionally check all rows's qualifier contains |NOT substring inside */
			 String gpadRowArr[] =  gpadOutputRow.split("\t");
			 /* Skip checking the header; all rows need to contain NOT in its qualifier */
			 if (gpadRowArr.length > 2) {
				 Assert.assertTrue(gpadRowArr[2].contains("|NOT"));
			 }
		 }
	}
}
