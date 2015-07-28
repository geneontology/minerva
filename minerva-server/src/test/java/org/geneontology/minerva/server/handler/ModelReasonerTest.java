package org.geneontology.minerva.server.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.json.JsonOwlIndividual;
import org.geneontology.minerva.server.external.ExternalLookupService;
import org.geneontology.minerva.server.handler.M3BatchHandler.Entity;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3Argument;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3Request;
import org.geneontology.minerva.server.handler.M3BatchHandler.Operation;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class ModelReasonerTest {

	private static CurieHandler curieHandler = null;
	private static JsonOrJsonpBatchHandler handler = null;
	private static UndoAwareMolecularModelManager models = null;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		init(new ParserWrapper());
	}
	
	static void init(ParserWrapper pw) throws OWLOntologyCreationException, IOException {
		final OWLGraphWrapper graph = pw.parseToOWLGraph("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl");
		
		// curie handler
		final String modelIdcurie = "gomodel";
		final String modelIdPrefix = "http://model.geneontology.org/";
		final CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap(modelIdcurie, modelIdPrefix));
		curieHandler = new MappedCurieHandler(DefaultCurieHandler.getMappings(), localMappings);
		
		OWLReasonerFactory rf = new ElkReasonerFactory();
		models = new UndoAwareMolecularModelManager(graph, rf, curieHandler, modelIdPrefix);
		boolean useReasoner = true;
		boolean useModelReasoner = false;
		handler = new JsonOrJsonpBatchHandler(models, useReasoner, useModelReasoner,
				Collections.<OWLObjectProperty>emptySet(), (ExternalLookupService) null);
		models.setPathToOWLFiles("src/test/resources/reasoner-test");
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
	public void testReasoner() throws Exception {
		List<M3Request> batch = new ArrayList<>();
		M3Request r;
		
		final String individualId = "http://model.geneontology.org/5525a0fc00000001/5525a0fc0000023";
		final IRI individualIRI = IRI.create(individualId);
		final String individualIdCurie = curieHandler.getCuri(individualIRI);
		final String modelId = "http://model.geneontology.org/5525a0fc00000001";
		final ModelContainer model = models.getModel(IRI.create(modelId));
		assertNotNull(model);
		boolean found = false;
		boolean foundCurie = false;
		Set<OWLNamedIndividual> individuals = model.getAboxOntology().getIndividualsInSignature();
		for (OWLNamedIndividual individual : individuals) {
			if (individualIRI.equals(individual.getIRI())) {
				found = true;
				foundCurie = individualIdCurie.equals(curieHandler.getCuri(individual.getIRI()));
			}
		}
		assertTrue(found);
		assertTrue(foundCurie);
		
		
		// get model
		r = new M3Request();
		r.entity = Entity.model;
		r.operation = Operation.get;
		r.arguments = new M3Argument();
		r.arguments.modelId = modelId;
		batch.add(r);
		
		M3BatchResponse response = executeBatch(batch);
		JsonOwlIndividual[] responseIndividuals = BatchTestTools.responseIndividuals(response);
		JsonOwlIndividual targetIndividual = null;
		for (JsonOwlIndividual individual : responseIndividuals) {
			if (individualIdCurie.equals(individual.id)) {
				targetIndividual = individual;
				break;
			}
		}
		assertNotNull(targetIndividual);
		assertNotNull(targetIndividual.inferredType);
		assertEquals("Expected two inferences", 2, targetIndividual.inferredType.length);
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
