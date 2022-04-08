/**
 *
 */
package org.geneontology.minerva.server.handler;

import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.geneontology.minerva.BlazegraphOntologyManager;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.json.InferenceProvider;
import org.geneontology.minerva.json.JsonModel;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.server.inferences.InferenceProviderCreator;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.Set;

import static org.geneontology.minerva.server.handler.OperationsTools.createModelRenderer;

/**
 * Gets Model readonly data for Annotation Review Tool
 * Uses Jersey + JSONP
 *
 *
 */
@Path("/search/stored") //using store endpoint temporarily because thats what barista allows
public class ModelARTHandler {

    private final BlazegraphMolecularModelManager<?> m3;
    private final BlazegraphOntologyManager go_lego;
    private final CurieHandler curieHandler;
    private final InferenceProviderCreator ipc;

    /**
     *
     */
    public ModelARTHandler(BlazegraphMolecularModelManager<?> m3, InferenceProviderCreator ipc) {
        this.m3 = m3;
        this.go_lego = m3.getGolego_repo();
        this.curieHandler = m3.getCuriHandler();
        this.ipc = ipc;
    }

    public class ModelARTResult {
        private String id;
        private JsonModel storedModel;
        private JsonModel activeModel;
        private JsonModel diffModel;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setStoredModel(JsonModel storedModel) {
            this.storedModel = storedModel;
        }

        public JsonModel getStoredModel() {
            return this.storedModel;
        }

        public void setActiveModel(JsonModel activeModel) {
            this.activeModel = activeModel;
        }

        public JsonModel getActiveModel() {
            return this.activeModel;
        }
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ModelARTResult storedGet(
            @QueryParam("id") Set<String> id
    ) throws Exception {
        ModelARTResult result = new ModelARTResult();
        result = stored(id);
        return result;
    }

    public ModelARTResult stored(Set<String> ids) throws Exception {
        ModelARTResult result = new ModelARTResult();

        for (String mid : ids) {
            addToModel(mid, result);
        }

        return result;
    }

    private void addToModel(String modelId, ModelARTResult result) throws Exception {

        IRI modelIri = curieHandler.getIRI(modelId);
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology currentOntology = m3.getModelAbox(modelIri);
        OWLOntology storedOntology = m3.loadModelABox(modelIri, manager);

        //OWLOntology stored_ontology = man1.copyOntology(storedOntology, OntologyCopy.DEEP);
        ModelContainer storedMC = new ModelContainer(modelIri, null, storedOntology);
        final MolecularModelJsonRenderer storedRenderer = createModelRenderer(storedMC, go_lego, null, curieHandler, m3.getTboxLabelIndex());
        JsonModel jsonStoredModel = storedRenderer.renderModel();

        ModelContainer activeMC = new ModelContainer(modelIri, null, currentOntology);
        InferenceProvider inferenceProvider = ipc.create(activeMC);
        final MolecularModelJsonRenderer renderer = createModelRenderer(activeMC, go_lego, inferenceProvider, curieHandler, m3.getTboxLabelIndex());
        JsonModel jsonActiveModel = renderer.renderModel();

        result.storedModel = jsonStoredModel;
        result.activeModel = jsonActiveModel;
        result.diffModel = getDiff(jsonStoredModel, jsonActiveModel);

    }

    private JsonModel getDiff(JsonModel storedOntology, JsonModel activeOntology) {
        return new JsonModel();
    }

}
