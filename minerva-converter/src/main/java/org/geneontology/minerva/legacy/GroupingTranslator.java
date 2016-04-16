package org.geneontology.minerva.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationValueVisitorEx;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;

import owltools.gaf.Bioentity;
import owltools.gaf.GafDocument;
import owltools.gaf.GeneAnnotation;

/**
 * Tool for grouping translated lego models, by model state or taxon group.
 */
public class GroupingTranslator {

	final Set<String> modelStates = new HashSet<>();
	final Map<String, List<GeneAnnotation>> typedAnnotations = new HashMap<>();
	final Map<String, List<Bioentity>> typedEntities = new HashMap<>();

	final Map<String, List<GeneAnnotation>> modelGroupAnnotations = new HashMap<>();
	final Map<String, List<Bioentity>> modelGroupEntities = new HashMap<>();
	
	private final Map<String, String> taxonGroups;
	private final String defaultTaxonGroup;
	
	private final LegoToGeneAnnotationTranslator translator;
	private final ExternalLookupService lookup;
	private final CurieHandler curieHandler;
	
	private final boolean addLegoModelId;
	
	public GroupingTranslator(LegoToGeneAnnotationTranslator translator, ExternalLookupService lookup,
			Map<String, String> taxonGroups, String defaultTaxonGroup, boolean addLegoModelId)
	{
		this.translator = translator;
		this.lookup = lookup;
		this.curieHandler = translator.curieHandler;

		this.taxonGroups = taxonGroups;
		this.defaultTaxonGroup = defaultTaxonGroup;

		this.addLegoModelId = addLegoModelId;
	}
	
	public void translate(OWLOntology model) {
		// get curie
		String modelCurie = getModelCurie(model, curieHandler, null);
		
		List<String> addtitionalRefs = handleRefs(addLegoModelId, modelCurie);
		
		// get state
		final String modelState = getModelState(model, "unknown");
		modelStates.add(modelState);

		// create containers
		GafDocument annotations = new GafDocument(null, null);

		// translate
		translator.translate(model, lookup, annotations, addtitionalRefs);

		// append to appropriate model state containers
		List<GeneAnnotation> modelStateAnnotations = typedAnnotations.get(modelState);
		if (modelStateAnnotations == null) {
			modelStateAnnotations = new ArrayList<>();
			typedAnnotations.put(modelState, modelStateAnnotations);
		}
		for(GeneAnnotation annotation : annotations.getGeneAnnotations()) {
			modelStateAnnotations.add(annotation);
		}
		List<Bioentity> modelStateEntities = typedEntities.get(modelState);
		if (modelStateEntities == null) {
			modelStateEntities = new ArrayList<>();
			typedEntities.put(modelState, modelStateEntities);
		}
		for(Bioentity entity : annotations.getBioentities()) {
			if (!modelStateEntities.contains(entity)) {
				modelStateEntities.add(entity);
			}
		}

		// sort into model organism groups
		if (taxonGroups != null) {
			for (Bioentity entity : annotations.getBioentities()) {
				String ncbiTaxonId = entity.getNcbiTaxonId();
				if (ncbiTaxonId != null) {
					ncbiTaxonId = ncbiTaxonId.replace("taxon", "NCBITaxon");
				}
				String group = taxonGroups.get(ncbiTaxonId);
				if (group == null) {
					group = defaultTaxonGroup;
				}
				List<GeneAnnotation> groupDocument = modelGroupAnnotations.get(group);
				if (groupDocument == null) {
					groupDocument = new ArrayList<>();
					modelGroupAnnotations.put(group, groupDocument);
				}
				List<Bioentity> groupEntities = modelGroupEntities.get(group);
				if (groupEntities == null) {
					groupEntities = new ArrayList<>();
					modelGroupEntities.put(group, groupEntities);
				}
				if (!groupEntities.contains(entity)) {
					groupEntities.add(entity);
				}
				for (GeneAnnotation ann : annotations.getGeneAnnotations()) {
					if (ann.getBioentity().equals(entity.getId())) {
						groupDocument.add(ann);
					}
				}
			}
		}
	}
	
	public Set<String> getModelStates() {
		return Collections.unmodifiableSet(modelStates);
	}
	
	public GafDocument getAnnotationsByState(String modelState) {
		GafDocument result = new GafDocument(null, null);
		List<GeneAnnotation> annotations = typedAnnotations.get(modelState);
		List<Bioentity> entities = typedEntities.get(modelState);
		if (annotations != null && entities != null) {
			for(Bioentity entity : entities) {
				result.addBioentity(entity);
			}
			for(GeneAnnotation annotation : annotations) {
				result.addGeneAnnotation(annotation);
			}
		}
		return result;
	}
	
	public GafDocument getAnnotationsByGroup(String taxonGroup) {
		GafDocument result = new GafDocument(null, null);
		List<GeneAnnotation> annotations = modelGroupAnnotations.get(taxonGroup);
		List<Bioentity> entities = modelGroupEntities.get(taxonGroup);
		if (annotations != null && entities != null) {
			for(Bioentity entity : entities) {
				result.addBioentity(entity);
			}
			for(GeneAnnotation annotation : annotations) {
				result.addGeneAnnotation(annotation);
			}
		}
		return result;
	}
	
	public Set<String> getTaxonGroups() {
		return Collections.unmodifiableSet(modelGroupAnnotations.keySet());
	}
	
	List<String> handleRefs(boolean addLegoModelId, String modelCurie) {
		List<String> addtitionalRefs = null;
		if (addLegoModelId && modelCurie != null) {
				addtitionalRefs = Collections.singletonList(modelCurie);
		}
		return addtitionalRefs;
	}
	
	public static String getModelState(OWLOntology model, String defaultValue) {
		String modelState = defaultValue;
		Set<OWLAnnotation> modelAnnotations = model.getAnnotations();
		for (OWLAnnotation modelAnnotation : modelAnnotations) {
			IRI propIRI = modelAnnotation.getProperty().getIRI();
			if (AnnotationShorthand.modelstate.getAnnotationProperty().equals(propIRI)) {
				String value = modelAnnotation.getValue().accept(new OWLAnnotationValueVisitorEx<String>() {

					@Override
					public String visit(IRI iri) {
						return null;
					}

					@Override
					public String visit(OWLAnonymousIndividual individual) {
						return null;
					}

					@Override
					public String visit(OWLLiteral literal) {
						return literal.getLiteral();
					}
				});
				if (value != null) {
					modelState = value;
				}
			}
		}
		return modelState;
	}
	
	public static String getModelCurie(OWLOntology model, CurieHandler curieHandler, String defaultValue) {
		// get model curie from ontology IRI
		String modelCurie = defaultValue;
		IRI ontologyIRI = model.getOntologyID().getOntologyIRI();
		if (ontologyIRI != null) {
			modelCurie = curieHandler.getCuri(ontologyIRI);
		}
		return modelCurie;
	}
}
