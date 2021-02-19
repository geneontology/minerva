/**
 * 
 */
package org.geneontology.minerva.server.gocam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.geneontology.minerva.BlazegraphOntologyManager;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.json.InferenceProvider;
import org.geneontology.minerva.json.JsonAnnotation;
import org.geneontology.minerva.json.JsonModel;
import org.geneontology.minerva.json.JsonOwlFact;
import org.geneontology.minerva.json.JsonOwlIndividual;
import org.geneontology.minerva.json.JsonOwlObject;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.server.gocam.data.RootType;
import org.geneontology.minerva.server.gocam.entities.Activity;
import org.geneontology.minerva.server.gocam.entities.Term;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse.ResponseData;
import org.geneontology.minerva.server.handler.OperationsTools;
import org.geneontology.minerva.server.inferences.InferenceProviderCreator;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.openrdf.query.Binding;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryResult;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;

import com.google.gson.annotations.SerializedName;

@Path("/search/activity-unit") // using store endpoint temporarily because thats what barista allows
public class ModelActivityUnitHandler {

	private final BlazegraphMolecularModelManager<?> m3;
	private final BlazegraphOntologyManager go_lego;
	private final CurieHandler curieHandler;
	private final InferenceProviderCreator ipc;

	public ModelActivityUnitHandler(BlazegraphMolecularModelManager<?> m3, InferenceProviderCreator ipc) {
		this.m3 = m3;
		this.go_lego = m3.getGolego_repo();
		this.curieHandler = m3.getCuriHandler();
		this.ipc = ipc;
	}

	public class ModelActivityUnitResult {
		private JsonModel baseModel;
		private GOCam activityModel;

		public JsonModel getBaseModel() {
			return baseModel;
		}

		public void setBaseModel(JsonModel baseModel) {
			this.baseModel = baseModel;
		}

		public GOCam getActivityModel() {
			return activityModel;
		}

		public void setActivityModel(GOCam activityModel) {
			this.activityModel = activityModel;
		}

	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public ModelActivityUnitResult storedGet(@QueryParam("id") Set<String> id) throws Exception {
		ModelActivityUnitResult result = new ModelActivityUnitResult();
		result = stored(id);
		return result;
	}

	public ModelActivityUnitResult stored(Set<String> ids) throws Exception {
		ModelActivityUnitResult result = new ModelActivityUnitResult();

		for (String mid : ids) {
			addToModel(mid, result);
		}

		return result;
	}

	private void addToModel(String modelId, ModelActivityUnitResult result) throws Exception {

		IRI modelIri = curieHandler.getIRI(modelId);
		OWLOntology currentOntology = m3.getModelAbox(modelIri);
		ModelContainer activeMC = new ModelContainer(modelIri, null, currentOntology);
		InferenceProvider inferenceProvider = ipc.create(activeMC);
		final MolecularModelJsonRenderer renderer = OperationsTools.createModelRenderer(activeMC, go_lego,
				inferenceProvider, curieHandler);
		JsonModel jsonBaseModel = renderer.renderModel();

		result.baseModel = jsonBaseModel;
		result.activityModel = getActivityModel(jsonBaseModel);

	}



	private GOCam getActivityModel(JsonModel baseModel) {
		GOCam goCam = new GOCam(baseModel.modelId);
		Set<Activity> activities = new HashSet<Activity>();

		goCam.addAnnotations(baseModel.annotations); 

		for (JsonOwlIndividual individual : baseModel.individuals) {
			JsonOwlObject[] rootTypes = individual.rootType;

			if (GOCamTools.isType(rootTypes, RootType.GO_MOLECULAR_FUNCTION)) {
				JsonOwlObject[] types = individual.type;
				if (types.length > 0) {
					JsonOwlObject typeObj = types[0];
					Term term = new Term(individual.id, typeObj, individual.annotations);
					Activity activity = new Activity(term);
					activity.parse(baseModel.individuals, baseModel.facts);
					activities.add(activity);	
				}
			}
		}

		goCam.setActivities(activities);
		goCam.parse(baseModel.individuals, baseModel.facts);
		return goCam;
	}
}













