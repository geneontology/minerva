package org.geneontology.minerva.model;

import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.test.TestOntology;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoCamModelTest {
    static final String gocam_dir = "src/test/resources/validation/model_test/";

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testRootTypesForComplements() throws Exception {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology tboxOntology = TestOntology.load();
        CurieHandler curieHandler = new MappedCurieHandler();
        String inputDB = folder.newFile().getAbsolutePath();
        UndoAwareMolecularModelManager m3 = new UndoAwareMolecularModelManager(tboxOntology, curieHandler, "gomodel", inputDB, null,
                TestOntology.newJournalPath(folder.getRoot()), false);
        try {
            m3.importModelToDatabase(new File("src/test/resources/test-complement-roots.ttl"), true);
            ModelContainer mc = m3.getModel(IRI.create("http://model.geneontology.org/61f3310500000003"));
            OWLOntology gocam_via_mc = mc.getAboxOntology();
            GoCamModel g = new GoCamModel(gocam_via_mc, m3);
            assertTrue("Can get roots for classes and complements",
                    g.ind_types.get(man.getOWLDataFactory().getOWLNamedIndividual(IRI.create("http://model.geneontology.org/61f3310500000003/61f3310500000004")))
                            .contains("http://purl.obolibrary.org/obo/GO_0008150"));
            assertTrue("Can get roots for classes and complements",
                    g.ind_types.get(man.getOWLDataFactory().getOWLNamedIndividual(IRI.create("http://model.geneontology.org/61f3310500000003/61f3310500000005")))
                            .contains("http://purl.obolibrary.org/obo/GO_0008150"));
        } finally {
            m3.dispose();
        }
    }

    @Test
    public void testGoModelStats() throws Exception {
        OWLOntology tbox_ontology = TestOntology.load();
        CurieHandler curieHandler = new MappedCurieHandler();
        String inputDB = folder.newFile().getAbsolutePath();
//load it into a journal and launch an m3
        UndoAwareMolecularModelManager m3 = null;
        try {
            File f = new File(gocam_dir);
            if (f.isDirectory()) {
                //remove anything that existed from previous runs
                File bgdb = new File(inputDB);
                if (bgdb.exists()) {
                    bgdb.delete();
                }
                //set it up with empty db
                m3 = new UndoAwareMolecularModelManager(tbox_ontology, curieHandler, "gomodel", inputDB, null,
                        TestOntology.newJournalPath(folder.getRoot()), false);
                //load the db
                for (File file : f.listFiles()) {
                    if (file.getName().endsWith("ttl")) {
                        m3.importModelToDatabase(file, true);
                    }
                }
            }
//read it back out and check on stats		
            for (IRI modelIRI : m3.getAvailableModelIds()) {
                ModelContainer mc = m3.getModel(modelIRI);
                OWLOntology gocam_via_mc = mc.getAboxOntology();
                GoCamModel g = new GoCamModel(gocam_via_mc, m3);
                //testing for an issue with the OWL blazegraph loader
                assertFalse("title not read out of M3 retrieved model " + modelIRI, (g.getTitle() == null));
                //note these test cases from reactome contain some reactions that are not officially 'part of' the model
                //these reactions are not counted as activities, but causal relations coming from them are counted.
                if (modelIRI.toString().contains("R-HSA-5654719")) {
                    //SHC-mediated cascade:FGFR4
                    assertTrue("wrong n activities " + g.getStats().n_activity_units, g.getStats().n_activity_units == 4);
                    assertTrue("wrong n complete activities " + g.getStats().n_complete_activity_units, g.getStats().n_complete_activity_units == 2);
                    assertTrue("wrong n unenabled activities " + g.getStats().n_no_enabler, g.getStats().n_no_enabler == 2);
                    assertTrue("wrong n causal relations " + g.getStats().n_causal_in_relation_assertions, g.getStats().n_causal_in_relation_assertions == 6);
                } else if (modelIRI.toString().contains("R-HSA-201688")) {
                    //WNT mediated activation of DVL
                    assertTrue("wrong n activities " + g.getStats().n_activity_units, g.getStats().n_activity_units == 4);
                    assertTrue("wrong n complete activities " + g.getStats().n_complete_activity_units, g.getStats().n_complete_activity_units == 3);
                    assertTrue("wrong n unenabled activities " + g.getStats().n_no_enabler, g.getStats().n_no_enabler == 1);
                    assertTrue("wrong n causal relations " + g.getStats().n_causal_in_relation_assertions, g.getStats().n_causal_in_relation_assertions == 3);
                } else if (modelIRI.toString().contains("R-HSA-5654733")) {
                    //Negative regulation of FGFR4 signaling
                    assertTrue("wrong n activities " + g.getStats().n_activity_units, g.getStats().n_activity_units == 3);
                    assertTrue("wrong n complete activities " + g.getStats().n_complete_activity_units, g.getStats().n_complete_activity_units == 2);
                    assertTrue("wrong n unenabled activities " + g.getStats().n_no_enabler, g.getStats().n_no_enabler == 1);
                    assertTrue("wrong n causal relations " + g.getStats().n_causal_in_relation_assertions, g.getStats().n_causal_in_relation_assertions == 3);
                }
            }
        } finally {
            if (m3 != null) {
                m3.dispose();
            }
        }
    }

}
