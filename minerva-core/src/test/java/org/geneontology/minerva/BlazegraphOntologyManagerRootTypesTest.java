package org.geneontology.minerva;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class BlazegraphOntologyManagerRootTypesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testVirionComponentRootType() throws Exception {
        OWLOntologyManager ontologyManager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = ontologyManager.createOntology(IRI.create("http://example.org/virion-test"));
        OWLClass nucleocapsid = ontologyManager.getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0019013"));
        OWLClass virionComponent = ontologyManager.getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0044423"));
        ontologyManager.addAxiom(ontology, ontologyManager.getOWLDataFactory().getOWLSubClassOfAxiom(nucleocapsid, virionComponent));

        File journal = new File(folder.getRoot(), "ontology.jnl");
        BlazegraphOntologyManager repository = new BlazegraphOntologyManager(journal.getAbsolutePath(), false, ontology);
        try {
            Map<String, Set<String>> map = repository.getSuperCategoryMap(Collections.singleton(nucleocapsid.getIRI().toString()));
            Set<String> roots = map.get(nucleocapsid.getIRI().toString());
            assertTrue("Nucleocapsid should have virion component as a root type",
                    roots != null && roots.contains(virionComponent.getIRI().toString()));
        } finally {
            repository.dispose();
        }
    }
}
