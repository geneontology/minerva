package org.geneontology.minerva.server.handler;

import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.MolecularModelManager;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.UndoAwareMolecularModelManager.UndoMetadata;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.json.*;
import org.geneontology.minerva.server.handler.M3BatchHandler.*;
import org.geneontology.minerva.server.inferences.InferenceProviderCreator;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DataPropertyTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
    static final String go_lego_journal_file = "/tmp/test-go-lego-blazegraph.jnl";

    private UndoAwareMolecularModelManager createM3(OWLOntology tbox) throws OWLOntologyCreationException, IOException {
        UndoAwareMolecularModelManager mmm = new UndoAwareMolecularModelManager(tbox, curieHandler,
                "http://model.geneontology.org/", folder.newFile().getAbsolutePath(), null, go_lego_journal_file, true);
        return mmm;
    }

    @Test
    public void testDataPropertyMetadata() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = m.createOntology(IRI.generateDocumentIRI());
        {
            // create a test ontology with one data property
            OWLDataFactory f = m.getOWLDataFactory();
            IRI propIRI = IRI.generateDocumentIRI();
            OWLDataProperty prop = f.getOWLDataProperty(propIRI);
            m.addAxiom(ontology, f.getOWLDeclarationAxiom(prop));
            m.addAxiom(ontology, f.getOWLAnnotationAssertionAxiom(propIRI, f.getOWLAnnotation(f.getRDFSLabel(), f.getOWLLiteral("fake-data-property"))));
        }
        MolecularModelManager<?> mmm = createM3(ontology);
        Pair<List<JsonRelationInfo>, List<JsonRelationInfo>> pair = MolecularModelJsonRenderer.renderProperties(mmm, null, curieHandler);
        List<JsonRelationInfo> dataProperties = pair.getRight();
        assertEquals(1, dataProperties.size());
        mmm.dispose();
    }

    @Test
    public void testDataProperyRenderer() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = m.createOntology(IRI.generateDocumentIRI());
        final IRI clsIRI = IRI.generateDocumentIRI();
        final IRI propIRI = IRI.generateDocumentIRI();

        // create a test ontology with one data property and one class
        OWLDataFactory f = m.getOWLDataFactory();
        OWLDataProperty prop = f.getOWLDataProperty(propIRI);
        m.addAxiom(ontology, f.getOWLDeclarationAxiom(prop));
        m.addAxiom(ontology, f.getOWLAnnotationAssertionAxiom(propIRI, f.getOWLAnnotation(f.getRDFSLabel(), f.getOWLLiteral("fake-data-property"))));

        OWLClass cls = f.getOWLClass(clsIRI);
        m.addAxiom(ontology, f.getOWLDeclarationAxiom(cls));
        m.addAxiom(ontology, f.getOWLAnnotationAssertionAxiom(clsIRI, f.getOWLAnnotation(f.getRDFSLabel(), f.getOWLLiteral("fake-cls"))));

        // graph and m3
        final UndoMetadata metadata = new UndoMetadata("foo-user");
        UndoAwareMolecularModelManager m3 = createM3(ontology);

        final ModelContainer model = m3.generateBlankModel(metadata);
        final OWLNamedIndividual individual = m3.createIndividual(model, cls, metadata);
        m3.addDataProperty(model, individual, prop, f.getOWLLiteral(10), metadata);

        MolecularModelJsonRenderer r = new MolecularModelJsonRenderer(model, null, curieHandler);
        final JsonModel jsonModel = r.renderModel();
        assertEquals(1, jsonModel.individuals.length);
        assertEquals(1, jsonModel.individuals[0].annotations.length);
        {
            JsonAnnotation ann = jsonModel.individuals[0].annotations[0];
            assertEquals(propIRI.toString(), ann.key);
            assertEquals("10", ann.value);
            assertEquals("xsd:integer", ann.valueType);
        }
        m3.dispose();
    }

    @Test
    public void testDataPropertyBatch() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = m.createOntology(IRI.generateDocumentIRI());
        final IRI clsIRI = IRI.create("http://purl.obolibrary.org/obo/GO_0001");
        final IRI propIRI = IRI.create("http://purl.obolibrary.org/obo/RO_0001");

        // create a test ontology with one data property and one class
        OWLDataFactory f = m.getOWLDataFactory();
        OWLDataProperty prop = f.getOWLDataProperty(propIRI);
        m.addAxiom(ontology, f.getOWLDeclarationAxiom(prop));
        m.addAxiom(ontology, f.getOWLAnnotationAssertionAxiom(propIRI, f.getOWLAnnotation(f.getRDFSLabel(), f.getOWLLiteral("fake-data-property"))));

        OWLClass cls = f.getOWLClass(clsIRI);
        m.addAxiom(ontology, f.getOWLDeclarationAxiom(cls));
        m.addAxiom(ontology, f.getOWLAnnotationAssertionAxiom(clsIRI, f.getOWLAnnotation(f.getRDFSLabel(), f.getOWLLiteral("fake-cls"))));

        // graph and m3
        UndoAwareMolecularModelManager m3 = createM3(ontology);

        // handler
        InferenceProviderCreator ipc = null;
        JsonOrJsonpBatchHandler handler = new JsonOrJsonpBatchHandler(m3, "development", ipc, null, null);

        // empty model
        final ModelContainer model = m3.generateBlankModel(new UndoMetadata("foo-user"));

        // create individual with annotations, including one data property
        M3Request r1 = BatchTestTools.addIndividual(curieHandler.getCuri(model.getModelId()), "GO:0001");
        r1.arguments.values = new JsonAnnotation[2];
        r1.arguments.values[0] = new JsonAnnotation();
        r1.arguments.values[0].key = AnnotationShorthand.comment.name();
        r1.arguments.values[0].value = "foo-comment";
        r1.arguments.values[1] = new JsonAnnotation();
        r1.arguments.values[1].key = curieHandler.getCuri(propIRI);
        r1.arguments.values[1].value = "10";
        r1.arguments.values[1].valueType = "xsd:integer";

        M3BatchResponse response1 = exec(handler, Collections.singletonList(r1));

        final String individualsId;
        // check for data property as annotation
        {
            assertEquals(1, response1.data.individuals.length);
            JsonOwlIndividual i = response1.data.individuals[0];
            assertEquals(4, i.annotations.length);
            individualsId = i.id;
            JsonAnnotation dataPropAnnotation = null;
            for (JsonAnnotation ann : i.annotations) {
                if (curieHandler.getCuri(propIRI).equals(ann.key)) {
                    dataPropAnnotation = ann;
                }
            }
            assertNotNull(dataPropAnnotation);
        }
        assertNotNull(individualsId);

        // check underlying owl model for usage of OWLDataProperty
        {
            Set<OWLDataPropertyAssertionAxiom> axioms = model.getAboxOntology().getAxioms(AxiomType.DATA_PROPERTY_ASSERTION);
            assertEquals(1, axioms.size());
            OWLDataPropertyAssertionAxiom ax = axioms.iterator().next();
            OWLLiteral literal = ax.getObject();
            assertEquals(prop, ax.getProperty());
            assertEquals(f.getOWLLiteral(10), literal);
        }

        // delete data property
        M3Request r2 = new M3Request();
        r2.entity = Entity.individual;
        r2.operation = Operation.removeAnnotation;
        r2.arguments = new M3Argument();
        r2.arguments.individual = individualsId;
        r2.arguments.modelId = curieHandler.getCuri(model.getModelId());
        r2.arguments.values = new JsonAnnotation[1];
        r2.arguments.values[0] = new JsonAnnotation();
        r2.arguments.values[0].key = propIRI.toString();
        r2.arguments.values[0].value = "10";
        r2.arguments.values[0].valueType = "xsd:integer";

        M3BatchResponse response2 = exec(handler, Collections.singletonList(r2));
        // check for deleted property as annotation
        {
            assertEquals(1, response2.data.individuals.length);
            JsonOwlIndividual i = response2.data.individuals[0];
            assertEquals(3, i.annotations.length);
        }
        m3.dispose();
    }

    private M3BatchResponse exec(JsonOrJsonpBatchHandler handler, List<M3Request> requests) {
        String uid = "foo-user";
        String intention = "generated";
        String packetId = "0";
        M3BatchResponse response = handler.m3Batch(uid, Collections.emptySet(), intention, packetId, requests.toArray(new M3Request[requests.size()]), false, true);
        assertEquals(uid, response.uid);
        assertEquals(intention, response.intention);
        assertEquals(packetId, response.packetId);
        assertEquals(response.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response.messageType);
        return response;
    }
}
