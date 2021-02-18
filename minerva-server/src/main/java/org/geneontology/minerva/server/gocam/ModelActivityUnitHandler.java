/**
 * 
 */
package org.geneontology.minerva.server.gocam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import org.geneontology.minerva.json.JsonOwlIndividual;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
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

/**
 * Gets Model readonly data for Annotation Review Tool Uses Jersey + JSONP
 *
 *
 */
@Path("/search/activity-unit") // using store endpoint temporarily because thats what barista allows
public class ModelActivityUnitHandler {

	private final BlazegraphMolecularModelManager<?> m3;
	private final BlazegraphOntologyManager go_lego;
	private final CurieHandler curieHandler;
	private final InferenceProviderCreator ipc;

	/**
	 * 
	 */
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
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology currentOntology = m3.getModelAbox(modelIri);

		ModelContainer activeMC = new ModelContainer(modelIri, null, currentOntology);
		InferenceProvider inferenceProvider = ipc.create(activeMC);
		final MolecularModelJsonRenderer renderer = OperationsTools.createModelRenderer(activeMC, go_lego, inferenceProvider,
				curieHandler);
		JsonModel jsonBaseModel = renderer.renderModel();

		result.baseModel = jsonBaseModel;
		result.activityModel = getActivityModel(jsonBaseModel);

	}

	private GOCam getActivityModel(JsonModel baseModel) {
		GOCam goCam = new GOCam(baseModel.modelId);

		goCam.addAnnotations(baseModel.annotations);	
		
		
		
		return goCam;
	}
}

enum ActivityType {
	activityUnit, bpOnly, ccOnly
}

class Contributor {
	private String orcid;
	private String name;

	public Contributor(String orcid) {
		this.orcid = orcid;
	}

	public String getOrcid() {
		return orcid;
	}

	public void setOrcid(String orcid) {
		this.orcid = orcid;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}

class Group {
	private String url;
	private String name;

	public Group(String url) {
		this.url = url;
	}

	public Group(String url, String name) {
		this.url = url;
		this.name = name;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}

class Taxon {
	private String name;
	private String label;
	
	public Taxon(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
		
	public String getLabel() {
		return label;
	}
	
	public void setLabel(String label) {
		this.label = label;
	}	
	
}

class Activity {
	private ActivityType type;
	private String activityId;
	private String activityLabel;

	public Activity(String id, String label) {
		this.type = ActivityType.activityUnit;
		this.activityId = id;
		this.activityLabel = label;
	}

	public ActivityType getType() {
		return type;
	}

	public void setType(ActivityType type) {
		this.type = type;
	}

	public String getActivityId() {
		return activityId;
	}

	public void setActivityId(String activityId) {
		this.activityId = activityId;
	}

	public String getActivityLabel() {
		return activityLabel;
	}

	public void setActivityLabel(String activityLabel) {
		this.activityLabel = activityLabel;
	}
}

class GOCam {

	private String id;
	private String title;
	private String state;
	private Set<Contributor> contributors;
	private Set<Group> groups;
	private Set<Taxon> taxons;
	
	public GOCam(String id) {
		this.id = id;
		this.contributors = new HashSet<Contributor>();
		this.groups = new HashSet<Group>();
		this.taxons = new HashSet<Taxon>();
	}	

	public boolean addContributor(Contributor contributor) {
		 return contributors.add(contributor);
	}

	public boolean addGroup(Group group) {
		 return groups.add(group);
	}

	public boolean addTaxon(Taxon taxon) {
		 return taxons.add(taxon);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}	

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public Set<Taxon> getTaxons() {
		return taxons;
	}

	public void setTaxons(Set<Taxon> taxons) {
		this.taxons = taxons;
	}

	public Set<Contributor> getContributors() {
		return contributors;
	}

	public void setContributors(Set<Contributor> contributors) {
		this.contributors = contributors;
	}

	public Set<Group> getGroups() {
		return groups;
	}

	public void setGroups(Set<Group> groups) {
		this.groups = groups;
	}
	
	public void addAnnotations(JsonAnnotation[] annotations) {
		for (JsonAnnotation annotation : annotations) {
			if (AnnotationShorthand.contributor.name().equals(annotation.key)) {
				addContributor(new Contributor(annotation.value));
			}
			
			if (AnnotationShorthand.providedBy.name().equals(annotation.key)) {
				addGroup(new Group(annotation.value));
			}

			if (AnnotationShorthand.title.name().equals(annotation.key)) {
				setTitle(annotation.value);
			}
			
			if (AnnotationShorthand.modelstate.name().equals(annotation.key)) {
				setState(annotation.value);
			}
			
			if (AnnotationShorthand.taxon.name().equals(annotation.key)) {
				addTaxon(new Taxon(annotation.value));
			}
		}
	}

}
