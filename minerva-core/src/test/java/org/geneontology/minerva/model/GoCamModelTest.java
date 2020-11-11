package org.geneontology.minerva.model;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import org.geneontology.minerva.BlazegraphOntologyManager;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryResult;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

public class GoCamModelTest {
	static final String ontology_journal_file = "/tmp/test-go-lego-blazegraph.jnl";
	static final String gocam_dir = "src/test/resources/validation/model_test/";
	static BlazegraphOntologyManager onto_repo;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {

	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if(onto_repo!=null) {
			onto_repo.dispose();
		}
	}

	@Test
	public void testGoModelStats() throws Exception {		
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLOntology tbox_ontology = man.loadOntology(IRI.create("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl"));		
		CurieHandler curieHandler = new MappedCurieHandler();
		String inputDB = "/tmp/test-blazegraph-models.jnl";
//load it into a journal and launch an m3
		UndoAwareMolecularModelManager m3 = null;
		File f = new File(gocam_dir);
		if(f.isDirectory()) {
			//remove anything that existed from previous runs
			File bgdb = new File(inputDB);
			if(bgdb.exists()) {
				bgdb.delete(); 
			}
			//set it up with empty db
			m3 = new UndoAwareMolecularModelManager(tbox_ontology, curieHandler, "gomodel", inputDB, null, ontology_journal_file);
			onto_repo = m3.getGolego_repo(); 
			//load the db
			for(File file : f.listFiles()) {
				if(file.getName().endsWith("ttl")) {
					m3.importModelToDatabase(file, true);
				}
			}
		}
//read it back out and check on stats		
		for(IRI modelIRI : m3.getAvailableModelIds()) { 
			ModelContainer mc = m3.getModel(modelIRI);	
			OWLOntology gocam_via_mc = mc.getAboxOntology();			
			GoCamModel g = new GoCamModel(gocam_via_mc, m3);
			//testing for an issue with the OWL blazegraph loader
			assertFalse("title not read out of M3 retrieved model "+modelIRI, (g.getTitle()==null));
			//note these test cases from reactome contain some reactions that are not officially 'part of' the model
			//these reactions are not counted as activities, but causal relations coming from them are counted.
			if(modelIRI.toString().contains("R-HSA-5654719")) {
				//SHC-mediated cascade:FGFR4	
				assertTrue("wrong n activities "+g.getStats().n_activity_units, g.getStats().n_activity_units==4);
				assertTrue("wrong n complete activities "+g.getStats().n_complete_activity_units, g.getStats().n_complete_activity_units==2);
				assertTrue("wrong n unenabled activities "+g.getStats().n_no_enabler, g.getStats().n_no_enabler==2);
				assertTrue("wrong n causal relations "+g.getStats().n_causal_in_relation_assertions, g.getStats().n_causal_in_relation_assertions==6);			
			}else if(modelIRI.toString().contains("R-HSA-201688")) {
				//WNT mediated activation of DVL			
				assertTrue("wrong n activities "+g.getStats().n_activity_units, g.getStats().n_activity_units==4);
				assertTrue("wrong n complete activities "+g.getStats().n_complete_activity_units, g.getStats().n_complete_activity_units==3);
				assertTrue("wrong n unenabled activities "+g.getStats().n_no_enabler, g.getStats().n_no_enabler==1);
				assertTrue("wrong n causal relations "+g.getStats().n_causal_in_relation_assertions, g.getStats().n_causal_in_relation_assertions==3);
			}else if(modelIRI.toString().contains("R-HSA-5654733")) {
				//Negative regulation of FGFR4 signaling
				assertTrue("wrong n activities "+g.getStats().n_activity_units, g.getStats().n_activity_units==3);
				assertTrue("wrong n complete activities "+g.getStats().n_complete_activity_units, g.getStats().n_complete_activity_units==2);
				assertTrue("wrong n unenabled activities "+g.getStats().n_no_enabler, g.getStats().n_no_enabler==1);
				assertTrue("wrong n causal relations "+g.getStats().n_causal_in_relation_assertions, g.getStats().n_causal_in_relation_assertions==3);
			}
		}	
	}

}
