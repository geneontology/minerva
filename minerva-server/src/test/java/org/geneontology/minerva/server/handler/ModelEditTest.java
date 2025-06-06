package org.geneontology.minerva.server.handler;

import org.apache.commons.io.IOUtils;
import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.server.handler.M3BatchHandler.*;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse.MetaResponse;
import org.geneontology.minerva.server.inferences.InferenceProviderCreator;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;
import owltools.io.ParserWrapper;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.geneontology.minerva.BlazegraphOntologyManager.in_taxon;
import static org.junit.Assert.*;

public class ModelEditTest {

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

        models = new UndoAwareMolecularModelManager(tbox, curieHandler, modelIdPrefix, folder.newFile().getAbsolutePath(), null, go_lego_journal_file, true);
        InferenceProviderCreator ipc = null;
        handler = new JsonOrJsonpBatchHandler(models, "development", ipc,
                Collections.<OWLObjectProperty>emptySet(), (ExternalLookupService) null);
        StringWriter writer = new StringWriter();
        IOUtils.copy(ModelEditTest.class.getResourceAsStream("/edit-test/5437882f00000024"), writer, "utf-8");
        models.importModel(writer.toString());
        StringWriter writer2 = new StringWriter();
        IOUtils.copy(ModelEditTest.class.getResourceAsStream("/edit-test/test-model-taxon-annotations.ttl"), writer2, "utf-8");
        models.importModel(writer2.toString());
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

        r = BatchTestTools.addEdge(modelId, "VAR1", "RO:0002333", individualId);
        batch.add(r);

        executeBatch(batch);
    }

    @Test
    public void testTaxonAnnotationMaintenance() throws Exception {
        List<M3Request> batch = new ArrayList<>();
        M3Request r;
        IRI human = IRI.create("http://purl.obolibrary.org/obo/NCBITaxon_9606");
        IRI boar = IRI.create("http://purl.obolibrary.org/obo/NCBITaxon_9823");
        IRI modelIRI = IRI.create("http://model.geneontology.org/62183af000000536");
        final ModelContainer model = models.getModel(modelIRI);
        assertNotNull(model);
        Set<IRI> previousTaxonIRIs = model.getAboxOntology().getAnnotations().stream()
                .filter(a -> a.getProperty().equals(in_taxon))
                .map(a -> a.getValue().asIRI().get())
                .collect(Collectors.toSet());
        assertTrue(previousTaxonIRIs.contains(human));
        // there is a model annotation but no data about boar in the model
        assertTrue(previousTaxonIRIs.contains(boar));
        assertEquals(2, previousTaxonIRIs.size());
        // make any sort of change: create new individual
        r = BatchTestTools.addIndividual(modelIRI.toString(), "GO:0003674");
        r.arguments.assignToVariable = "VAR1";
        batch.add(r);
        executeBatch(batch);
        Set<IRI> newTaxonIRIs = model.getAboxOntology().getAnnotations().stream()
                .filter(a -> a.getProperty().equals(in_taxon))
                .map(a -> a.getValue().asIRI().get())
                .collect(Collectors.toSet());
        assertTrue(newTaxonIRIs.contains(human));
        assertFalse(newTaxonIRIs.contains(boar));
        // We should not pick up the worm taxon from the unused class declaration
        assertEquals(1, newTaxonIRIs.size());
    }

    @Test
    public void testModelReset() throws Exception {

        final String modelId = "http://model.geneontology.org/5437882f00000024";
        M3Request r;

        models.saveModel(models.getModel(IRI.create(modelId)));
        //cache a version of the initial model.
        OWLOntologyManager man1 = OWLManager.createOWLOntologyManager();
        OWLOntology startModel = man1.copyOntology(models.getModelAbox(IRI.create(modelId)), OntologyCopy.DEEP);
        Set<OWLAxiom> start_axioms = startModel.getABoxAxioms(null);
        // get model, check that the model is indicated as not modified
        M3BatchResponse resp1 = BatchTestTools.getModel(handler, modelId, false);
        assertFalse(resp1.data.modifiedFlag);

        // modify model
        // create new individual
        r = BatchTestTools.addIndividual(modelId, "GO:0003674");
        M3BatchResponse resp2 = executeBatch(r);
        // check that response indicates modified
        assertTrue(resp2.data.modifiedFlag);

        //compare and show they are different
        OWLOntologyManager man2 = OWLManager.createOWLOntologyManager();
        OWLOntology midModel = man2.copyOntology(models.getModelAbox(IRI.create(modelId)), OntologyCopy.DEEP);
        Set<OWLAxiom> mid_axioms = midModel.getABoxAxioms(null);
        assertFalse(mid_axioms.equals(start_axioms));

        //test diff command for comparison
        r = new M3Request();
        r.entity = Entity.model;
        r.operation = Operation.diffModel;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        M3BatchResponse diffresp = executeBatch(r);
        String dr = diffresp.data.diffResult;
        assertFalse(dr.equals("Ontologies are identical\n"));

        //now reset the model
        r = new M3Request();
        r.entity = Entity.model;
        r.operation = Operation.resetModel;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        M3BatchResponse resp3 = executeBatch(r);

        // check that response indicates not modified
        assertFalse(resp3.data.modifiedFlag);

        //compare and show they are the same
        OWLOntologyManager man3 = OWLManager.createOWLOntologyManager();
        OWLOntology endModel = man3.copyOntology(models.getModelAbox(IRI.create(modelId)), OntologyCopy.DEEP);
        Set<OWLAxiom> end_axioms = endModel.getABoxAxioms(null);
        assertTrue(start_axioms.equals(end_axioms));

        //test diff command for comparison
        r = new M3Request();
        r.entity = Entity.model;
        r.operation = Operation.diffModel;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        diffresp = executeBatch(r);
        dr = diffresp.data.diffResult;
        assertTrue(dr.equals("Ontologies are identical\n"));
    }

    @Test
    public void testModifiedFlag() throws Exception {

        final String modelId = "http://model.geneontology.org/5437882f00000024";
        final String curie = curieHandler.getCuri(IRI.create(modelId));
        M3Request r;

        models.saveModel(models.getModel(IRI.create(modelId)));

        // get meta, check that the model shows up as not modified
        MetaResponse meta1 = BatchTestTools.getMeta(handler);
        assertNotNull(meta1.modelsReadOnly);
        assertFalse(meta1.modelsReadOnly.isEmpty());
        for (Entry<String, Map<String, Object>> entity : meta1.modelsReadOnly.entrySet()) {
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
        for (Entry<String, Map<String, Object>> entity : readOnly.entrySet()) {
            boolean modifiedFlag = (Boolean) entity.getValue().get("modified-p");
            if (entity.getKey().equals(curie)) {
                assertTrue(modifiedFlag);
            } else {
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
        for (Entry<String, Map<String, Object>> entity : modelsModified3.entrySet()) {
            boolean modifiedFlag = (Boolean) entity.getValue().get("modified-p");
            assertFalse(modifiedFlag);
        }
    }

    private M3BatchResponse executeBatch(M3Request r) {
        return executeBatch(Collections.singletonList(r));
    }

    private M3BatchResponse executeBatch(List<M3Request> batch) {
        M3BatchResponse response = handler.m3Batch("test-user", Collections.emptySet(), "test-intention", "foo-packet-id",
                batch.toArray(new M3Request[batch.size()]), false, true);
        assertEquals("test-user", response.uid);
        assertEquals("test-intention", response.intention);
        //assertEquals(response.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response.messageType);
        return response;
    }
}
