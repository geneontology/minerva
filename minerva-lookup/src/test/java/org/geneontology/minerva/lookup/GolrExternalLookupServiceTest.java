package org.geneontology.minerva.lookup;

import org.bbop.golr.java.RetrieveGolrBioentities;
import org.bbop.golr.java.RetrieveGolrBioentities.GolrBioentityDocument;
import org.bbop.golr.java.RetrieveGolrOntologyClass;
import org.bbop.golr.java.RetrieveGolrOntologyClass.GolrOntologyClassDocument;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GolrExternalLookupServiceTest {

    private static final String UNUSED_URL = "http://example.invalid";
    private final CurieHandler handler = DefaultCurieHandler.getDefaultHandler();

    @Test
    public void testBioentityLookupDoesNotFallBackToOntologyLookup() throws Exception {
        FakeBioentityClient bioentities = new FakeBioentityClient()
                .add("SGD:S000004529", bioentity("TEM1 Scer", "gene", "NCBITaxon:559292"));
        FakeOntologyClient ontology = new FakeOntologyClient()
                .add("SGD:S000004529", ontologyClass("SGD:S000004529", "wrong fallback", null));
        GolrExternalLookupService service = service(bioentities, ontology);
        IRI id = handler.getIRI("SGD:S000004529");

        List<LookupEntry> lookup = service.lookup(id);

        assertEquals(1, lookup.size());
        assertEquals(id, lookup.get(0).id);
        assertEquals("TEM1 Scer", lookup.get(0).label);
        assertEquals("gene", lookup.get(0).type);
        assertEquals("NCBITaxon:559292", lookup.get(0).taxon);
        assertEquals(1, bioentities.requests);
        assertEquals(0, ontology.singleRequests);
    }

    @Test
    public void testOntologyLookupFallbackMapsClosureAndTaxon() throws Exception {
        FakeBioentityClient bioentities = new FakeBioentityClient();
        FakeOntologyClient ontology = new FakeOntologyClient()
                .add("UniProtKB:P32241", ontologyClass("UniProtKB:P32241", "VIPR1 Hsap",
                        "NCBITaxon:9606", "CHEBI:33695", "SO:0000704"));
        GolrExternalLookupService service = service(bioentities, ontology);
        IRI id = handler.getIRI("UniProtKB:P32241");

        List<LookupEntry> lookup = service.lookup(id);

        assertEquals(1, lookup.size());
        LookupEntry entry = lookup.get(0);
        assertEquals(id, entry.id);
        assertEquals("VIPR1 Hsap", entry.label);
        assertEquals("ontology_class", entry.type);
        assertEquals("NCBITaxon:9606", entry.taxon);
        assertEquals(Arrays.asList("CHEBI:33695", "SO:0000704"), entry.isa_closure);
        assertEquals("http://purl.obolibrary.org/obo/CHEBI_33695", entry.direct_parent_iri);
        assertEquals(1, bioentities.requests);
        assertEquals(1, ontology.singleRequests);
    }

    @Test
    public void testMissingIdentifierReturnsEmptyList() throws Exception {
        FakeBioentityClient bioentities = new FakeBioentityClient();
        FakeOntologyClient ontology = new FakeOntologyClient();
        GolrExternalLookupService service = service(bioentities, ontology);

        List<LookupEntry> lookup = service.lookup(handler.getIRI("GO:9999999"));

        assertEquals(Collections.emptyList(), lookup);
        assertEquals(1, bioentities.requests);
        assertEquals(1, ontology.singleRequests);
    }

    @Test
    public void testLookupIOExceptionReturnsNull() throws Exception {
        FakeBioentityClient bioentities = new FakeBioentityClient();
        bioentities.fail = true;
        FakeOntologyClient ontology = new FakeOntologyClient();
        GolrExternalLookupService service = service(bioentities, ontology);

        assertNull(service.lookup(handler.getIRI("SGD:S000004529")));
        assertEquals(1, bioentities.requests);
        assertEquals(0, ontology.singleRequests);
    }

    @Test
    public void testBatchLookupMapsResultsToRequestedIris() throws Exception {
        FakeBioentityClient bioentities = new FakeBioentityClient();
        FakeOntologyClient ontology = new FakeOntologyClient()
                .add("GO:0140312", ontologyClass("GO:0140312", "cargo adaptor activity", null))
                .add("ComplexPortal:CPX-900", ontologyClass("ComplexPortal:CPX-900",
                        "saga-kat2a_human Hsap", "NCBITaxon:9606", "GO:0032991"));
        GolrExternalLookupService service = service(bioentities, ontology);
        IRI go = handler.getIRI("GO:0140312");
        IRI complex = handler.getIRI("ComplexPortal:CPX-900");
        Set<IRI> ids = new HashSet<IRI>(Arrays.asList(go, complex));

        Map<IRI, List<LookupEntry>> lookups = service.lookupBatch(ids);

        assertEquals(2, lookups.size());
        assertEquals("cargo adaptor activity", lookups.get(go).get(0).label);
        assertEquals("saga-kat2a_human Hsap", lookups.get(complex).get(0).label);
        assertEquals(Collections.singletonList("GO:0032991"), lookups.get(complex).get(0).isa_closure);
        assertEquals(0, bioentities.requests);
        assertEquals(1, ontology.batchRequests);
    }

    @Test
    public void testBatchIOExceptionReturnsNull() throws Exception {
        FakeOntologyClient ontology = new FakeOntologyClient();
        ontology.fail = true;
        GolrExternalLookupService service = service(new FakeBioentityClient(), ontology);

        assertNull(service.lookupBatch(Collections.singleton(handler.getIRI("GO:0140312"))));
        assertEquals(1, ontology.batchRequests);
    }

    private GolrExternalLookupService service(FakeBioentityClient bioentities, FakeOntologyClient ontology) {
        return new GolrExternalLookupService(UNUSED_URL, bioentities, ontology, handler);
    }

    private static GolrBioentityDocument bioentity(String label, String type, String taxon) {
        GolrBioentityDocument document = new GolrBioentityDocument();
        document.bioentity_label = label;
        document.type = type;
        document.taxon = taxon;
        return document;
    }

    private static GolrOntologyClassDocument ontologyClass(String id, String label, String taxon,
                                                            String... closure) {
        GolrOntologyClassDocument document = new GolrOntologyClassDocument();
        document.annotation_class = id;
        document.annotation_class_label = label;
        document.only_in_taxon = taxon;
        document.isa_closure = Arrays.asList(closure);
        return document;
    }

    private static class FakeBioentityClient extends RetrieveGolrBioentities {

        private final Map<String, List<GolrBioentityDocument>> responses =
                new HashMap<String, List<GolrBioentityDocument>>();
        private int requests;
        private boolean fail;

        private FakeBioentityClient() {
            super(UNUSED_URL, 0);
        }

        private FakeBioentityClient add(String id, GolrBioentityDocument document) {
            responses.put(id, Collections.singletonList(document));
            return this;
        }

        @Override
        public List<GolrBioentityDocument> getGolrBioentites(String id) throws IOException {
            requests++;
            if (fail) {
                throw new IOException("simulated bioentity lookup failure");
            }
            List<GolrBioentityDocument> documents = responses.get(id);
            return documents == null ? Collections.<GolrBioentityDocument>emptyList() : documents;
        }
    }

    private static class FakeOntologyClient extends RetrieveGolrOntologyClass {

        private final Map<String, List<GolrOntologyClassDocument>> responses =
                new HashMap<String, List<GolrOntologyClassDocument>>();
        private int singleRequests;
        private int batchRequests;
        private boolean fail;

        private FakeOntologyClient() {
            super(UNUSED_URL, 0);
        }

        private FakeOntologyClient add(String id, GolrOntologyClassDocument document) {
            responses.put(id, Collections.singletonList(document));
            return this;
        }

        @Override
        public List<GolrOntologyClassDocument> getGolrOntologyCls(String id) throws IOException {
            singleRequests++;
            if (fail) {
                throw new IOException("simulated ontology lookup failure");
            }
            List<GolrOntologyClassDocument> documents = responses.get(id);
            return documents == null ? Collections.<GolrOntologyClassDocument>emptyList() : documents;
        }

        @Override
        public Map<String, List<GolrOntologyClassDocument>> getGolrOntologyCls(Set<String> curies)
                throws IOException {
            batchRequests++;
            if (fail) {
                throw new IOException("simulated ontology batch lookup failure");
            }
            Map<String, List<GolrOntologyClassDocument>> result =
                    new HashMap<String, List<GolrOntologyClassDocument>>();
            for (String curie : curies) {
                if (responses.containsKey(curie)) {
                    result.put(curie, new ArrayList<GolrOntologyClassDocument>(responses.get(curie)));
                }
            }
            return result;
        }
    }
}
