package org.geneontology.minerva.util;

import org.eclipse.rdf4j.sail.SailChangedEvent;
import org.eclipse.rdf4j.sail.SailChangedListener;

public class SailMutationCounter implements SailChangedListener {

    private int records = 0;

    public int mutationCount() {
        return records;
    }

    @Override
    public void sailChanged(SailChangedEvent sailChangedEvent) {
        records++;
    }

}
