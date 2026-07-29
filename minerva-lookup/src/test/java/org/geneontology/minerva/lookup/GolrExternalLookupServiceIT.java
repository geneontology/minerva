package org.geneontology.minerva.lookup;

import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Optional smoke tests for the deployed Golr service. Run with -Pgolr-integration test. */
public class GolrExternalLookupServiceIT {

    private static final String DEFAULT_GOLR_URL = "http://noctua-golr.berkeleybop.org";
    private final CurieHandler handler = DefaultCurieHandler.getDefaultHandler();
    private final GolrExternalLookupService service = new GolrExternalLookupService(
            System.getProperty("golr.url", DEFAULT_GOLR_URL), handler);

    @Test
    public void testKnownOntologyClassLookup() throws Exception {
        List<LookupEntry> lookup = service.lookup(handler.getIRI("GO:0140312"));

        assertNotNull(lookup);
        assertEquals(1, lookup.size());
        assertEquals("cargo adaptor activity", lookup.get(0).label);
    }

    @Test
    public void testKnownTermsBatchLookup() throws Exception {
        IRI go = handler.getIRI("GO:0003700");
        IRI geneProduct = handler.getIRI("UniProtKB:P32241");
        Set<IRI> ids = new HashSet<IRI>(Arrays.asList(go, geneProduct));

        Map<IRI, List<LookupEntry>> lookups = service.lookupBatch(ids);

        assertNotNull(lookups);
        assertTrue(lookups.containsKey(go));
        assertTrue(lookups.containsKey(geneProduct));
        assertFalse(lookups.get(go).isEmpty());
        assertFalse(lookups.get(geneProduct).isEmpty());
    }
}
