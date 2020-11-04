package org.geneontology.minerva.server.handler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;

import org.apache.commons.io.IOUtils;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.server.inferences.InferenceProviderCreator;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import owltools.io.ParserWrapper;

public class ModelDiffTest {

	@ClassRule
	public static TemporaryFolder folder = new TemporaryFolder();

	private static CurieHandler curieHandler = null;
	private static JsonOrJsonpBatchHandler handler = null;
	private static UndoAwareMolecularModelManager models = null;
	static final String go_lego_journal_file = "/tmp/test-go-lego-blazegraph.jnl";

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		init(new ParserWrapper());
	}

	static void init(ParserWrapper pw) throws OWLOntologyCreationException, IOException {
		//This includes only the needed terms for the test to pass
		final OWLOntology tbox = OWLManager.createOWLOntologyManager().loadOntology(IRI.create(new File("src/test/resources/edit-test/go-lego-empty.owl")));
		// curie handler
		final String modelIdcurie = "gomodel";
		final String modelIdPrefix = "http://model.geneontology.org/";
		final CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap(modelIdcurie, modelIdPrefix));
		curieHandler = new MappedCurieHandler(DefaultCurieHandler.loadDefaultMappings(), localMappings);

		models = new UndoAwareMolecularModelManager(tbox, curieHandler, modelIdPrefix, folder.newFile().getAbsolutePath(), null, go_lego_journal_file);
		InferenceProviderCreator ipc = null;
		handler = new JsonOrJsonpBatchHandler(models, "development", ipc,
				Collections.<OWLObjectProperty>emptySet(), (ExternalLookupService) null);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if (handler != null) {
			handler = null;
		}
		if (models != null) {
			models.dispose();
		}
	}

	@Test
	public void testAddPmidDiff() throws OWLOntologyCreationException, RepositoryException, RDFParseException, RDFHandlerException, IOException {
		fail("Not yet implemented"); // TODO
		
		
		File f1 = new File("/models/difftest/without_pmid17_annotation.ttl");
		String model_id = models.importModelToDatabase(f1, false);
		
		//File f1 = new File("/models/difftest/with_pmid17_annotation.ttl");
		
	}

}
