package org.geneontology.minerva.server.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.server.handler.M3BatchHandler.Entity;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3Argument;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse.MetaResponse;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3Request;
import org.geneontology.minerva.server.handler.M3BatchHandler.Operation;
import org.geneontology.minerva.server.inferences.InferenceProviderCreator;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;
import owltools.io.CatalogXmlIRIMapper;

public class ModelEditTest {

	private static CurieHandler curieHandler = null;
	private static JsonOrJsonpBatchHandler handler = null;
	private static UndoAwareMolecularModelManager models = null;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ParserWrapper pw = new ParserWrapper();

		// if available, set catalog
        String envCatalog = System.getenv().get("GENEONTOLOGY_CATALOG");
        if (envCatalog != null) {
        	pw.addIRIMapper(new CatalogXmlIRIMapper(envCatalog));
        }

		init(pw);
	}
	
	static void init(ParserWrapper pw) throws OWLOntologyCreationException, IOException {
		final OWLGraphWrapper graph = pw.parseToOWLGraph("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl");
		
		// curie handler
		final String modelIdcurie = "gomodel";
		final String modelIdPrefix = "http://model.geneontology.org/";
		final CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap(modelIdcurie, modelIdPrefix));
		curieHandler = new MappedCurieHandler(DefaultCurieHandler.getMappings(), localMappings);
		
		models = new UndoAwareMolecularModelManager(graph, curieHandler, modelIdPrefix);
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
	
	@Rule
	public TemporaryFolder folder= new TemporaryFolder();
	
	@Before
	public void before() throws Exception {
		// clean up potential pre models
		if (models != null) {
			models.dispose();
		}
		// create temp folder
		File createdFolder= folder.newFolder();

		// copy test models into temp folder
		File sourceModels = new File("src/test/resources/edit-test").getCanonicalFile();
		File[] modelFiles = sourceModels.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return StringUtils.isAlphanumeric(name);
			}
		});
		for (File modelFile : modelFiles) {
			FileUtils.copyFileToDirectory(modelFile, createdFolder, false);	
		}
		// set path to OWL path
		models.setPathToOWLFiles(createdFolder.getAbsolutePath());
	}
	
	@Test
	public void testAddEdgeAsBatch() throws Exception {
		List<M3Request> batch = new ArrayList<>();
		M3Request r;
		
		final String individualId = "http://model.geneontology.org/5437882f00000024/5437882f0000032";
		final IRI individualIRI = IRI.create(individualId);
		final String individualIdCurie = curieHandler.getCuri(individualIRI);
		final String modelId = "http://model.geneontology.org/5437882f00000024";
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
		
		
		// create new individual
		r = BatchTestTools.addIndividual(modelId, "GO:0003674");
		r.arguments.assignToVariable = "VAR1";
		batch.add(r);
		
		// add link to existing individual (converted from old model)
		r = BatchTestTools.addEdge(modelId, "VAR1", "BFO:0000050", individualId);
		batch.add(r);
		
		executeBatch(batch);
	}
	
	@Test
	public void testModifiedFlag() throws Exception {
		final String modelId = "http://model.geneontology.org/5437882f00000024";
		final String curie = curieHandler.getCuri(IRI.create(modelId));
		M3Request r;
		
		// get meta, check that the model shows up as not modified
		MetaResponse meta1 = BatchTestTools.getMeta(handler);
		assertNotNull(meta1.modelsReadOnly);
		assertFalse(meta1.modelsReadOnly.isEmpty());
		for(Entry<String, Map<String, Object>> entity : meta1.modelsReadOnly.entrySet()) {
			boolean modifiedFlag = (Boolean) entity.getValue().get("modified-p");
			assertFalse(modifiedFlag);
		}
		
		// get model, check that the model indicated as not modified
		M3BatchResponse resp1 = BatchTestTools.getModel(handler, modelId, false);
		assertFalse(resp1.data.modifiedFlag);
		
		// modify model
		// create new individual
		r = BatchTestTools.addIndividual(modelId, "GO:0003674");
		M3BatchResponse resp2 = executeBatch(r);
		
		// check that response indicates modified
		assertTrue(resp2.data.modifiedFlag);
		
		// get meta, check that the model shows up as modified
		MetaResponse meta2 = BatchTestTools.getMeta(handler);
		assertNotNull(meta2.modelsReadOnly);
		Map<String, Map<String, Object>> readOnly = (Map<String, Map<String, Object>>) meta2.modelsReadOnly;
		assertFalse(readOnly.isEmpty());
		for(Entry<String, Map<String, Object>> entity : readOnly.entrySet()) {
			boolean modifiedFlag = (Boolean) entity.getValue().get("modified-p");
			if(entity.getKey().equals(curie)) {
				assertTrue(modifiedFlag);
			}
			else {
				assertFalse(modifiedFlag);
			}
		}
		
		// save
		r = new M3Request();
		r.entity = Entity.model;
		r.operation = Operation.storeModel;
		r.arguments = new M3Argument();
		r.arguments.modelId = modelId;
		M3BatchResponse resp3 = executeBatch(r);
		
		// check that response indicates not modified
		assertFalse(resp3.data.modifiedFlag);
		
		// get meta, check that the model shows up as not modified
		MetaResponse meta3 = BatchTestTools.getMeta(handler);
		assertNotNull(meta3.modelsReadOnly);
		Map<String, Map<String, Object>> modelsModified3 = (Map<String, Map<String, Object>>) meta3.modelsReadOnly;
		assertFalse(modelsModified3.isEmpty());
		for(Entry<String, Map<String, Object>> entity : modelsModified3.entrySet()) {
			boolean modifiedFlag = (Boolean) entity.getValue().get("modified-p");
			assertFalse(modifiedFlag);
		}
	}
	
	private M3BatchResponse executeBatch(M3Request r) {
		return executeBatch(Collections.singletonList(r));
	}
	
	private M3BatchResponse executeBatch(List<M3Request> batch) {
		M3BatchResponse response = handler.m3Batch("test-user", "test-intention", "foo-packet-id",
				batch.toArray(new M3Request[batch.size()]), false, true);
		assertEquals("test-user", response.uid);
		assertEquals("test-intention", response.intention);
		assertEquals(response.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response.messageType);
		return response;
	}
}
