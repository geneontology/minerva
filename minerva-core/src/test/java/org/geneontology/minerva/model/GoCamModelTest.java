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
	static final String ontology_journal_file = "/tmp/blazegraph.jnl";
	static final String gocam_dir = "src/test/resources/validation/tmp/";
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
	public void testWithM3() throws IOException, OWLOntologyCreationException, RepositoryException, RDFParseException, RDFHandlerException {		
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
			//the following results in very odd behavior where sometimes the title goes missing from the model	
			ModelContainer mc = m3.getModel(modelIRI);	
			OWLOntology gocam_via_mc = mc.getAboxOntology();
			GoCamModel g = new GoCamModel(gocam_via_mc, onto_repo);
			//testing for an issue with the OWL blazegraph loader
			assertFalse("title not read out of M3 retrieved model "+modelIRI, (g.getTitle()==null));
			for(OWLObjectProperty p : g.getCausal_count().keySet()) {
				System.out.println(p+" "+g.getCausal_count().get(p));
			}
			System.out.println("Finished loading as GoCamModel: "+modelIRI);
			
		}	
	}

}
