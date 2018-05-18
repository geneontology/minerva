package org.geneontology.minerva.curie;

import static org.junit.Assert.*;

import java.io.InputStream;

import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;

public class DefaultCurieHandlerTest {

	@Test
	public void testAll() {
		MappedCurieHandler handler = (MappedCurieHandler) DefaultCurieHandler.getDefaultHandler();
		assertFalse(handler.getInternalMappings().isEmpty());
		assertTrue(handler.getInternalMappings().containsKey("BFO"));
		assertTrue(handler.getInternalMappings().containsKey("GO"));
		assertTrue(handler.getInternalMappings().containsKey("IAO"));
		assertTrue(handler.getInternalMappings().containsKey("MGI"));
		assertTrue(handler.getInternalMappings().containsKey("ECO"));
		assertTrue(handler.getInternalMappings().containsKey("PMID"));
		assertFalse(handler.getInternalMappings().containsKey("BLABLA"));
	}
    
    @Test
    public void testGo() {
        InputStream stream = DefaultCurieHandler.loadResourceAsStream("go_context.jsonld");
        assertNotNull(stream);
        CurieMappings mappings = CurieMappingsJsonld.loadJsonLdContext(stream);
        assertFalse(mappings.getMappings().isEmpty());
    }
    
    @Test
    public void testObo() {
        InputStream stream = DefaultCurieHandler.loadResourceAsStream("obo_context.jsonld");
        assertNotNull(stream);
        CurieMappings mappings = CurieMappingsJsonld.loadJsonLdContext(stream);
        assertFalse(mappings.getMappings().isEmpty());
    }
	
	@Test
	public void testMonarch() {
		InputStream stream = DefaultCurieHandler.loadResourceAsStream("monarch_context.jsonld");
		assertNotNull(stream);
		CurieMappings mappings = CurieMappingsJsonld.loadJsonLdContext(stream);
		assertFalse(mappings.getMappings().isEmpty());
	}

	@Test
	public void testConversions() throws UnknownIdentifierException {
		CurieHandler handler = DefaultCurieHandler.getDefaultHandler();
		final OWLDataFactory f = OWLManager.createOWLOntologyManager().getOWLDataFactory();
		
		IRI longBFO = IRI.create("http://purl.obolibrary.org/obo/BFO_0000050");
		
		assertEquals(longBFO, handler.getIRI("BFO:0000050"));
		assertEquals("BFO:0000050", handler.getCuri(f.getOWLAnnotationProperty(longBFO)));

		
		IRI longEco = IRI.create("http://purl.obolibrary.org/obo/ECO_0000217");
		assertEquals(longEco, handler.getIRI("ECO:0000217"));
		assertEquals("ECO:0000217", handler.getCuri(f.getOWLClass(longEco)));
		
		
		IRI longPmid = IRI.create("http://www.ncbi.nlm.nih.gov/pubmed/0000");
		assertEquals(longPmid, handler.getIRI("PMID:0000"));
		assertEquals("PMID:0000", handler.getCuri(f.getOWLClass(longPmid)));
		
		// test failure for non existing prefix
		try {
			handler.getIRI("BLABLA:000001");
			fail("Expected an UnknownIdentifierException to be thrown");
		} catch (UnknownIdentifierException e) {
		}
	}
}
