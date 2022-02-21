package org.geneontology.minerva.curie;

import java.util.Collections;
import java.util.Map;

public interface CurieMappings {

    public Map<String, String> getMappings();

    static final CurieMappings EMPTY = new SimpleCurieMappings(Collections.<String, String>emptyMap());

    static class SimpleCurieMappings implements CurieMappings {
        private final Map<String, String> mappings;

        public SimpleCurieMappings(Map<String, String> mappings) {
            this.mappings = mappings;
        }

        @Override
        public Map<String, String> getMappings() {
            return mappings;
        }
    }
}
