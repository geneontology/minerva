package org.geneontology.minerva.evidence;

import static org.junit.Assert.*;

import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;

import owltools.io.ParserWrapper;
import owltools.io.CatalogXmlIRIMapper;

public class FindGoCodesTest {
	
	private static CurieHandler curieHandler;
	private static OWLOntology eco;
	private static FindGoCodes codes;
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		curieHandler = DefaultCurieHandler.getDefaultHandler();
		codes = new FindGoCodes(curieHandler);
		ParserWrapper pw = new ParserWrapper();

		// if available, set catalog
        String envCatalog = System.getenv().get("GENEONTOLOGY_CATALOG");
        if (envCatalog != null) {
        	pw.addIRIMapper(new CatalogXmlIRIMapper(envCatalog));
        }

		eco = pw.parseOWL(IRI.create("http://purl.obolibrary.org/obo/eco.owl"));
	}

	@Test
	public void testFindShortEvidence() throws Exception {
		// ECO:0000305 (IC) <- ECO:0000306 <- ECO:0001828
		Pair<String, String> pair0 = lookup("ECO:0000305");
		assertNotNull(pair0);
		assertEquals("IC", pair0.getLeft());
		
		Pair<String, String> pair1 = lookup("ECO:0000306");
		assertNotNull(pair1);
		assertEquals("IC", pair1.getLeft());
		
		Pair<String, String> pair2 = lookup("ECO:0001828");
		assertNotNull(pair2);
		assertEquals("IC", pair2.getLeft());
	}
	
	private Pair<String, String> lookup(String testId) {
		IRI testIRI = curieHandler.getIRI(testId);
		OWLClass testOwlClass = eco.getOWLOntologyManager().getOWLDataFactory().getOWLClass(testIRI);
		Pair<String, String> pair = codes.findShortEvidence(testOwlClass, testId, eco);
		return pair;
	}

}
