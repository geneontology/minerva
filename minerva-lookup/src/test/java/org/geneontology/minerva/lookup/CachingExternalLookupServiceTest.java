package org.geneontology.minerva.lookup;

import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class CachingExternalLookupServiceTest {

    @Test
    public void testRepeatedLookupUsesCachedResult() {
        IRI id = IRI.create("http://example.org/entity/1");
        RecordingLookupService delegate = new RecordingLookupService()
                .add(id, "first entity");
        ExternalLookupService cache = new CachingExternalLookupService(delegate, 100, 1, TimeUnit.HOURS);

        assertEquals("first entity", cache.lookup(id).get(0).label);
        assertEquals("first entity", cache.lookup(id).get(0).label);

        assertEquals(1, delegate.lookupRequests);
    }

    @Test
    public void testRepeatedBatchLookupUsesCachedResults() {
        IRI first = IRI.create("http://example.org/entity/1");
        IRI second = IRI.create("http://example.org/entity/2");
        RecordingLookupService delegate = new RecordingLookupService()
                .add(first, "first entity")
                .add(second, "second entity");
        ExternalLookupService cache = new CachingExternalLookupService(delegate, 100, 1, TimeUnit.HOURS);
        Set<IRI> ids = new HashSet<IRI>(Arrays.asList(first, second));

        Map<IRI, List<LookupEntry>> firstLookup = cache.lookupBatch(ids);
        Map<IRI, List<LookupEntry>> secondLookup = cache.lookupBatch(ids);

        assertEquals("first entity", firstLookup.get(first).get(0).label);
        assertEquals("second entity", secondLookup.get(second).get(0).label);
        assertEquals(2, delegate.lookupRequests);
        assertEquals(0, delegate.batchRequests);
    }

    private static class RecordingLookupService implements ExternalLookupService {

        private final Map<IRI, List<LookupEntry>> entries = new HashMap<IRI, List<LookupEntry>>();
        private int lookupRequests;
        private int batchRequests;

        private RecordingLookupService add(IRI id, String label) {
            entries.put(id, Collections.singletonList(new LookupEntry(id, label, "test", null, null)));
            return this;
        }

        @Override
        public List<LookupEntry> lookup(IRI id) {
            lookupRequests++;
            return entries.get(id);
        }

        @Override
        public LookupEntry lookup(IRI id, String taxon) {
            List<LookupEntry> lookup = lookup(id);
            return lookup == null || lookup.isEmpty() ? null : lookup.get(0);
        }

        @Override
        public Map<IRI, List<LookupEntry>> lookupBatch(Set<IRI> toLookUp) {
            batchRequests++;
            Map<IRI, List<LookupEntry>> result = new HashMap<IRI, List<LookupEntry>>();
            for (IRI id : toLookUp) {
                if (entries.containsKey(id)) {
                    result.put(id, entries.get(id));
                }
            }
            return result;
        }
    }
}
