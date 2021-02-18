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
import org.geneontology.minerva.json.JsonOwlObject;
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

	private boolean isType(JsonOwlObject[] rootTypes, RootType rootType) {
		return Arrays.stream(rootTypes).anyMatch(x -> Objects.equals(x.id, rootType.id));
	}

	private GOCam getActivityModel(JsonModel baseModel) {
		GOCam goCam = new GOCam(baseModel.modelId);
		Set<Activity> activities = new HashSet<Activity>();

		goCam.addAnnotations(baseModel.annotations);

		for (JsonOwlIndividual individual : baseModel.individuals) {
			JsonOwlObject[] rootTypes = individual.rootType;

			if (isType(rootTypes, RootType.GoMolecularFunction)) {
				JsonOwlObject[] types = individual.type;
				if (types.length > 0) {
					JsonOwlObject typeObj = types[0];
					Term term = new Term(individual.id, typeObj, individual.annotations);
					Activity activity = new Activity(term);
					activities.add(activity);
				}
			}
		}

		goCam.setActivities(activities);
		return goCam;
	}
}

enum EntityType {
	TERM, EVIDENCE
}

enum ActivityType {
	ACTIVITY_UNIT, BP_ONLY, CC_ONLY
}

class Entity {
	private String uuid;
	private String id;
	private String label;
	private EntityType entityType;

	public Entity(String uuid, String id, String label, EntityType entityType) {
		this.uuid = uuid;
		this.id = id;
		this.label = label;
		this.entityType = entityType;
	}

	public String getUuid() {
		return uuid;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public EntityType getEntityType() {
		return entityType;
	}

	public void setEntityType(EntityType entityType) {
		this.entityType = entityType;
	}
}

class Term extends Entity {
	private String aspect;
	private boolean isRootNode;
	private boolean isExtension;
	private Set<Contributor> contributors;
	private Set<Group> groups;
	private String date;

	public Term(String uuid, String id, String label) {
		super(uuid, id, label, EntityType.TERM);		
		this.contributors = new HashSet<Contributor>();
		this.groups = new HashSet<Group>();
	}

	public Term(String uuid, JsonOwlObject type) {
		this(uuid, type.id, type.label);
	}

	public Term(String uuid, JsonOwlObject type, JsonAnnotation[] annotations) {
		this(uuid, type.id, type.label);
		this.addAnnotations(annotations);
	}

	public String getAspect() {
		return aspect;
	}

	public boolean addContributor(Contributor contributor) {
		return contributors.add(contributor);
	}

	public boolean addGroup(Group group) {
		return groups.add(group);
	}

	public void setAspect(String aspect) {
		this.aspect = aspect;
	}

	public boolean isRootNode() {
		return isRootNode;
	}

	public void setRootNode(boolean isRootNode) {
		this.isRootNode = isRootNode;
	}

	public boolean isExtension() {
		return isExtension;
	}

	public void setExtension(boolean isExtension) {
		this.isExtension = isExtension;
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

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public void addAnnotations(JsonAnnotation[] annotations) {
		for (JsonAnnotation annotation : annotations) {
			if (AnnotationShorthand.contributor.name().equals(annotation.key)) {
				addContributor(new Contributor(annotation.value));
			}

			if (AnnotationShorthand.providedBy.name().equals(annotation.key)) {
				addGroup(new Group(annotation.value));
			}

			if (AnnotationShorthand.modelstate.name().equals(annotation.key)) {
				setDate(annotation.value);
			}
		}
	}

}

class Evidence extends Entity {
	private Entity evidence;
	private String reference;
	private String with;
	private Set<Contributor> contributors;
	private Set<Group> groups;
	private Date date;

	public Evidence(String uuid, String id, String label) {
		super(uuid, id, label, EntityType.EVIDENCE);
		
		this.contributors = new HashSet<Contributor>();
		this.groups = new HashSet<Group>();
	}

	public Entity getEvidence() {
		return evidence;
	}

	public void setEvidence(Entity evidence) {
		this.evidence = evidence;
	}

	public String getReference() {
		return reference;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}

	public String getWith() {
		return with;
	}

	public void setWith(String with) {
		this.with = with;
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

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

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
	private String uuid;
	private String title;
	private Term rootTerm;
	private Term mf;
	private Term bp;
	private Term cc;

	public Activity(Term rootTerm) {
		this.type = ActivityType.ACTIVITY_UNIT;
		this.uuid = rootTerm.getUuid();
		this.title = rootTerm.getLabel();

		this.rootTerm = rootTerm;
		this.mf = rootTerm;
	}

	public ActivityType getType() {
		return type;
	}

	public void setType(ActivityType type) {
		this.type = type;
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Term getRootTerm() {
		return rootTerm;
	}

	public void setRootTerm(Term rootTerm) {
		this.rootTerm = rootTerm;
	}

	public Term getMF() {
		return mf;
	}

	public void setMF(Term mf) {
		this.mf = mf;
	}

	public Term getBP() {
		return bp;
	}

	public void setBP(Term bp) {
		this.bp = bp;
	}

	public Term getCC() {
		return cc;
	}

	public void setCC(Term cc) {
		this.cc = cc;
	}

}

class GOCam {

	private String id;
	private String title;
	private String state;
	private Set<Contributor> contributors;
	private Set<Group> groups;
	private Set<Taxon> taxons;
	private Set<Activity> activities;

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

	public Set<Activity> getActivities() {
		return activities;
	}

	public void setActivities(Set<Activity> activities) {
		this.activities = activities;
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

enum RootType {
	GoProteinContainingComplex("GO:0032991", "protein-containing complex"),
	GoCellularComponent("GO:0005575", "cellular_component"), GoBiologicalProcess("GO:0008150", "biological_process"),
	GoMolecularFunction("GO:0003674", "molecular_function"),
	GoMolecularEntity("CHEBI:33695", "information biomacromolecule"),
	GoChemicalEntity("CHEBI:24431", "chemical entity");

	private static final Map<String, RootType> BY_ID = new HashMap<>();
	private static final Map<String, RootType> BY_LABEL = new HashMap<>();

	static {
		for (RootType e : values()) {
			BY_ID.put(e.id, e);
			BY_LABEL.put(e.label, e);
		}
	}

	public final String id;
	public final String label;

	private RootType(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public static RootType valueOfId(String id) {
		return BY_ID.get(id);
	}

	public static RootType valueOfLabel(String label) {
		return BY_LABEL.get(label);
	}

}