package org.geneontology.minerva.lookup;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.bbop.golr.java.RetrieveGolrBioentities;
import org.bbop.golr.java.RetrieveGolrOntologyClass;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;

import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GolrExternalLookupServiceTest {

    private static final String golrUrl = "http://noctua-golr.berkeleybop.org";
    //noting that tests written assumed "http://golr.berkeleybop.org/solr";
    private final CurieHandler handler = DefaultCurieHandler.getDefaultHandler();

    @Test
    public void testLookupString1() throws Exception {
        GolrExternalLookupService s = new GolrExternalLookupService(golrUrl, handler, true);
        String testCurie = "SGD:S000004529";
        List<LookupEntry> lookup = s.lookup(handler.getIRI(testCurie));
        assertEquals(1, lookup.size());
        assertEquals("TEM1 Scer", lookup.get(0).label);

        IRI testIRI = IRI.create("http://identifiers.org/sgd/S000004529");
        assertEquals(testCurie, handler.getCuri(testIRI));
        List<LookupEntry> lookup2 = s.lookup(testIRI);
        assertEquals(1, lookup2.size());
        assertEquals("TEM1 Scer", lookup2.get(0).label);
    }

    @Test
    public void testLookupString2() throws Exception {
        GolrExternalLookupService s = new GolrExternalLookupService(golrUrl, handler);
        List<LookupEntry> lookup = s.lookup(handler.getIRI("SGD:S000004328"));
        assertEquals(1, lookup.size());
        assertEquals("SGD1 Scer", lookup.get(0).label);
    }

    @Test
    public void testLookupString3() throws Exception {
        GolrExternalLookupService s = new GolrExternalLookupService(golrUrl, handler);
        String testCurie = "SGD:S000005952";
        List<LookupEntry> lookup = s.lookup(handler.getIRI(testCurie));
        assertEquals(1, lookup.size());
        assertEquals("PHO85 Scer", lookup.get(0).label);
        //assertEquals("gene", lookup.get(0).type);

        IRI testIRI = IRI.create("http://identifiers.org/sgd/S000005952");
        String gCuri = handler.getCuri(testIRI);
        List<LookupEntry> lookup2 = s.lookup(testIRI);
        assertEquals(testCurie, gCuri);
        assertEquals(1, lookup2.size());
        assertEquals("PHO85 Scer", lookup2.get(0).label);
        //assertEquals("gene", lookup2.get(0).type);

    }

    @Test
    public void testLookupStringCls() throws Exception {
        GolrExternalLookupService s = new GolrExternalLookupService(golrUrl, handler);
        List<LookupEntry> lookup = s.lookup(handler.getIRI("GO:0140312"));
        //I don't believe we are loading PO:0001040 anymore
        assertEquals(1, lookup.size());
        assertEquals("cargo adaptor activity", lookup.get(0).label);
    }

    @Test
    public void testLookupStringCls2() throws Exception {
        Logger.getLogger(GolrExternalLookupService.class).setLevel(Level.DEBUG);
        GolrExternalLookupService s = new GolrExternalLookupService(golrUrl, handler);
        List<LookupEntry> lookup = s.lookup(handler.getIRI("WBbt:0006748"));
        assertEquals(1, lookup.size());
        assertEquals("vulva", lookup.get(0).label);
        //suspect this has fallen out of scope
        //	List<LookupEntry> lookup = s.lookup(handler.getIRI("UBERON:0010403"));
        //	assertEquals(1, lookup.size());
        //	assertEquals("brain marginal zone", lookup.get(0).label);
    }

    @Test
    public void testLookupGeneProductCls() throws Exception {
        Logger.getLogger(GolrExternalLookupService.class).setLevel(Level.DEBUG);
        GolrExternalLookupService s = new GolrExternalLookupService(golrUrl, handler);
        IRI gp_iri = handler.getIRI("UniProtKB:P32241-1");
        List<LookupEntry> lookup = s.lookup(gp_iri);
        assertEquals(1, lookup.size());
        LookupEntry e = lookup.get(0);
        assertEquals("VIPR1 Hsap", e.label);
        assertEquals(23, e.isa_closure.size());
        assertTrue(e.isa_closure.contains("PR:000000001"));
//		
        gp_iri = handler.getIRI("SGD:S000005952");
        lookup = s.lookup(gp_iri);
        assertEquals(1, lookup.size());
        e = lookup.get(0);
        assertEquals("PHO85 Scer", e.label);
        assertTrue(e.isa_closure.size() >= 15);
        assertTrue(e.isa_closure.contains("CHEBI:33695"));
//		
//		//example non-gene obo:ComplexPortal_CPX-900  https://www.ebi.ac.uk/complexportal/complex/CPX-900
        gp_iri = handler.getIRI("ComplexPortal:CPX-900");
        lookup = s.lookup(gp_iri);
        assertEquals(1, lookup.size());
        e = lookup.get(0);
        assertEquals("saga-kat2a_human Hsap", e.label);
        assertEquals(6, e.isa_closure.size());
        assertTrue(e.isa_closure.contains("GO:0032991"));
    }

    @Test
    public void testCachedGolrLookup() throws Exception {
        final List<URI> requests = new ArrayList<URI>();
        GolrExternalLookupService golr = new GolrExternalLookupService(golrUrl,
                new RetrieveGolrBioentities(golrUrl, 2) {

                    @Override
                    protected void logRequest(URI uri) {
                        requests.add(uri);
                    }

                }, new RetrieveGolrOntologyClass(golrUrl, 2) {
            @Override
            protected void logRequest(URI uri) {
                requests.add(uri);
            }
        }, handler);
        ExternalLookupService s = new CachingExternalLookupService(golr, 1000, 24l, TimeUnit.HOURS);

        List<LookupEntry> lookup1 = s.lookup(handler.getIRI("SGD:S000004529"));
        assertEquals(1, lookup1.size());
        assertEquals("TEM1 Scer", lookup1.get(0).label);
        int count = requests.size();

        List<LookupEntry> lookup2 = s.lookup(handler.getIRI("SGD:S000004529"));
        assertEquals(1, lookup2.size());
        assertEquals("TEM1 Scer", lookup2.get(0).label);

        // there should be no new request to Golr, that's what the cache is for!
        assertEquals(count, requests.size());
    }

    @Test
    public void testCachedGolrBatchLookup() throws Exception {
        final List<URI> requests = new ArrayList<URI>();
        GolrExternalLookupService golr = new GolrExternalLookupService(golrUrl,
                new RetrieveGolrBioentities(golrUrl, 2) {

                    @Override
                    protected void logRequest(URI uri) {
                        requests.add(uri);
                    }

                }, new RetrieveGolrOntologyClass(golrUrl, 2) {
            @Override
            protected void logRequest(URI uri) {
                requests.add(uri);
            }
        }, handler);
        ExternalLookupService s = new CachingExternalLookupService(golr, 1000, 24l, TimeUnit.HOURS);
        Set<IRI> ids = new HashSet<IRI>();
        ids.add(handler.getIRI("SGD:S000004529"));
        ids.add(handler.getIRI("CHEBI:33695"));
        ids.add(handler.getIRI("ComplexPortal:CPX-900"));
        ids.add(handler.getIRI("UniProtKB:P32241-1"));
        ids.add(handler.getIRI("GO:0003700"));

        Map<IRI, List<LookupEntry>> lookups = s.lookupBatch(ids);
        assertEquals(5, lookups.size());
        assertEquals("TEM1 Scer", lookups.get(handler.getIRI("SGD:S000004529")).get(0).label);
        int count = requests.size();

        Map<IRI, List<LookupEntry>> lookups2 = s.lookupBatch(ids);
        assertEquals(5, lookups2.size());
        assertEquals("TEM1 Scer", lookups2.get(handler.getIRI("SGD:S000004529")).get(0).label);

        // there should be no new request to Golr, that's what the cache is for!
        assertEquals(count, requests.size());
    }


    public void printListTermLabels(ExternalLookupService s, List<String> terms) throws UnknownIdentifierException {
        for (String id : terms) {
            if (id.contains("CHEBI") || id.contains("PR") || id.contains("BFO")) {
                List<LookupEntry> elookup = s.lookup(handler.getIRI(id));
                for (LookupEntry l : elookup) {
                    System.out.println(l.id + "\t" + l.label);
                }
            }
        }
    }

}
