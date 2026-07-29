package org.geneontology.minerva.test;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Utilities for tests that need a small, deterministic GO-CAM TBox. */
public final class TestOntology {

    private TestOntology() {
    }

    public static OWLOntology load() throws IOException, OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        try (InputStream stream = TestOntology.class.getResourceAsStream("/test-ontology.ttl")) {
            if (stream == null) {
                throw new IOException("Missing test ontology resource: /test-ontology.ttl");
            }
            return manager.loadOntologyFromOntologyDocument(stream);
        }
    }

    /** Return a unique, initially absent path so Minerva initializes it from the test TBox. */
    public static String newJournalPath(File directory) {
        return new File(directory, "test-ontology-" + UUID.randomUUID() + ".jnl").getAbsolutePath();
    }
}
