package org.geneontology.minerva.server.handler;

import org.geneontology.minerva.MinervaOWLGraphWrapper;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.json.*;
import org.geneontology.minerva.json.JsonOwlObject.JsonOwlObjectType;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.geneontology.minerva.lookup.TableLookupService;
import org.geneontology.minerva.server.StartUpTool;
import org.geneontology.minerva.server.handler.M3BatchHandler.*;
import org.geneontology.minerva.server.inferences.CachingInferenceProviderCreatorImpl;
import org.geneontology.minerva.server.inferences.InferenceProviderCreator;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.parameters.Imports;
import owltools.io.ParserWrapper;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

@SuppressWarnings("unchecked")
public class BatchModelHandlerTest {

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    private static CurieHandler curieHandler = null;
    private static JsonOrJsonpBatchHandler handler = null;
    private static UndoAwareMolecularModelManager models = null;
    private static Set<OWLObjectProperty> importantRelations = null;
    private final static DateGenerator dateGenerator = new DateGenerator();
    static final String ontology_journal_file = "/tmp/test-go-lego-blazegraph.jnl";
    static final String uid = "test-user";
    static final Set<String> providedBy = Collections.singleton("test-provider");
    static final String intention = "test-intention";
    private static final String packetId = "foo-packet-id";

    private static ExternalLookupService lookupService;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        init(new ParserWrapper());
    }

    static void init(ParserWrapper pw) throws OWLOntologyCreationException, IOException, UnknownIdentifierException {
        final MinervaOWLGraphWrapper graph = pw.parseToOWLGraph("src/test/resources/go-lego-minimal.owl");
        final OWLObjectProperty legorelParent = StartUpTool.getRelation("http://purl.obolibrary.org/obo/LEGOREL_0000000", graph);
        assertNotNull(legorelParent);
        importantRelations = StartUpTool.getAssertedSubProperties(legorelParent, graph);
        assertFalse(importantRelations.isEmpty());
        // curie handler
        final String modelIdcurie = "gomodel";
        final String modelIdPrefix = "http://model.geneontology.org/";
        final CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap(modelIdcurie, modelIdPrefix));
        curieHandler = new MappedCurieHandler(DefaultCurieHandler.loadDefaultMappings(), localMappings);
        InferenceProviderCreator ipc = CachingInferenceProviderCreatorImpl.createElk(false, null);
        models = new UndoAwareMolecularModelManager(graph.getSourceOntology(), curieHandler, modelIdPrefix, folder.newFile().getAbsolutePath(), null, ontology_journal_file, true);
        lookupService = createTestProteins(curieHandler);
        handler = new JsonOrJsonpBatchHandler(models, "development", ipc, importantRelations, lookupService) {

            @Override
            protected String generateDateString() {
                // hook for overriding the date generation with a custom counter
                if (dateGenerator.useCounter) {
                    int count = dateGenerator.counter;
                    dateGenerator.counter += 1;
                    return Integer.toString(count);
                }
                return super.generateDateString();
            }
        };
        JsonOrJsonpBatchHandler.VALIDATE_BEFORE_SAVE = true;
    }

    private static ExternalLookupService createTestProteins(CurieHandler curieHandler) throws UnknownIdentifierException {
        List<LookupEntry> testEntries = new ArrayList<LookupEntry>();
        testEntries.add(new LookupEntry(curieHandler.getIRI("UniProtKB:P0000"),
                "P0000", "protein", "fake-taxon-id", null));
        testEntries.add(new LookupEntry(curieHandler.getIRI("UniProtKB:P0001"),
                "P0001", "protein", "fake-taxon-id", null));
        testEntries.add(new LookupEntry(curieHandler.getIRI("UniProtKB:P0002"),
                "P0002", "protein", "fake-taxon-id", null));
        testEntries.add(new LookupEntry(curieHandler.getIRI("UniProtKB:P0003"),
                "P0003", "protein", "fake-taxon-id", null));
        return new TableLookupService(testEntries);
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
    public void testTypeOperations() throws Exception {
        final String modelId = generateBlankModel();

        List<M3Request> batch = new ArrayList<M3Request>();
        M3Request r = BatchTestTools.addIndividual(modelId, "GO:0006915"); // apoptotic process
        r.arguments.assignToVariable = "i1";
        r.arguments.values = new JsonAnnotation[2];
        r.arguments.values[0] = JsonTools.create(AnnotationShorthand.comment, "comment 1", null);
        r.arguments.values[1] = JsonTools.create(AnnotationShorthand.comment, "comment 2", null);
        batch.add(r);

        r = new M3Request();
        r.entity = Entity.individual;
        r.operation = Operation.addType;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        r.arguments.individual = "i1";
        r.arguments.expressions = new JsonOwlObject[1];
        r.arguments.expressions[0] = BatchTestTools.createSvf("BFO:0000066", "GO:0005623"); // occurs_in, cell
        batch.add(r);

        r = new M3Request();
        r.entity = Entity.individual;
        r.operation = Operation.addType;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        r.arguments.individual = "i1";
        r.arguments.expressions = new JsonOwlObject[1];
        r.arguments.expressions[0] = new JsonOwlObject();
        r.arguments.expressions[0].type = JsonOwlObjectType.SomeValueFrom;
        r.arguments.expressions[0].property = new JsonOwlObject();
        r.arguments.expressions[0].property.type = JsonOwlObjectType.ObjectProperty;
        r.arguments.expressions[0].property.id = "RO:0002333"; // enabled_by
        // "GO:0043234 and (('has part' some UniProtKB:P0002) OR ('has part' some UniProtKB:P0003))";
        r.arguments.expressions[0].filler = createComplexExpr();
        batch.add(r);

        r = BatchTestTools.addIndividual(modelId, "GO:0043276",
                BatchTestTools.createSvf("RO:0002333", "GO:0043234")); // enabled_by
        batch.add(r);

        M3BatchResponse resp2 = executeBatch(batch, false);
        String individual1 = null;
        String individual2 = null;
        JsonOwlIndividual[] iObjs = BatchTestTools.responseIndividuals(resp2);
        assertEquals(2, iObjs.length);
        for (JsonOwlIndividual iObj : iObjs) {
            String clsId = null;
            for (JsonOwlObject currentType : iObj.type) {
                if (currentType.type == JsonOwlObjectType.Class) {
                    clsId = currentType.id;
                }
            }
            if (clsId.contains("6915")) {
                individual1 = iObj.id;
                assertEquals(3, iObj.type.length);
            } else {
                individual2 = iObj.id;
                assertEquals(2, iObj.type.length);
            }
        }
        assertNotNull(individual1);
        assertNotNull(individual2);

        // create fact
        final M3Request r3 = new M3Request();
        r3.entity = Entity.edge;
        r3.operation = Operation.add;
        r3.arguments = new M3Argument();
        r3.arguments.modelId = modelId;
        r3.arguments.subject = individual1;
        r3.arguments.object = individual2;
        r3.arguments.predicate = "BFO:0000050"; // part_of

        execute(r3, false);

        // delete complex expression type
        final M3Request r4 = new M3Request();
        r4.entity = Entity.individual;
        r4.operation = Operation.removeType;
        r4.arguments = new M3Argument();
        r4.arguments.modelId = modelId;
        r4.arguments.individual = individual1;
        r4.arguments.expressions = new JsonOwlObject[1];
        r4.arguments.expressions[0] = new JsonOwlObject();
        r4.arguments.expressions[0].type = JsonOwlObjectType.SomeValueFrom;
        r4.arguments.expressions[0].property = new JsonOwlObject();
        r4.arguments.expressions[0].property.type = JsonOwlObjectType.ObjectProperty;
        r4.arguments.expressions[0].property.id = "RO:0002333"; // enabled_by
        // "GO:0043234 and (('has part' some UniProtKB:P0002) OR ('has part' some UniProtKB:P0003))";
        r4.arguments.expressions[0].filler = createComplexExpr();

        M3BatchResponse resp4 = execute(r4, false);
        JsonOwlIndividual[] iObjs4 = BatchTestTools.responseIndividuals(resp4);
        assertEquals(1, iObjs4.length);
        JsonOwlObject[] types = iObjs4[0].type;
        assertEquals(2, types.length);
    }

    private static JsonOwlObject createComplexExpr() {
        // "GO:0043234 and (('has part' some UniProtKB:P0002) OR ('has part' some UniProtKB:P0003))";
        JsonOwlObject expr = new JsonOwlObject();
        expr.type = JsonOwlObjectType.IntersectionOf;
        expr.expressions = new JsonOwlObject[2];

        // GO:0043234
        expr.expressions[0] = new JsonOwlObject();
        expr.expressions[0].type = JsonOwlObjectType.Class;
        expr.expressions[0].id = "GO:0043234";

        // OR
        expr.expressions[1] = new JsonOwlObject();
        expr.expressions[1].type = JsonOwlObjectType.UnionOf;
        expr.expressions[1].expressions = new JsonOwlObject[2];

        //'has part' some UniProtKB:P0002
        expr.expressions[1].expressions[0] = BatchTestTools.createSvf("BFO:0000051", "UniProtKB:P0002");

        // 'has part' some UniProtKB:P0003
        expr.expressions[1].expressions[1] = BatchTestTools.createSvf("BFO:0000051", "UniProtKB:P0003");

        return expr;
    }


    @Test
    public void testAddIndividual() throws Exception {
        final String modelId = generateBlankModel();

        // create one individuals
        final M3Request r = new M3Request();
        r.entity = Entity.individual;
        r.operation = Operation.add;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        r.arguments.expressions = new JsonOwlObject[1];
        r.arguments.expressions[0] = new JsonOwlObject();
        r.arguments.expressions[0].type = JsonOwlObjectType.Class;
        r.arguments.expressions[0].id = "GO:0006915"; // apoptotic process

        M3BatchResponse resp = execute(r, false);
        assertEquals(resp.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, resp.messageType);
        JsonOwlIndividual[] iObjs = BatchTestTools.responseIndividuals(resp);
        assertEquals(1, iObjs.length);
    }

    @Test
    public void testModelAnnotations() throws Exception {
        final String modelId = generateBlankModel();

        final JsonAnnotation[] annotations1 = getModelAnnotations(modelId);
        // creation date
        // user id
        // providedBy
        // model state
        assertEquals(4, annotations1.length);

        // create annotations
        final M3Request r1 = new M3Request();
        r1.entity = Entity.model;
        r1.operation = Operation.addAnnotation;
        r1.arguments = new M3Argument();
        r1.arguments.modelId = modelId;

        r1.arguments.values = new JsonAnnotation[2];
        r1.arguments.values[0] = new JsonAnnotation();
        r1.arguments.values[0].key = AnnotationShorthand.comment.name();
        r1.arguments.values[0].value = "comment 1";
        r1.arguments.values[1] = new JsonAnnotation();
        r1.arguments.values[1].key = AnnotationShorthand.comment.name();
        r1.arguments.values[1].value = "comment 2";

        execute(r1, false);

        final JsonAnnotation[] annotations2 = getModelAnnotations(modelId);
        assertNotNull(annotations2);
        assertEquals(6, annotations2.length);


        // remove one annotation
        final M3Request r2 = new M3Request();
        r2.entity = Entity.model;
        r2.operation = Operation.removeAnnotation;
        r2.arguments = new M3Argument();
        r2.arguments.modelId = modelId;

        r2.arguments.values = new JsonAnnotation[1];
        r2.arguments.values[0] = new JsonAnnotation();
        r2.arguments.values[0].key = AnnotationShorthand.comment.name();
        r2.arguments.values[0].value = "comment 1";

        execute(r2, false);

        final JsonAnnotation[] annotations3 = getModelAnnotations(modelId);
        assertNotNull(annotations3);
        assertEquals(5, annotations3.length);
    }

    @Test
    public void testModelAnnotationsTemplate() throws Exception {
        final String modelId = generateBlankModel();
        final JsonAnnotation[] annotations1 = getModelAnnotations(modelId);
        // creation date
        // user id
        // providedBy
        // model state
        assertEquals(4, annotations1.length);

        // create template annotation
        final M3Request r1 = new M3Request();
        r1.entity = Entity.model;
        r1.operation = Operation.addAnnotation;
        r1.arguments = new M3Argument();
        r1.arguments.modelId = modelId;

        r1.arguments.values = new JsonAnnotation[1];
        r1.arguments.values[0] = new JsonAnnotation();
        r1.arguments.values[0].key = AnnotationShorthand.templatestate.getShorthand();
        r1.arguments.values[0].value = Boolean.TRUE.toString();

        execute(r1, false);

        final JsonAnnotation[] annotations2 = getModelAnnotations(modelId);
        assertNotNull(annotations2);
        assertEquals(5, annotations2.length);

        // remove one annotation
        final M3Request r2 = new M3Request();
        r2.entity = Entity.model;
        r2.operation = Operation.removeAnnotation;
        r2.arguments = new M3Argument();
        r2.arguments.modelId = modelId;

        r2.arguments.values = new JsonAnnotation[1];
        r2.arguments.values[0] = new JsonAnnotation();
        r2.arguments.values[0].key = AnnotationShorthand.modelstate.getShorthand();
        r2.arguments.values[0].value = "development";

        final M3Request r3 = new M3Request();
        r3.entity = Entity.model;
        r3.operation = Operation.addAnnotation;
        r3.arguments = new M3Argument();
        r3.arguments.modelId = modelId;

        r3.arguments.values = new JsonAnnotation[1];
        r3.arguments.values[0] = new JsonAnnotation();
        r3.arguments.values[0].key = AnnotationShorthand.modelstate.getShorthand();
        r3.arguments.values[0].value = "review";

        executeBatch(Arrays.asList(r2, r3), false);

        final JsonAnnotation[] annotations3 = getModelAnnotations(modelId);
        assertNotNull(annotations3);
        assertEquals(5, annotations3.length);
        String foundModelState = null;
        for (JsonAnnotation annotation : annotations3) {
            if (AnnotationShorthand.modelstate.getShorthand().equals(annotation.key)) {
                assertNull("Multiple model states are not allowed", foundModelState);
                foundModelState = annotation.value;
            }
        }
        assertEquals("review", foundModelState);
    }

    @Test
    public void testMultipleMeta() throws Exception {
        //models.dispose();

        // get meta
        final M3Request r = new M3Request();
        r.entity = Entity.meta;
        r.operation = Operation.get;

        M3BatchResponse response = execute(r, false);
        final JsonRelationInfo[] relations = BatchTestTools.responseRelations(response);
        final OWLObjectProperty part_of = OWLManager.getOWLDataFactory().getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/BFO_0000050"));
        assertNotNull(part_of);
        final String partOfJsonId = models.getCuriHandler().getCuri(part_of);
        boolean hasPartOf = false;
        for (JsonRelationInfo info : relations) {
            String id = info.id;
            assertNotNull(id);
            if (partOfJsonId.equals(id)) {
                assertEquals(true, info.relevant);
                hasPartOf = true;
            }
        }
        assertTrue(relations.length > 20);
        assertTrue(hasPartOf);

        final JsonEvidenceInfo[] evidences = BatchTestTools.responseEvidences(response);
        assertTrue(evidences.length > 100);

        final Map<String, List<JsonAnnotation>> modelIds = BatchTestTools.responseModelsMeta(response);
        assertFalse(modelIds.size() == 0);
    }

    @Test
    public void testFailOnMetaAndChange() throws Exception {
//		models.dispose();

        final String modelId = generateBlankModel();

        final List<M3Request> batch1 = new ArrayList<M3Request>();
        batch1.add(BatchTestTools.addIndividual(modelId, "GO:0008150")); // biological process

        M3Request r = new M3Request();
        r.entity = Entity.meta;
        r.operation = Operation.get;
        batch1.add(r);

        M3BatchResponse response = handler.m3Batch(uid, providedBy, intention, packetId, batch1.toArray(new M3Request[batch1.size()]), false, true);
        assertEquals(uid, response.uid);
        assertEquals(intention, response.intention);

        assertEquals(M3BatchResponse.MESSAGE_TYPE_ERROR, response.messageType);
    }

    @Test
    public void testSaveAsNonMeta() throws Exception {
        //models.dispose();

        final String modelId = generateBlankModel();

        final List<M3Request> batch1 = new ArrayList<M3Request>();
        batch1.add(BatchTestTools.addIndividual(modelId, "GO:0008150")); // biological process


        M3Request r = new M3Request();
        r.entity = Entity.model;
        r.operation = Operation.addAnnotation;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.title, "foo");
        batch1.add(r);

        r = new M3Request();
        r.entity = Entity.model;
        r.operation = Operation.storeModel;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        batch1.add(r);

        M3BatchResponse response = executeBatch(batch1, false);
        JsonOwlIndividual[] responseIndividuals = BatchTestTools.responseIndividuals(response);
        assertEquals(1, responseIndividuals.length);
    }

    @Test
    public void testAddBlankModel() throws Exception {
//		models.dispose();

        final M3Request r1 = new M3Request();
        r1.entity = Entity.model;
        r1.operation = Operation.add;
        r1.arguments = new M3Argument();

        M3BatchResponse response1 = execute(r1, false);
        final String modelId1 = BatchTestTools.responseId(response1);

        final M3Request r2 = new M3Request();
        r2.entity = Entity.model;
        r2.operation = Operation.add;
        r2.arguments = new M3Argument();

        M3BatchResponse response2 = execute(r2, false);
        final String modelId2 = BatchTestTools.responseId(response2);

        assertNotEquals(modelId1, modelId2);

        final M3Request batch3 = new M3Request();
        batch3.entity = Entity.model;
        batch3.operation = Operation.add;
        batch3.arguments = new M3Argument();

        M3BatchResponse response3 = execute(batch3, false);
        final String modelId3 = BatchTestTools.responseId(response3);

        assertNotEquals(modelId1, modelId3);
        assertNotEquals(modelId2, modelId3);
    }

    @Test
    public void testDelete() throws Exception {
        //models.dispose();

        final String modelId = generateBlankModel();

        // create
        final M3Request r1 = BatchTestTools.addIndividual(modelId, "GO:0008104", // protein localization
                BatchTestTools.createSvf("RO:0002333", "UniProtKB:P0000"), // enabled_by
                BatchTestTools.createSvf("BFO:0000050", "GO:0006915")); // part_of apoptotic process

        final M3BatchResponse response1 = execute(r1, false);

        JsonOwlIndividual[] iObjs1 = BatchTestTools.responseIndividuals(response1);
        assertEquals(1, iObjs1.length);
        JsonOwlIndividual individual1 = iObjs1[0];
        assertNotNull(individual1);
        assertNotNull(individual1.id);

        JsonOwlObject[] types1 = individual1.type;
        assertEquals(3, types1.length);
        String apopId = null;
        for (JsonOwlObject e : types1) {
            if (JsonOwlObjectType.SomeValueFrom == e.type) {
                if (e.filler.id.equals("GO:0006915")) {
                    apopId = e.filler.id;
                    break;
                }
            }
        }
        assertNotNull(apopId);

        // delete
        final M3Request r2 = new M3Request();
        r2.entity = Entity.individual;
        r2.operation = Operation.removeType;
        r2.arguments = new M3Argument();
        r2.arguments.modelId = modelId;
        r2.arguments.individual = individual1.id;

        r2.arguments.expressions = new JsonOwlObject[1];
        r2.arguments.expressions[0] = BatchTestTools.createSvf("BFO:0000050", apopId); // part_of

        final M3BatchResponse response2 = execute(r2, false);

        JsonOwlIndividual[] iObjs2 = BatchTestTools.responseIndividuals(response2);
        assertEquals(1, iObjs2.length);
        JsonOwlIndividual individual2 = iObjs2[0];
        assertNotNull(individual2);
        JsonOwlObject[] types2 = individual2.type;
        assertEquals(2, types2.length);
    }

    @Test
    public void testDeleteEdge() throws Exception {
        //models.dispose();
        final String modelId = generateBlankModel();

        // setup model
        // simple three individuals (mf, bp, gene) with two facts: bp -p-> mf, mf -enabled_by-> gene
        final List<M3Request> batch1 = new ArrayList<M3Request>();

        // activity/mf
        M3Request r = BatchTestTools.addIndividual(modelId, "GO:0003674"); // molecular function
        r.arguments.assignToVariable = "mf";
        batch1.add(r);

        // process
        r = BatchTestTools.addIndividual(modelId, "GO:0008150"); // biological process
        r.arguments.assignToVariable = "bp";
        batch1.add(r);

        // gene
        r = BatchTestTools.addIndividual(modelId, "UniProtKB:P0000"); // gene
        r.arguments.assignToVariable = "gene";
        batch1.add(r);

        // activity -> process
        r = BatchTestTools.addEdge(modelId, "mf", "BFO:0000050", "bp"); // part_of
        batch1.add(r); // part_of

        // mf -enabled_by-> gene
        r = BatchTestTools.addEdge(modelId, "mf", "RO:0002333", "gene"); // enabled_by
        batch1.add(r); // part_of

        final M3BatchResponse response1 = executeBatch(batch1, false);
        JsonOwlIndividual[] iObjs1 = BatchTestTools.responseIndividuals(response1);
        assertEquals(3, iObjs1.length);

        String mf = null;
        String bp = null;
        for (JsonOwlIndividual iObj : iObjs1) {
            String id = iObj.id;
            assertNotNull(id);
            JsonOwlObject[] types = iObj.type;
            assertNotNull(types);
            assertEquals(1, types.length);
            JsonOwlObject typeObj = types[0];
            String typeId = typeObj.id;
            assertNotNull(typeId);
            if ("GO:0003674".equals(typeId)) {
                mf = id;
            } else if ("GO:0008150".equals(typeId)) {
                bp = id;
            }
        }
        assertNotNull(mf);
        assertNotNull(bp);


        final List<M3Request> batch2 = new ArrayList<M3Request>();
        r = BatchTestTools.deleteEdge(modelId, mf, "BFO:0000050", bp);
        batch2.add(r);

        final M3BatchResponse response2 = executeBatch(batch2, false);
        assertEquals(M3BatchResponse.SIGNAL_MERGE, response2.signal);
        JsonOwlIndividual[] iObjs2 = BatchTestTools.responseIndividuals(response2);
        assertEquals(2, iObjs2.length);
    }

    @Test
    public void testModelCopy() {
        final List<M3Request> batch1 = new ArrayList<M3Request>();
        final String sourceModelId = generateBlankModel();
        // evidence1
        M3Request r = BatchTestTools.addIndividual(sourceModelId, "ECO:0000000"); // evidence from ECO
        r.arguments.assignToVariable = "evidence-var1";
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.source, "PMID:000000");
        batch1.add(r);
        // evidence2
        r = BatchTestTools.addIndividual(sourceModelId, "ECO:0000001"); // evidence from ECO
        r.arguments.assignToVariable = "evidence-var2";
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.source, "PMID:000001");
        batch1.add(r);
        // evidence3
        r = BatchTestTools.addIndividual(sourceModelId, "ECO:0000002"); // evidence from ECO
        r.arguments.assignToVariable = "evidence-var3";
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.source, "PMID:000002");
        batch1.add(r);
        r = BatchTestTools.addIndividual(sourceModelId, "GO:0003674"); // molecular function
        r.arguments.assignToVariable = "mf";
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.evidence, "evidence-var1");
        batch1.add(r);
        r = BatchTestTools.addIndividual(sourceModelId, "GO:0008150"); // biological process
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.evidence, "evidence-var3");
        r.arguments.assignToVariable = "bp";
        batch1.add(r);
        // activity -> process
        r = BatchTestTools.addEdge(sourceModelId, "mf", "BFO:0000050", "bp"); // part_of
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.evidence, "evidence-var2");
        batch1.add(r); // part_of
        final M3BatchResponse response1 = executeBatch(batch1, false);
        System.err.println(response1.message);
        Arrays.stream(response1.data.individuals).forEach(i -> System.err.println(i.id));

        M3Request r2 = BatchTestTools.copyModel(sourceModelId);
        M3BatchResponse response2 = execute(r2, false);
        Arrays.stream(response2.data.individuals).forEach(i -> System.err.println(i.id));
        Arrays.stream(response2.data.facts).forEach(f -> System.err.println(f.subject + " " + f.property + " " + f.object));
        Arrays.stream(response2.data.annotations).forEach(a -> System.err.println(a.key + " " + a.value));
    }

    @Test
    public void testDeleteEvidenceIndividuals() throws Exception {
        //models.dispose();
        final String modelId = generateBlankModel();

        // setup model
        // simple four individuals (mf, bp, 2 evidences) with a fact in between bp and mf
        final List<M3Request> batch1 = new ArrayList<M3Request>();

        // evidence1
        M3Request r = BatchTestTools.addIndividual(modelId, "ECO:0000000"); // evidence from ECO
        r.arguments.assignToVariable = "evidence-var1";
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.source, "PMID:000000");
        batch1.add(r);

        // evidence2
        r = BatchTestTools.addIndividual(modelId, "ECO:0000001"); // evidence from ECO
        r.arguments.assignToVariable = "evidence-var2";
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.source, "PMID:000001");
        batch1.add(r);

        // evidence3
        r = BatchTestTools.addIndividual(modelId, "ECO:0000002"); // evidence from ECO
        r.arguments.assignToVariable = "evidence-var3";
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.source, "PMID:000002");
        batch1.add(r);

        // activity/mf
        r = BatchTestTools.addIndividual(modelId, "GO:0003674"); // molecular function
        r.arguments.assignToVariable = "mf";
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.evidence, "evidence-var1");
        batch1.add(r);

        // process
        r = BatchTestTools.addIndividual(modelId, "GO:0008150"); // biological process
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.evidence, "evidence-var3");
        r.arguments.assignToVariable = "bp";
        batch1.add(r);

        // activity -> process
        r = BatchTestTools.addEdge(modelId, "mf", "BFO:0000050", "bp"); // part_of
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.evidence, "evidence-var2");
        batch1.add(r); // part_of

        final M3BatchResponse response1 = executeBatch(batch1, false);

        //run diff to show changes
        //test diff command for comparison
        M3Request dr = new M3Request();
        dr.entity = Entity.model;
        dr.operation = Operation.diffModel;
        dr.arguments = new M3Argument();
        dr.arguments.modelId = modelId;
        M3BatchResponse diffresp = execute(dr, false);
        String diff = diffresp.data.diffResult;
        assertFalse(diff.equals("Ontologies are identical\n"));


        // find individuals
        JsonOwlIndividual[] iObjs1 = BatchTestTools.responseIndividuals(response1);
        assertEquals(5, iObjs1.length);
        String evidence1 = null;
        String evidence2 = null;
        String evidence3 = null;
        String mf = null;
        String bp = null;
        for (JsonOwlIndividual iObj : iObjs1) {
            String id = iObj.id;
            assertNotNull(id);
            JsonOwlObject[] types = iObj.type;
            assertNotNull(types);
            assertEquals(1, types.length);
            JsonOwlObject typeObj = types[0];
            String typeId = typeObj.id;
            assertNotNull(typeId);
            if ("GO:0003674".equals(typeId)) {
                mf = id;
            } else if ("GO:0008150".equals(typeId)) {
                bp = id;
            } else if ("ECO:0000000".equals(typeId)) {
                evidence1 = id;
            } else if ("ECO:0000001".equals(typeId)) {
                evidence2 = id;
            } else if ("ECO:0000002".equals(typeId)) {
                evidence3 = id;
            }
        }
        assertNotNull(evidence1);
        assertNotNull(evidence2);
        assertNotNull(evidence3);
        assertNotNull(mf);
        assertNotNull(bp);

        // one edge
        JsonOwlFact[] facts1 = BatchTestTools.responseFacts(response1);
        assertEquals(1, facts1.length);
        assertEquals(4, facts1[0].annotations.length); // evidence, date, contributor, provider

        // remove fact evidence
        final List<M3Request> batch2 = new ArrayList<M3Request>();
        r = BatchTestTools.removeIndividual(modelId, evidence2);
        batch2.add(r);

        executeBatch(batch2, false);

        final M3BatchResponse response3 = checkCounts(modelId, 4, 1);
        JsonOwlFact[] factsObjs = BatchTestTools.responseFacts(response3);
        assertEquals(3, factsObjs[0].annotations.length); // date and contributor remain

        // delete bp evidence instance
        final List<M3Request> batch4 = new ArrayList<M3Request>();
        r = BatchTestTools.removeIndividual(modelId, evidence3);
        batch4.add(r);

        executeBatch(batch4, false);

        final M3BatchResponse response5 = checkCounts(modelId, 3, 1);
        JsonOwlIndividual[] indivdualObjs5 = BatchTestTools.responseIndividuals(response5);
        boolean found = false;
        for (JsonOwlIndividual iObj : indivdualObjs5) {
            String id = iObj.id;
            assertNotNull(id);
            JsonOwlObject[] types = iObj.type;
            assertNotNull(types);
            assertEquals(1, types.length);
            JsonOwlObject typeObj = types[0];
            String typeId = typeObj.id;
            assertNotNull(typeId);
            if ("GO:0008150".equals(typeId)) {
                found = true;
                assertTrue(iObj.annotations.length == 3); // date and contributor and provider remain
            }
        }
        assertTrue(found);


        // delete mf instance -> delete also mf evidence instance and fact
        final List<M3Request> batch6 = new ArrayList<M3Request>();
        r = BatchTestTools.removeIndividual(modelId, mf);
        batch6.add(r);

        executeBatch(batch6, false);

        M3BatchResponse response7 = checkCounts(modelId, 1, 0);
        JsonOwlIndividual[] iObjs7 = BatchTestTools.responseIndividuals(response7);
        assertEquals(bp, iObjs7[0].id);
    }

    //FIXME @Test
    public void testInconsistentModel() throws Exception {
        //models.dispose();

        final String modelId = generateBlankModel();

        // create
        final M3Request r = BatchTestTools.addIndividual(modelId, "GO:0009653", // anatomical structure morphogenesis
                BatchTestTools.createClass("GO:0048856")); // anatomical structure development

        final M3BatchResponse response = execute(r, true);
        assertTrue(response.isReasoned);
        Boolean inconsistentFlag = BatchTestTools.responseInconsistent(response);
        assertEquals(Boolean.TRUE, inconsistentFlag);
    }

    //FIXME @Test
    public void testInferencesRedundant() throws Exception {
        //models.dispose();
        final String modelId = generateBlankModel();

        // GO:0009826 ! unidimensional cell growth
        // GO:0000902 ! cell morphogenesis
        // should infer only one type: 'unidimensional cell growth'
        // 'cell morphogenesis' is a super-class and redundant

        // create
        final M3Request r = BatchTestTools.addIndividual(modelId, "GO:0000902", // cell morphogenesis
                BatchTestTools.createClass("GO:0009826")); // unidimensional cell growth

        final M3BatchResponse response = execute(r, true);
        assertTrue(response.isReasoned);
        assertNull("Model should not be inconsistent", BatchTestTools.responseInconsistent(response));
        JsonOwlIndividual[] inferred = BatchTestTools.responseIndividuals(response);
        assertNotNull(inferred);
        assertEquals(1, inferred.length);
        JsonOwlIndividual inferredData = inferred[0];
        JsonOwlObject[] types = inferredData.inferredType;
        assertEquals(1, types.length);
        JsonOwlObject type = types[0];
        assertEquals(JsonOwlObjectType.Class, type.type);
        assertEquals("GO:0009826", type.id);
    }

    //FIXME @Test
    public void testTrivialInferences() throws Exception {
        //models.dispose();

        final String modelId = generateBlankModel();
        // create
        final M3Request r = BatchTestTools.addIndividual(modelId, "GO:0051231"); // spindle elongation

        final M3BatchResponse response = execute(r, true);
        assertTrue(response.isReasoned);
        assertNull("Model should not be inconsistent", BatchTestTools.responseInconsistent(response));
        JsonOwlIndividual[] inferred = BatchTestTools.responseIndividuals(response);
        assertNotNull(inferred);
        assertEquals(1, inferred.length);
        JsonOwlIndividual inferredData = inferred[0];
        assertNull(inferredData.inferredType);
    }

    //FIXME @Test
    public void testInferencesAdditional() throws Exception {
        //models.dispose();

        final String modelId = generateBlankModel();

        // GO:0051231 ! spindle elongation
        // part_of GO:0000278 ! mitotic cell cycle
        // should infer one new type: GO:0000022 ! mitotic spindle elongation

        // create
        final M3Request r = BatchTestTools.addIndividual(modelId, "GO:0051231", // spindle elongation
                BatchTestTools.createSvf("BFO:0000050", "GO:0000278")); // part_of, mitotic cell cycle

        final M3BatchResponse response = execute(r, true);
        assertTrue(response.isReasoned);
        assertNull("Model should not be inconsistent", BatchTestTools.responseInconsistent(response));
        JsonOwlIndividual[] inferred = BatchTestTools.responseIndividuals(response);
        assertNotNull(inferred);
        assertEquals(1, inferred.length);
        JsonOwlIndividual inferredData = inferred[0];
        JsonOwlObject[] types = inferredData.inferredType;
        assertEquals(1, types.length);
        JsonOwlObject type = types[0];
        assertEquals(JsonOwlObjectType.Class, type.type);
        assertEquals("GO:0000022", type.id);
    }

    @Test
    public void testValidationBeforeSave() throws Exception {
        assertTrue(JsonOrJsonpBatchHandler.VALIDATE_BEFORE_SAVE);
        //models.dispose();

        final String modelId = generateBlankModel();

        // try to save
        M3Request[] batch = new M3Request[1];
        batch[0] = new M3Request();
        batch[0].entity = Entity.model;
        batch[0].operation = Operation.storeModel;
        batch[0].arguments = new M3Argument();
        batch[0].arguments.modelId = modelId;
        M3BatchResponse resp1 = handler.m3Batch(uid, providedBy, intention, packetId, batch, false, true);
        assertEquals("This operation must fail as the model has no title or individuals", M3BatchResponse.MESSAGE_TYPE_ERROR, resp1.messageType);
        assertNotNull(resp1.commentary);
        assertTrue(resp1.commentary.contains("title"));
    }

    @Test
    public void testPrivileged() throws Exception {
        M3Request[] batch = new M3Request[1];
        batch[0] = new M3Request();
        batch[0].entity = Entity.model;
        batch[0].operation = Operation.add;
        M3BatchResponse resp1 = handler.m3Batch(uid, providedBy, intention, packetId, batch, false, false);
        assertEquals(M3BatchResponse.MESSAGE_TYPE_ERROR, resp1.messageType);
        assertTrue(resp1.message.contains("Insufficient"));
    }

    //FIXME @Test
    public void testExportLegacy() throws Exception {
        final String modelId = generateBlankModel();

        // create
        final M3Request r1 = BatchTestTools.addIndividual(modelId, "GO:0008104", // protein localization
                BatchTestTools.createSvf("RO:0002333", "UniProtKB:P0000"), // enabled_by
                BatchTestTools.createSvf("BFO:0000050", "GO:0006915")); // part_of

        execute(r1, false);


        final M3Request r2 = new M3Request();
        r2.operation = Operation.exportModelLegacy;
        r2.entity = Entity.model;
        r2.arguments = new M3Argument();
        r2.arguments.modelId = modelId;
//		batch2.arguments.format = "gpad"; // optional, default is gaf 

        final M3BatchResponse response2 = execute(r2, false);
        String exportString = BatchTestTools.responseExport(response2);
//		System.out.println("----------------");
//		System.out.println(exportString);
//		System.out.println("----------------");
        assertNotNull(exportString);
    }

    //FIXME @Test
    public void testUndoRedo() throws Exception {
        final String modelId = generateBlankModel();

        // create
        final M3Request r1 = BatchTestTools.addIndividual(modelId, "GO:0008104", // protein localization
                BatchTestTools.createSvf("RO:0002333", "UniProtKB:P0000"), // enabled_by
                BatchTestTools.createSvf("BFO:0000050", "GO:0006915")); // part_of apoptotic process

        final M3BatchResponse response1 = execute(r1, false);
        JsonOwlIndividual[] iObjs1 = BatchTestTools.responseIndividuals(response1);
        assertEquals(1, iObjs1.length);
        JsonOwlIndividual individual1 = iObjs1[0];
        assertNotNull(individual1);
        final String individualId = individual1.id;
        assertNotNull(individualId);

        JsonOwlObject[] types1 = individual1.type;
        assertEquals(3, types1.length);
        String apopId = null;
        for (JsonOwlObject e : types1) {
            if (JsonOwlObjectType.SomeValueFrom == e.type) {
                if (e.filler.id.equals("GO:0006915")) {
                    apopId = e.filler.id;
                    break;
                }
            }
        }
        assertNotNull(apopId);

        // check undo redo list
        final M3Request r2 = new M3Request();
        r2.entity = Entity.model;
        r2.operation = Operation.getUndoRedo;
        r2.arguments = new M3Argument();
        r2.arguments.modelId = modelId;
        final M3BatchResponse response2 = execute(r2, false);
        List<Object> undo2 = (List<Object>) response2.data.undo;
        List<Object> redo2 = (List<Object>) response2.data.redo;
        assertTrue(undo2.size() > 1);
        assertTrue(redo2.isEmpty());

        // delete
        final M3Request r3 = new M3Request();
        r3.entity = Entity.individual;
        r3.operation = Operation.removeType;
        r3.arguments = new M3Argument();
        r3.arguments.modelId = modelId;
        r3.arguments.individual = individualId;
        r3.arguments.expressions = new JsonOwlObject[]{BatchTestTools.createSvf("BFO:0000050", apopId)};

        final M3BatchResponse response3 = execute(r3, false);
        JsonOwlIndividual[] iObjs3 = BatchTestTools.responseIndividuals(response3);
        assertEquals(1, iObjs3.length);
        JsonOwlIndividual individual3 = iObjs3[0];
        assertNotNull(individual3);
        JsonOwlObject[] types3 = individual3.type;
        assertEquals(2, types3.length);

        // check undo redo list
        final M3Request r4 = new M3Request();
        r4.entity = Entity.model;
        r4.operation = Operation.getUndoRedo;
        r4.arguments = new M3Argument();
        r4.arguments.modelId = modelId;

        final M3BatchResponse response4 = execute(r4, false);
        List<Object> undo4 = (List<Object>) response4.data.undo;
        List<Object> redo4 = (List<Object>) response4.data.redo;
        assertTrue(undo4.size() > 1);
        assertTrue(redo4.isEmpty());

        // undo
        final M3Request r5 = new M3Request();
        r5.entity = Entity.model;
        r5.operation = Operation.undo;
        r5.arguments = new M3Argument();
        r5.arguments.modelId = modelId;

        execute(r5, false);

        // check undo redo list
        final M3Request r6 = new M3Request();
        r6.entity = Entity.model;
        r6.operation = Operation.getUndoRedo;
        r6.arguments = new M3Argument();
        r6.arguments.modelId = modelId;

        final M3BatchResponse response6 = execute(r6, false);
        List<Object> undo6 = (List<Object>) response6.data.undo;
        List<Object> redo6 = (List<Object>) response6.data.redo;
        assertTrue(undo6.size() > 1);
        assertTrue(redo6.size() == 1);

    }

    //FIXME @Test
    public void testAllIndividualEvidenceDelete() throws Exception {
        /*
         * create three individuals, two facts and two evidence individuals
         */
        // blank model
        final String modelId = generateBlankModel();
        final List<M3Request> batch1 = new ArrayList<M3Request>();

        // evidence1
        M3Request r = BatchTestTools.addIndividual(modelId, "ECO:0000000"); // evidence from ECO
        r.arguments.assignToVariable = "evidence-var1";
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.source, "PMID:000000");
        batch1.add(r);

        // evidence2
        r = BatchTestTools.addIndividual(modelId, "ECO:0000001"); // evidence from ECO
        r.arguments.assignToVariable = "evidence-var2";
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.source, "PMID:000001");
        batch1.add(r);

        // activity/mf
        r = BatchTestTools.addIndividual(modelId, "GO:0003674"); // molecular function
        r.arguments.assignToVariable = "mf";
        batch1.add(r);

        // process
        r = BatchTestTools.addIndividual(modelId, "GO:0008150"); // biological process
        r.arguments.assignToVariable = "bp";
        batch1.add(r);

        // location/cc
        r = BatchTestTools.addIndividual(modelId, "GO:0005575"); // cellular component
        r.arguments.assignToVariable = "cc";
        batch1.add(r);

        // activity -> process
        r = BatchTestTools.addEdge(modelId, "mf", "BFO:0000050", "bp"); // part_of
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.evidence, "evidence-var1");
        batch1.add(r); // part_of

        // activity -> cc
        r = BatchTestTools.addEdge(modelId, "mf", "BFO:0000066", "cc"); // occurs_in
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.evidence, "evidence-var2");
        batch1.add(r);

        final M3BatchResponse response1 = executeBatch(batch1, false);

        // find individuals
        JsonOwlIndividual[] iObjs1 = BatchTestTools.responseIndividuals(response1);
        assertEquals(5, iObjs1.length);
        String evidence1 = null;
        String evidence2 = null;
        String mf = null;
        String bp = null;
        String cc = null;
        for (JsonOwlIndividual iObj : iObjs1) {
            String id = iObj.id;
            assertNotNull(id);
            JsonOwlObject[] types = iObj.type;
            assertNotNull(types);
            assertEquals(1, types.length);
            JsonOwlObject typeObj = types[0];
            String typeId = typeObj.id;
            assertNotNull(typeId);
            if ("GO:0003674".equals(typeId)) {
                mf = id;
            } else if ("GO:0008150".equals(typeId)) {
                bp = id;
            } else if ("GO:0005575".equals(typeId)) {
                cc = id;
            } else if ("ECO:0000000".equals(typeId)) {
                evidence1 = id;
            } else if ("ECO:0000001".equals(typeId)) {
                evidence2 = id;
            }
        }
        assertNotNull(evidence1);
        assertNotNull(evidence2);
        assertNotNull(mf);
        assertNotNull(bp);
        assertNotNull(cc);

        // two edges
        JsonOwlFact[] facts1 = BatchTestTools.responseFacts(response1);
        assertEquals(2, facts1.length);

        /*
         * delete one fact and expect that the associated evidence is also deleted
         */
        // delete: mf -part_of-> bp
        r = BatchTestTools.deleteEdge(modelId, mf, "BFO:0000050", bp);
        final M3BatchResponse response2 = execute(r, false);

        JsonOwlIndividual[] iObjs2 = BatchTestTools.responseIndividuals(response2);
        assertEquals(4, iObjs2.length); // should return the whole model, due to the delete of the evidence!

        // get the whole model to check global counts
        checkCounts(modelId, 4, 1);

        /*
         * delete one individuals of an fact and expect a cascading delete, including the evidence
         */
        r = BatchTestTools.removeIndividual(modelId, cc);
        M3BatchResponse response3 = execute(r, false);

        JsonOwlIndividual[] iObjs3 = BatchTestTools.responseIndividuals(response3);
        assertEquals(2, iObjs3.length);
        JsonOwlFact[] facts3 = BatchTestTools.responseFacts(response3);
        assertEquals(0, facts3.length);

        checkCounts(modelId, 2, 0);
    }

    private M3BatchResponse checkCounts(String modelId, int individuals, int facts) {
        final M3BatchResponse response = BatchTestTools.getModel(handler, modelId, false);
        assertEquals(response.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response.messageType);
        JsonOwlIndividual[] iObjs = BatchTestTools.responseIndividuals(response);
        assertEquals(individuals, iObjs.length);
        JsonOwlFact[] factsObjs = BatchTestTools.responseFacts(response);
        assertEquals(facts, factsObjs.length);
        return response;
    }

    private JsonAnnotation[] getModelAnnotations(String modelId) {
        final M3BatchResponse response = BatchTestTools.getModel(handler, modelId, false);
        return response.data.annotations;
    }

    @Test
    public void testAllIndividualUseCase() throws Exception {
        /*
         * Create a full set of individuals for an activity diagram of a gene.
         */
        // blank model
        final String modelId = generateBlankModel();
        List<M3Request> batch = new ArrayList<M3Request>();

        // evidence
        M3Request r = BatchTestTools.addIndividual(modelId, "ECO:0000000"); // evidence from ECO
        r.arguments.assignToVariable = "evidence-var";
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.source, "PMID:000000");
        batch.add(r);

        // activity/mf
        r = BatchTestTools.addIndividual(modelId, "GO:0003674"); // molecular function
        r.arguments.assignToVariable = "mf";
        batch.add(r);

        // process
        r = BatchTestTools.addIndividual(modelId, "GO:0008150"); // biological process
        r.arguments.assignToVariable = "bp";
        batch.add(r);

        // location/cc
        r = BatchTestTools.addIndividual(modelId, "GO:0005575"); // cellular component
        r.arguments.assignToVariable = "cc";
        batch.add(r);

        // gene
        r = BatchTestTools.addIndividual(modelId, "MGI:000000"); // fake gene (not in the test set of known genes!)
        r.arguments.assignToVariable = "gene";
        batch.add(r);

        // relations
        // activity -> gene
        r = BatchTestTools.addEdge(modelId, "mf", "RO:0002333", "gene"); // enabled_by
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.evidence, "evidence-var");
        batch.add(r);

        // activity -> process
        r = BatchTestTools.addEdge(modelId, "mf", "BFO:0000050", "bp"); // part_of
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.evidence, "evidence-var");
        batch.add(r); // part_of

        // activity -> cc
        r = BatchTestTools.addEdge(modelId, "mf", "BFO:0000066", "cc"); // occurs_in
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.evidence, "evidence-var");
        batch.add(r);

        /*
         * Test for annoying work-around until the external validation is more stable
         */
        boolean defaultIdPolicy = handler.CHECK_LITERAL_IDENTIFIERS;
        try {
            handler.CHECK_LITERAL_IDENTIFIERS = false;
            executeBatch(batch, false);
        } finally {
            handler.CHECK_LITERAL_IDENTIFIERS = defaultIdPolicy;
        }
    }

    //FIXME @Test
    public void testVariables1() throws Exception {
        /*
         * TASK: create three individuals (mf,bp,cc) and a directed relation
         * between the new instances
         */
        final String modelId = generateBlankModel();
        final M3Request[] batch = new M3Request[5];
        batch[0] = new M3Request();
        batch[0].entity = Entity.individual;
        batch[0].operation = Operation.add;
        batch[0].arguments = new M3Argument();
        batch[0].arguments.modelId = modelId;
        BatchTestTools.setExpressionClass(batch[0].arguments, "GO:0003674"); // molecular function
        batch[0].arguments.assignToVariable = "mf";

        batch[1] = new M3Request();
        batch[1].entity = Entity.individual;
        batch[1].operation = Operation.add;
        batch[1].arguments = new M3Argument();
        batch[1].arguments.modelId = modelId;
        BatchTestTools.setExpressionClass(batch[1].arguments, "GO:0008150"); // biological process
        batch[1].arguments.assignToVariable = "bp";

        batch[2] = new M3Request();
        batch[2].entity = Entity.edge;
        batch[2].operation = Operation.add;
        batch[2].arguments = new M3Argument();
        batch[2].arguments.modelId = modelId;
        batch[2].arguments.subject = "mf";
        batch[2].arguments.predicate = "BFO:0000050"; // part_of
        batch[2].arguments.object = "bp";

        batch[3] = new M3Request();
        batch[3].entity = Entity.individual;
        batch[3].operation = Operation.add;
        batch[3].arguments = new M3Argument();
        batch[3].arguments.modelId = modelId;
        BatchTestTools.setExpressionClass(batch[3].arguments, "GO:0005575"); // cellular component
        batch[3].arguments.assignToVariable = "cc";

        batch[4] = new M3Request();
        batch[4].entity = Entity.edge;
        batch[4].operation = Operation.add;
        batch[4].arguments = new M3Argument();
        batch[4].arguments.modelId = modelId;
        batch[4].arguments.subject = "mf";
        batch[4].arguments.predicate = "BFO:0000066"; // occurs_in
        batch[4].arguments.object = "cc";

        M3BatchResponse response = handler.m3Batch(uid, providedBy, intention, packetId, batch, false, true);
        assertEquals(uid, response.uid);
        assertEquals(intention, response.intention);
        assertEquals(response.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response.messageType);

        JsonOwlIndividual[] iObjs = BatchTestTools.responseIndividuals(response);
        assertEquals(3, iObjs.length);
        String mf = null;
        String bp = null;
        String cc = null;
        for (JsonOwlIndividual iObj : iObjs) {
            String id = iObj.id;
            assertNotNull(id);
            JsonOwlObject[] types = iObj.type;
            assertNotNull(types);
            assertEquals(1, types.length);
            JsonOwlObject typeObj = types[0];
            String typeId = typeObj.id;
            assertNotNull(typeId);
            if ("GO:0003674".equals(typeId)) {
                mf = id;
            } else if ("GO:0008150".equals(typeId)) {
                bp = id;
            } else if ("GO:0005575".equals(typeId)) {
                cc = id;
            }
        }
        assertNotNull(mf);
        assertNotNull(bp);
        assertNotNull(cc);

        JsonOwlFact[] facts = BatchTestTools.responseFacts(response);
        assertEquals(2, facts.length);
        boolean mfbp = false;
        boolean mfcc = false;
        for (JsonOwlFact fact : facts) {
            String subject = fact.subject;
            String property = fact.property;
            String object = fact.object;
            assertNotNull(subject);
            assertNotNull(property);
            assertNotNull(object);
            if (mf.equals(subject) && "BFO:0000050".equals(property) && bp.equals(object)) {
                mfbp = true;
            }
            if (mf.equals(subject) && "BFO:0000066".equals(property) && cc.equals(object)) {
                mfcc = true;
            }
        }
        assertTrue(mfbp);
        assertTrue(mfcc);
    }

    //FIXME @Test
    public void testVariables2() throws Exception {
        /*
         * TASK: try to use an undefined variable
         */
        final String modelId = generateBlankModel();
        final M3Request[] batch = new M3Request[2];
        batch[0] = new M3Request();
        batch[0].entity = Entity.individual;
        batch[0].operation = Operation.add;
        batch[0].arguments = new M3Argument();
        batch[0].arguments.modelId = modelId;
        BatchTestTools.setExpressionClass(batch[0].arguments, "GO:0003674"); // molecular function
        batch[0].arguments.assignToVariable = "mf";

        batch[1] = new M3Request();
        batch[1].entity = Entity.edge;
        batch[1].operation = Operation.add;
        batch[1].arguments = new M3Argument();
        batch[1].arguments.modelId = modelId;
        batch[1].arguments.subject = "mf";
        batch[1].arguments.predicate = "BFO:0000050"; // part_of
        batch[1].arguments.object = "foo";

        M3BatchResponse response = handler.m3Batch(uid, providedBy, intention, packetId, batch, false, true);
        assertEquals(uid, response.uid);
        assertEquals(intention, response.intention);
        assertEquals("The operation should fail with an unknown identifier exception",
                M3BatchResponse.MESSAGE_TYPE_ERROR, response.messageType);
        assertTrue(response.message, response.message.contains("UnknownIdentifierException"));
        assertTrue(response.message, response.message.contains("foo")); // unknown
    }

    @Test
    public void testDeprecatedModel() throws Exception {
//		models.dispose();

        final String modelId1 = generateBlankModel();
        final String modelId2 = generateBlankModel();

        // add deprecated annotation to model 2
        final M3Request batch1 = new M3Request();
        batch1.entity = Entity.model;
        batch1.operation = Operation.addAnnotation;
        batch1.arguments = new M3Argument();
        batch1.arguments.modelId = modelId2;
        batch1.arguments.values = new JsonAnnotation[1];
        batch1.arguments.values[0] = new JsonAnnotation();
        batch1.arguments.values[0].key = AnnotationShorthand.deprecated.name();
        batch1.arguments.values[0].value = Boolean.TRUE.toString();

        execute(batch1, false);

        final M3Request batch2 = new M3Request();
        batch2.entity = Entity.meta;
        batch2.operation = Operation.get;

        final M3BatchResponse response2 = execute(batch2, false);

        Map<String, List<JsonAnnotation>> meta = BatchTestTools.responseModelsMeta(response2);
        //assertEquals(2, meta.size()); //FIXME should not reuse models after dispose; need to change these tests to make a fresh m3
        // model 1
        List<JsonAnnotation> modelData = meta.get(modelId1);
        assertNotNull(modelData);
        for (JsonAnnotation json : modelData) {
            if (json.key.equals(AnnotationShorthand.deprecated.name())) {
                fail("the model should not have a deprecation annotation");
            }
        }

        // model 2, deprecated
        modelData = meta.get(modelId2);
        assertNotNull(modelData);
        boolean found = false;
        for (JsonAnnotation json : modelData) {
            if (json.key.equals(AnnotationShorthand.deprecated.name())) {
                found = true;
                assertEquals("true", json.value);
            }
        }
        assertTrue("the model must have a deprecation annotation", found);

    }

    //FIXME @Test
    public void testAutoAnnotationsForAddType() throws Exception {
        /*
         * test that if a type is added or removed from an individual also
         * updates the contributes annotation
         */
        final String modelId = generateBlankModel();
        // create individual
        final M3Request[] batch1 = new M3Request[1];
        batch1[0] = new M3Request();
        batch1[0].entity = Entity.individual;
        batch1[0].operation = Operation.add;
        batch1[0].arguments = new M3Argument();
        batch1[0].arguments.modelId = modelId;
        BatchTestTools.setExpressionClass(batch1[0].arguments, "GO:0003674"); // molecular function

        String uid1 = "1";
        Set<String> providedBy1 = Collections.singleton("provider1");
        M3BatchResponse response1 = handler.m3Batch(uid1, providedBy1, intention, packetId, batch1, false, true);
        assertEquals(uid1, response1.uid);
        assertEquals(intention, response1.intention);
        assertEquals(response1.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response1.messageType);

        // find contributor
        JsonOwlIndividual[] individuals1 = BatchTestTools.responseIndividuals(response1);
        assertEquals(1, individuals1.length);

        final String id = individuals1[0].id;

        JsonAnnotation[] annotations1 = individuals1[0].annotations;
        assertEquals(2, annotations1.length);
        String contrib1 = null;
        for (JsonAnnotation annotation : annotations1) {
            if (AnnotationShorthand.contributor.name().equals(annotation.key)) {
                contrib1 = annotation.value;
            }
        }
        assertNotNull(contrib1);
        assertEquals(uid1, contrib1);

        // remove type
        final M3Request[] batch2 = new M3Request[1];
        batch2[0] = new M3Request();
        batch2[0].entity = Entity.individual;
        batch2[0].operation = Operation.removeType;
        batch2[0].arguments = new M3Argument();
        batch2[0].arguments.modelId = modelId;
        batch2[0].arguments.individual = id;
        BatchTestTools.setExpressionClass(batch2[0].arguments, "GO:0003674"); // molecular function

        String uid2 = "2";
        Set<String> providedBy2 = Collections.singleton("provider2");
        M3BatchResponse response2 = handler.m3Batch(uid2, providedBy2, intention, packetId, batch2, false, true);
        assertEquals(uid2, response2.uid);
        assertEquals(intention, response2.intention);
        assertEquals(response2.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response2.messageType);

        // find contributor and compare with prev
        JsonOwlIndividual[] individuals2 = BatchTestTools.responseIndividuals(response2);
        assertEquals(1, individuals2.length);

        JsonAnnotation[] annotations2 = individuals2[0].annotations;
        assertEquals(3, annotations2.length);
        Set<String> contribSet1 = new HashSet<String>();
        for (JsonAnnotation annotation : annotations2) {
            if (AnnotationShorthand.contributor.name().equals(annotation.key)) {
                contribSet1.add(annotation.value);
            }
        }
        assertEquals(2, contribSet1.size());
        assertTrue(contribSet1.contains(uid1));
        assertTrue(contribSet1.contains(uid2));

        // add type
        final M3Request[] batch3 = new M3Request[1];
        batch3[0] = new M3Request();
        batch3[0].entity = Entity.individual;
        batch3[0].operation = Operation.addType;
        batch3[0].arguments = new M3Argument();
        batch3[0].arguments.modelId = modelId;
        batch3[0].arguments.individual = id;
        BatchTestTools.setExpressionClass(batch3[0].arguments, "GO:0003674"); // molecular function

        String uid3 = "3";
        Set<String> providedBy3 = Collections.singleton("provider3");
        M3BatchResponse response3 = handler.m3Batch(uid3, providedBy3, intention, packetId, batch3, false, true);
        assertEquals(uid3, response3.uid);
        assertEquals(intention, response3.intention);
        assertEquals(response3.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response3.messageType);

        // find contributor and compare with prev
        JsonOwlIndividual[] individuals3 = BatchTestTools.responseIndividuals(response3);
        assertEquals(1, individuals3.length);

        JsonAnnotation[] annotations3 = individuals3[0].annotations;
        assertEquals(4, annotations3.length);
        Set<String> contribSet2 = new HashSet<String>();
        for (JsonAnnotation annotation : annotations3) {
            if (AnnotationShorthand.contributor.name().equals(annotation.key)) {
                contribSet2.add(annotation.value);
            }
        }
        assertEquals(3, contribSet2.size());
        assertTrue(contribSet2.contains(uid1));
        assertTrue(contribSet2.contains(uid2));
        assertTrue(contribSet2.contains(uid3));
    }

    static class DateGenerator {

        boolean useCounter = false;
        int counter = 0;
    }

    //FIXME @Test
    public void testUpdateDateAnnotation() throws Exception {
        /*
         * test that the last modification date is update for every change of an
         * individual or fact
         */
        try {
            dateGenerator.counter = 0;
            dateGenerator.useCounter = true;

            // test update with add/remove annotation of a fact
            final String modelId = generateBlankModel();

            // setup initial fact with two individuals
            final M3Request[] batch1 = new M3Request[3];
            batch1[0] = new M3Request();
            batch1[0].entity = Entity.individual;
            batch1[0].operation = Operation.add;
            batch1[0].arguments = new M3Argument();
            batch1[0].arguments.modelId = modelId;
            BatchTestTools.setExpressionClass(batch1[0].arguments, "GO:0003674"); // molecular function
            batch1[0].arguments.assignToVariable = "mf";

            batch1[1] = new M3Request();
            batch1[1].entity = Entity.individual;
            batch1[1].operation = Operation.add;
            batch1[1].arguments = new M3Argument();
            batch1[1].arguments.modelId = modelId;
            BatchTestTools.setExpressionClass(batch1[1].arguments, "GO:0008150"); // biological process
            batch1[1].arguments.assignToVariable = "bp";

            batch1[2] = new M3Request();
            batch1[2].entity = Entity.edge;
            batch1[2].operation = Operation.add;
            batch1[2].arguments = new M3Argument();
            batch1[2].arguments.modelId = modelId;
            batch1[2].arguments.subject = "mf";
            batch1[2].arguments.predicate = "BFO:0000050"; // part_of
            batch1[2].arguments.object = "bp";

            M3BatchResponse response1 = handler.m3Batch(uid, providedBy, intention, packetId, batch1, false, true);
            assertEquals(uid, response1.uid);
            assertEquals(intention, response1.intention);
            assertEquals(response1.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response1.messageType);

            // find fact and date annotation
            String prevDate = null;
            {
                JsonOwlFact[] responseFacts = BatchTestTools.responseFacts(response1);
                assertEquals(1, responseFacts.length);
                Set<String> dates = new HashSet<String>();
                for (JsonAnnotation ann : responseFacts[0].annotations) {
                    if (AnnotationShorthand.date.name().equals(ann.key)) {
                        dates.add(ann.value);
                    }
                }
                assertEquals(1, dates.size());
                prevDate = dates.iterator().next();
                assertNotNull(prevDate);
            }
            String mf = null;
            String bp = null;
            {
                JsonOwlIndividual[] responseIndividuals = BatchTestTools.responseIndividuals(response1);
                assertEquals(2, responseIndividuals.length);
                for (JsonOwlIndividual iObj : responseIndividuals) {
                    String id = iObj.id;
                    assertNotNull(id);
                    JsonOwlObject[] types = iObj.type;
                    assertNotNull(types);
                    assertEquals(1, types.length);
                    JsonOwlObject typeObj = types[0];
                    String typeId = typeObj.id;
                    assertNotNull(typeId);
                    if ("GO:0003674".equals(typeId)) {
                        mf = id;
                    } else if ("GO:0008150".equals(typeId)) {
                        bp = id;
                    }
                }
            }
            assertNotNull(mf);
            assertNotNull(bp);

            // add comment to fact
            final M3Request[] batch2 = new M3Request[1];
            batch2[0] = new M3Request();
            batch2[0].entity = Entity.edge;
            batch2[0].operation = Operation.addAnnotation;
            batch2[0].arguments = new M3Argument();
            batch2[0].arguments.modelId = modelId;
            batch2[0].arguments.subject = mf;
            batch2[0].arguments.predicate = "BFO:0000050"; // part_of
            batch2[0].arguments.object = bp;
            batch2[0].arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.comment, "foo");

            M3BatchResponse response2 = handler.m3Batch(uid, providedBy, intention, packetId, batch2, false, true);
            assertEquals(uid, response2.uid);
            assertEquals(intention, response2.intention);
            assertEquals(response2.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response2.messageType);

            // find fact and compare date with prev
            {
                JsonOwlFact[] responseFacts = BatchTestTools.responseFacts(response2);
                assertEquals(1, responseFacts.length);
                Set<String> dates = new HashSet<String>();
                for (JsonAnnotation ann : responseFacts[0].annotations) {
                    if (AnnotationShorthand.date.name().equals(ann.key)) {
                        dates.add(ann.value);
                    }
                }
                assertEquals(1, dates.size());
                String currentDate = dates.iterator().next();
                assertNotNull(currentDate);
                assertNotEquals(prevDate, currentDate);
                prevDate = currentDate;
            }

            // remove comment from fact
            final M3Request[] batch3 = new M3Request[1];
            batch3[0] = new M3Request();
            batch3[0].entity = Entity.edge;
            batch3[0].operation = Operation.removeAnnotation;
            batch3[0].arguments = new M3Argument();
            batch3[0].arguments.modelId = modelId;
            batch3[0].arguments.subject = mf;
            batch3[0].arguments.predicate = "BFO:0000050"; // part_of
            batch3[0].arguments.object = bp;
            batch3[0].arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.comment, "foo");

            M3BatchResponse response3 = handler.m3Batch(uid, providedBy, intention, packetId, batch3, false, true);
            assertEquals(uid, response3.uid);
            assertEquals(intention, response3.intention);
            assertEquals(response3.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response3.messageType);

            // find fact and compare date with prev
            {
                JsonOwlFact[] responseFacts = BatchTestTools.responseFacts(response3);
                assertEquals(1, responseFacts.length);
                Set<String> dates = new HashSet<String>();
                for (JsonAnnotation ann : responseFacts[0].annotations) {
                    if (AnnotationShorthand.date.name().equals(ann.key)) {
                        dates.add(ann.value);
                    }
                }
                assertEquals(1, dates.size());
                String currentDate = dates.iterator().next();
                assertNotNull(currentDate);
                assertNotEquals(prevDate, currentDate);
                prevDate = currentDate;
            }


            // test update with add/remove type of an individual
            // find individual and date annotation

            String individualId = null;
            {
                JsonOwlIndividual[] individuals1 = BatchTestTools.responseIndividuals(response1);
                assertEquals(2, individuals1.length);
                final Set<String> dates = new HashSet<String>();
                for (JsonOwlIndividual individual : individuals1) {
                    individualId = individual.id;
                    assertNotNull(individualId);
                    JsonOwlObject[] types = individual.type;
                    assertNotNull(types);
                    assertEquals(1, types.length);
                    JsonOwlObject typeObj = types[0];
                    String typeId = typeObj.id;
                    assertNotNull(typeId);
                    if ("GO:0003674".equals(typeId)) {
                        for (JsonAnnotation annotation : individual.annotations) {
                            if (AnnotationShorthand.date.name().equals(annotation.key)) {
                                dates.add(annotation.value);
                            }
                        }
                    }
                }
                assertEquals(1, dates.size());
                prevDate = dates.iterator().next();
                assertNotNull(prevDate);
            }

            // remove type
            final M3Request[] batch4 = new M3Request[1];
            batch4[0] = new M3Request();
            batch4[0].entity = Entity.individual;
            batch4[0].operation = Operation.removeType;
            batch4[0].arguments = new M3Argument();
            batch4[0].arguments.modelId = modelId;
            batch4[0].arguments.individual = individualId;
            BatchTestTools.setExpressionClass(batch4[0].arguments, "GO:0003674");

            M3BatchResponse response4 = handler.m3Batch(uid, providedBy, intention, packetId, batch4, false, true);
            assertEquals(uid, response4.uid);
            assertEquals(intention, response4.intention);
            assertEquals(response4.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response4.messageType);

            // find individual and compare date with prev
            {
                JsonOwlIndividual[] responseIndividuals = BatchTestTools.responseIndividuals(response4);
                assertEquals(1, responseIndividuals.length);
                final Set<String> dates = new HashSet<String>();
                for (JsonAnnotation annotation : responseIndividuals[0].annotations) {
                    if (AnnotationShorthand.date.name().equals(annotation.key)) {
                        dates.add(annotation.value);
                    }
                }
                assertEquals(1, dates.size());
                String currentDate = dates.iterator().next();
                assertNotNull(currentDate);
                assertNotEquals(prevDate, currentDate);
                prevDate = currentDate;
            }

            // add type
            final M3Request[] batch5 = new M3Request[1];
            batch5[0] = new M3Request();
            batch5[0].entity = Entity.individual;
            batch5[0].operation = Operation.addType;
            batch5[0].arguments = new M3Argument();
            batch5[0].arguments.modelId = modelId;
            batch5[0].arguments.individual = individualId;
            BatchTestTools.setExpressionClass(batch5[0].arguments, "GO:0003674");

            M3BatchResponse response5 = handler.m3Batch(uid, providedBy, intention, packetId, batch5, false, true);
            assertEquals(uid, response5.uid);
            assertEquals(intention, response5.intention);
            assertEquals(response5.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response5.messageType);

            // find individual and compare date with prev
            {
                JsonOwlIndividual[] responseIndividuals = BatchTestTools.responseIndividuals(response5);
                assertEquals(1, responseIndividuals.length);
                final Set<String> dates = new HashSet<String>();
                for (JsonAnnotation annotation : responseIndividuals[0].annotations) {
                    if (AnnotationShorthand.date.name().equals(annotation.key)) {
                        dates.add(annotation.value);
                    }
                }
                assertEquals(1, dates.size());
                assertEquals(1, dates.size());
                String currentDate = dates.iterator().next();
                assertNotNull(currentDate);
                assertNotEquals(prevDate, currentDate);
            }
        } finally {
            dateGenerator.useCounter = false;
        }
    }

    //FIXME @Test
    public void testUpdateDateAnnotationEvidence() throws Exception {
        try {
            dateGenerator.counter = 0;
            dateGenerator.useCounter = true;

            // test update with add/remove annotation of an evidence individuals
            final String modelId = generateBlankModel();

            // setup initial fact with two individuals
            List<M3Request> batch1 = new ArrayList<M3Request>();

            // evidence1
            M3Request r = BatchTestTools.addIndividual(modelId, "ECO:0000000"); // evidence from ECO
            r.arguments.assignToVariable = "evidence-var1";
            r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.source, "PMID:000000");
            batch1.add(r);

            // evidence2
            r = BatchTestTools.addIndividual(modelId, "ECO:0000001"); // evidence from ECO
            r.arguments.assignToVariable = "evidence-var2";
            r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.source, "PMID:000001");
            batch1.add(r);

            r = BatchTestTools.addIndividual(modelId, "GO:0003674"); // molecular function
            r.arguments.assignToVariable = "mf";
            r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.evidence, "evidence-var1");
            batch1.add(r);

            r = BatchTestTools.addIndividual(modelId, "GO:0008150"); // biological process
            r.arguments.assignToVariable = "bp";
            batch1.add(r);

            r = BatchTestTools.addEdge(modelId, "mf", "BFO:0000050", "bp"); // part_of
            r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.evidence, "evidence-var2");
            batch1.add(r);

            M3BatchResponse response1 = executeBatch(batch1, "FOO:1", false);

            // find all the individual ids
            // find date for mf
            String evidence1 = null;
            String evidence2 = null;
            String mf = null;
            String dateMf = null;
            String bp = null;
            {
                JsonOwlIndividual[] iObjs1 = BatchTestTools.responseIndividuals(response1);
                assertEquals(4, iObjs1.length);
                for (JsonOwlIndividual iObj : iObjs1) {
                    String id = iObj.id;
                    assertNotNull(id);
                    JsonOwlObject[] types = iObj.type;
                    assertNotNull(types);
                    assertEquals(1, types.length);
                    JsonOwlObject typeObj = types[0];
                    String typeId = typeObj.id;
                    assertNotNull(typeId);
                    if ("GO:0003674".equals(typeId)) {
                        mf = id;
                        for (JsonAnnotation ann : iObj.annotations) {
                            if (AnnotationShorthand.date.name().equals(ann.key)) {
                                dateMf = ann.value;
                            }
                        }
                    } else if ("GO:0008150".equals(typeId)) {
                        bp = id;
                    } else if ("ECO:0000000".equals(typeId)) {
                        evidence1 = id;
                    } else if ("ECO:0000001".equals(typeId)) {
                        evidence2 = id;
                    }
                }
                assertNotNull(evidence1);
                assertNotNull(evidence2);
                assertNotNull(mf);
                assertNotNull(dateMf);
                assertNotNull(bp);
            }

            // delete evidence1 and expect a date update and contrib for mf

            final List<M3Request> batch2 = new ArrayList<M3Request>();
            r = BatchTestTools.removeIndividual(modelId, evidence1);
            batch2.add(r);

            {
                M3BatchResponse response2 = executeBatch(batch2, "FOO:2", false);

                JsonOwlIndividual[] individuals = BatchTestTools.responseIndividuals(response2);
                Set<String> currentDates = new HashSet<String>();
                Set<String> contrib = new HashSet<String>();
                for (JsonOwlIndividual individual : individuals) {
                    if (mf.equals(individual.id)) {
                        for (JsonAnnotation annotation : individual.annotations) {
                            if (AnnotationShorthand.date.name().equals(annotation.key)) {
                                currentDates.add(annotation.value);
                            } else if (AnnotationShorthand.contributor.name().equals(annotation.key)) {
                                contrib.add(annotation.value);
                            }
                        }
                    }
                }
                assertEquals(1, currentDates.size());
                assertFalse(currentDates.contains(dateMf)); // prev Date
                dateMf = currentDates.iterator().next();

                assertEquals(2, contrib.size());
                assertTrue(contrib.contains("FOO:1"));
                assertTrue(contrib.contains("FOO:2"));
            }

            // delete evidence2 and expect a date update and contrib for fact

            final List<M3Request> batch3 = new ArrayList<M3Request>();
            r = BatchTestTools.removeIndividual(modelId, evidence2);
            batch3.add(r);

            {
                M3BatchResponse response3 = executeBatch(batch3, "FOO:3", false);
                JsonOwlFact[] facts = BatchTestTools.responseFacts(response3);
                assertEquals(1, facts.length);
                Set<String> currentDates = new HashSet<String>();
                Set<String> contrib = new HashSet<String>();
                for (JsonAnnotation annotation : facts[0].annotations) {
                    if (AnnotationShorthand.date.name().equals(annotation.key)) {
                        currentDates.add(annotation.value);
                    } else if (AnnotationShorthand.contributor.name().equals(annotation.key)) {
                        contrib.add(annotation.value);
                    }
                }
                assertEquals(1, currentDates.size());
                assertFalse(currentDates.contains(dateMf)); // prev Date

                assertEquals(2, contrib.size());
                assertTrue(contrib.contains("FOO:1"));
                assertTrue(contrib.contains("FOO:3"));
            }
        } finally {
            dateGenerator.useCounter = false;
        }
    }

    //FIXME @Test
    public void testCoordinateRoundTrip() throws Exception {
        //models.dispose();

        String modelId = generateBlankModel();

        M3Request r;
        final List<M3Request> batch1 = new ArrayList<M3Request>();
        r = BatchTestTools.addIndividual(modelId, "GO:0008150"); // biological process
        r.arguments.values = new JsonAnnotation[2];
        r.arguments.values[0] = JsonTools.create(AnnotationShorthand.x, "100", null);
        r.arguments.values[1] = JsonTools.create(AnnotationShorthand.y, "200", null);
        batch1.add(r);


        r = new M3Request();
        r.entity = Entity.model;
        r.operation = Operation.addAnnotation;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        r.arguments.values = BatchTestTools.singleAnnotation(AnnotationShorthand.title, "foo");
        batch1.add(r);

        r = new M3Request();
        r.entity = Entity.model;
        r.operation = Operation.storeModel;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        batch1.add(r);

        final M3BatchResponse response1 = executeBatch(batch1, false);
        JsonOwlIndividual[] responseIndividuals = BatchTestTools.responseIndividuals(response1);
        assertEquals(1, responseIndividuals.length);

        //models.dispose();
        assertTrue(models.getCurrentModelIds().isEmpty());

        Set<IRI> availableModelIds = models.getAvailableModelIds();
        assertEquals(1, availableModelIds.size());

        r = new M3Request();
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        r.entity = Entity.model;
        r.operation = Operation.get;

        final M3BatchResponse response2 = executeBatch(Collections.singletonList(r), false);
        JsonOwlIndividual[] responseIndividuals2 = BatchTestTools.responseIndividuals(response2);
        assertEquals(1, responseIndividuals2.length);
        JsonOwlIndividual ind = responseIndividuals2[0];
        boolean foundX = false;
        boolean foundY = false;
        for (JsonAnnotation ann : ind.annotations) {
            if (ann.key.equals(AnnotationShorthand.x.getShorthand())) {
                foundX = "100".equals(ann.value);
            } else if (ann.key.equals(AnnotationShorthand.y.getShorthand())) {
                foundY = "200".equals(ann.value);
            }
        }
        assertTrue(foundX);
        assertTrue(foundY);
    }

    @Test
    public void testPmidIRIIndividual() throws Exception {
        String modelId = generateBlankModel();

        M3Request r;
        final List<M3Request> batch1 = new ArrayList<M3Request>();
        r = new M3Request();
        r.entity = Entity.individual;
        r.operation = Operation.add;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        r.arguments.individualIRI = "PMID:0000";
        BatchTestTools.setExpressionClass(r.arguments, "IAO:0000311");
        batch1.add(r);

        // de-activate check as "IAO:0000311" is currently not in the import chain
        boolean defaultIdPolicy = handler.CHECK_LITERAL_IDENTIFIERS;
        M3BatchResponse response1;
        try {
            handler.CHECK_LITERAL_IDENTIFIERS = false;
            response1 = executeBatch(batch1, false);
        } finally {
            handler.CHECK_LITERAL_IDENTIFIERS = defaultIdPolicy;
        }

        JsonOwlIndividual[] individuals1 = BatchTestTools.responseIndividuals(response1);
        assertEquals(1, individuals1.length);
        assertEquals("PMID:0000", individuals1[0].id);

        // de-activate check as "IAO:0000311" is currently not in the import chain
        // execute second request to test behavior for multiple adds with the same PMID
        defaultIdPolicy = handler.CHECK_LITERAL_IDENTIFIERS;
        M3BatchResponse response2;
        try {
            handler.CHECK_LITERAL_IDENTIFIERS = false;
            response2 = executeBatch(batch1, false);
        } finally {
            handler.CHECK_LITERAL_IDENTIFIERS = defaultIdPolicy;
        }

        JsonOwlIndividual[] individuals2 = BatchTestTools.responseIndividuals(response2);
        assertEquals(1, individuals2.length);
        assertEquals("PMID:0000", individuals2[0].id);
    }

    @Test
    public void testUnknownIdentifier() throws Exception {
        String modelId = generateBlankModel();

        M3Request r;
        final List<M3Request> batch1 = new ArrayList<M3Request>();
        r = new M3Request();
        r.entity = Entity.individual;
        r.operation = Operation.add;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        BatchTestTools.setExpressionClass(r.arguments, "IDA");
        batch1.add(r);

        boolean defaultIdPolicy = handler.CHECK_LITERAL_IDENTIFIERS;
        M3BatchResponse response;
        try {
            handler.CHECK_LITERAL_IDENTIFIERS = true;
            response = handler.m3Batch(uid, providedBy, intention, packetId, batch1.toArray(new M3Request[batch1.size()]), false, true);
        } finally {
            handler.CHECK_LITERAL_IDENTIFIERS = defaultIdPolicy;
        }
        assertEquals(uid, response.uid);
        assertEquals(intention, response.intention);

        // this has to fail as IDA is *not* a known identifier
        assertEquals(M3BatchResponse.MESSAGE_TYPE_ERROR, response.messageType);
    }

    //FIXME @Test
    public void testRelationLabels() throws Exception {
        //models.dispose();

        // find test relation
        Set<OWLObjectProperty> properties = models.getOntology().getObjectPropertiesInSignature(Imports.INCLUDED);
        OWLObjectProperty gorel0002006 = null;
        for (OWLObjectProperty p : properties) {
            IRI iri = p.getIRI();
            if (iri.toString().endsWith("http://purl.obolibrary.org/obo/GOREL_0002006")) {
                gorel0002006 = p;
            }
        }
        assertNotNull(gorel0002006);
        String gorel0002006Curie = curieHandler.getCuri(gorel0002006);

        // check meta
        M3Request r = new M3Request();
        r.entity = Entity.meta;
        r.operation = Operation.get;

        M3BatchResponse response1 = execute(r, false);
        final JsonRelationInfo[] relations = BatchTestTools.responseRelations(response1);
        JsonRelationInfo gorel0002006Info = null;
        for (JsonRelationInfo rel : relations) {
            if (rel.id.equals(gorel0002006Curie)) {
                gorel0002006Info = rel;
            }
        }
        assertNotNull(gorel0002006Info);
        assertEquals("results_in_organization_of", gorel0002006Info.label);


        // use relation and check that response also contains relation label
        String modelId = generateBlankModel();

        r = new M3Request();
        r.entity = Entity.individual;
        r.operation = Operation.add;
        r.arguments = new M3Argument();
        r.arguments.modelId = modelId;
        r.arguments.expressions = new JsonOwlObject[1];
        r.arguments.expressions[0] = BatchTestTools.createSvf(gorel0002006Curie, "GO:0003674");

        M3BatchResponse response2 = execute(r, false);
        JsonOwlIndividual[] individuals = BatchTestTools.responseIndividuals(response2);
        assertEquals(1, individuals.length);
        JsonOwlIndividual individual = individuals[0];
        JsonOwlObject[] types = individual.type;
        assertEquals(1, types.length);
        JsonOwlObject property = types[0].property;
        assertEquals(gorel0002006Curie, property.id);
        assertEquals("results_in_organization_of", property.label);

    }

    private M3BatchResponse execute(M3Request r, boolean useReasoner) {
        return executeBatch(Collections.singletonList(r), useReasoner);
    }

    private M3BatchResponse executeBatch(List<M3Request> batch, boolean useReasoner) {
        return executeBatch(batch, uid, useReasoner);
    }

    private M3BatchResponse executeBatch(List<M3Request> batch, String uid, boolean useReasoner) {
        M3BatchResponse response = handler.m3Batch(uid, providedBy, intention, packetId, batch.toArray(new M3Request[batch.size()]), useReasoner, true);
        assertEquals(uid, response.uid);
        assertEquals(intention, response.intention);
        assertEquals(response.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response.messageType);
        return response;
    }

    /**
     * @return modelId
     */
    private String generateBlankModel() {
        String modelId = BatchTestTools.generateBlankModel(handler);
        return modelId;
    }
}
