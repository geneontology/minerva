package org.geneontology.minerva.model;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.geneontology.minerva.BlazegraphOntologyManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

public class GoCamModelTest {
	static final String ontology_journal_file = "/tmp/test-go-lego-blazegraph.jnl";
	static final String gocam_file = "src/test/resources/validation/tmp/R-HSA-8952158.ttl";//R-HSA-70171.ttl";
	static BlazegraphOntologyManager onto_repo;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		System.out.println("setting up ontology manager");
		onto_repo = new BlazegraphOntologyManager(ontology_journal_file);
		System.out.println("ready");
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		onto_repo.dispose();
	}

	@Test
	public void test() {
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		try {
			System.out.println("loading "+gocam_file);
			OWLOntology gocam = man.loadOntologyFromOntologyDocument(new File(gocam_file));
			GoCamModel g = new GoCamModel(gocam, onto_repo);
			System.out.println("gocam model \n\t"+g.toString()+"\n"+g.getStats().toString());
		} catch (OWLOntologyCreationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
		
//		fail("Not yet implemented"); // TODO
	}

}
