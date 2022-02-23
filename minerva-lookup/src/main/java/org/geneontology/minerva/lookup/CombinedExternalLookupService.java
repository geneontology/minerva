package org.geneontology.minerva.lookup;

import org.apache.commons.lang3.StringUtils;
import org.semanticweb.owlapi.model.IRI;

import java.util.*;

public class CombinedExternalLookupService implements ExternalLookupService {

    private final Iterable<ExternalLookupService> services;

    /**
     * @param services
     */
    public CombinedExternalLookupService(ExternalLookupService... services) {
        this(Arrays.asList(services));
    }

    /**
     * @param services
     */
    public CombinedExternalLookupService(Iterable<ExternalLookupService> services) {
        this.services = services;
    }

    @Override
    public List<LookupEntry> lookup(IRI id) {
        List<LookupEntry> result = new ArrayList<LookupEntry>();
        for (ExternalLookupService service : services) {
            List<LookupEntry> cResult = service.lookup(id);
            if (cResult != null && !cResult.isEmpty()) {
                result.addAll(cResult);
            }
        }
        return result;
    }

    @Override
    public LookupEntry lookup(IRI id, String taxon) {
        LookupEntry result = null;
        for (ExternalLookupService service : services) {
            result = service.lookup(id, taxon);
            if (result != null) {
                break;
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "[" + StringUtils.join(services, "|") + "]";
    }

    @Override
    public Map<IRI, List<LookupEntry>> lookupBatch(Set<IRI> to_look_up) {
        // TODO Auto-generated method stub
        return null;
    }


}
