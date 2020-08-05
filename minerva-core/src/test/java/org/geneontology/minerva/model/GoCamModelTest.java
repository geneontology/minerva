package org.geneontology.minerva.model;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.geneontology.minerva.BlazegraphOntologyManager;
import org.geneontology.minerva.ModelContainer;
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
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

public class GoCamModelTest {
	static final String ontology_journal_file = "/tmp/test-go-lego-blazegraph.jnl";
	static final String gocam_file = "src/test/resources/validation/tmp/";//SYNGO_2759.ttl";//5966411600000744.ttl";//R-HSA-70171.ttl"; //R-HSA-8952158.ttl";
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

	//@Test
	public void testDirect() throws IOException {
		System.out.println("setting up ontology manager");
		onto_repo = new BlazegraphOntologyManager(ontology_journal_file);
		System.out.println("ready");
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		try {
			System.out.println("loading "+gocam_file);
			OWLOntology gocam = man.loadOntologyFromOntologyDocument(new File(gocam_file));
			GoCamModel g = new GoCamModel(gocam, onto_repo);
			System.out.println("gocam model \n\t"+g.toString()+"\n"+g.getGoCamModelStats().toString());
			System.out.println(g.getGoCamModelStats().stats2cols());
		} catch (OWLOntologyCreationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}

	@Test
	public void testWithModelManager() throws OWLOntologyCreationException, IOException, RepositoryException, RDFParseException, RDFHandlerException {
		OWLOntology dummy = OWLManager.createOWLOntologyManager().createOntology(IRI.create("http://example.org/dummy"));
		CurieHandler curieHandler = new MappedCurieHandler();
		String inputDB = "/tmp/test-blazegraph.jnl";
		BlazegraphMolecularModelManager<Void> m3 = new BlazegraphMolecularModelManager<>(dummy, curieHandler, "gomodel", inputDB, null, ontology_journal_file);
		File f = new File(gocam_file);
		if(f.isDirectory()) {
			for(File file : f.listFiles()) {
				if(file.getName().endsWith("ttl")) {
					m3.importModelToDatabase(file, true);
				}
			}
		}
		m3.getAvailableModelIds().stream().forEach(modelIRI -> {
			OWLOntology gocam = m3.getModelAbox(modelIRI); 
		//the following results in very odd behavior where sometimes the title goes missing from the model	
		//	ModelContainer mc = m3.getModel(modelIRI);	
		//	OWLOntology gocam = mc.getAboxOntology();
			GoCamModel g;
			try {
				g = new GoCamModel(gocam, m3.getGolego_repo());
				if(g.getTitle()==null) {
					System.out.println("missing title "+modelIRI);
				}
				System.out.println("gocam model \n\t"+g.toString()+"\n"+g.getGoCamModelStats().toString());
				System.out.println(g.getGoCamModelStats().stats2cols());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		m3.getGolego_repo().dispose();
	}

}
