package org.geneontology.minerva.server.handler;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.server.external.ExternalLookupService;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3Request;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class ModelEditTest {

	private final static CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
	private static JsonOrJsonpBatchHandler handler = null;
	private static UndoAwareMolecularModelManager models = null;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		init(new ParserWrapper());
	}
	
	static void init(ParserWrapper pw) throws OWLOntologyCreationException, IOException {
		final OWLGraphWrapper graph = pw.parseToOWLGraph("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl");
		OWLReasonerFactory rf = new ElkReasonerFactory();
		models = new UndoAwareMolecularModelManager(graph, rf, curieHandler,
				"http://model.geneontology.org/", "gomodel:");
		boolean useReasoner = false;
		boolean useModelReasoner = false;
		handler = new JsonOrJsonpBatchHandler(models, useReasoner, useModelReasoner,
				Collections.<OWLObjectProperty>emptySet(), (ExternalLookupService) null);
		models.setPathToOWLFiles("src/test/resources/edit-test");
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
	public void testAddEdgeAsBatch() throws Exception {
		List<M3Request> batch = new ArrayList<>();
		M3Request r;
		
		final String individualId = "http://model.geneontology.org/5437882f00000024/5437882f0000032";
		final String modelId = "http://model.geneontology.org/5437882f00000024";
		final ModelContainer model = models.getModel(modelId);
		assertNotNull(model);
		boolean found = false;
		Set<OWLNamedIndividual> individuals = model.getAboxOntology().getIndividualsInSignature();
		for (OWLNamedIndividual individual : individuals) {
			String curi = curieHandler.getCuri(individual);
			if (curi.equals(individualId)) {
				found = true;
			}
		}
		assertTrue(found);
		
		
		// create new individual
		r = BatchTestTools.addIndividual(modelId, "GO:0003674");
		r.arguments.assignToVariable = "VAR1";
		batch.add(r);
		
		// add link to existing individual (converted from old model)
		r = BatchTestTools.addEdge(modelId, "VAR1", "BFO:0000050", individualId);
		batch.add(r);
		
		executeBatch(batch);
	}
	
	private M3BatchResponse executeBatch(List<M3Request> batch) {
		M3BatchResponse response = handler.m3Batch("test-user", "test-intention", "foo-packet-id",
				batch.toArray(new M3Request[batch.size()]), true);
		assertEquals("test-user", response.uid);
		assertEquals("test-intention", response.intention);
		assertEquals(response.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response.messageType);
		return response;
	}
}
