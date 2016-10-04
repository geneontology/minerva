package org.geneontology.minerva.generate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.MolecularModelManager;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.geneontology.reasoner.ExpressionMaterializingReasoner;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.util.OWLClassExpressionVisitorAdapter;

import owltools.gaf.Bioentity;
import owltools.gaf.GeneAnnotation;
import owltools.gaf.eco.SimpleEcoMapper;
import owltools.vocab.OBOUpperVocabulary;

import com.google.common.collect.Sets;

public class ModelSeeding<METADATA> {
	
	private final ExpressionMaterializingReasoner reasoner;
	private final SeedingDataProvider dataProvider;
	private final CurieHandler curieHandler;
	private final Set<OWLAnnotation> defaultAnnotations;
	private final SimpleEcoMapper ecoMapper;
	
	public ModelSeeding(ExpressionMaterializingReasoner reasoner, SeedingDataProvider dataProvider, 
			Set<OWLAnnotation> defaultAnnotations, CurieHandler curieHandler, SimpleEcoMapper ecoMapper) {
		this.reasoner = reasoner;
		this.dataProvider = dataProvider;
		this.defaultAnnotations = defaultAnnotations;
		this.curieHandler = curieHandler;
		this.ecoMapper = ecoMapper;
		reasoner.setIncludeImports(true);
	}

	public void seedModel(ModelContainer model, MolecularModelManager<METADATA> manager, String bp, final METADATA metadata) throws Exception {
		final IRI modelId = model.getModelId();
		final Map<Bioentity, List<GeneAnnotation>> geneProducts = dataProvider.getGeneProducts(bp);
		if (geneProducts.isEmpty()) {
			throw new Exception("No gene products found for the given process id: "+bp);
		}
		
		final OWLDataFactory f = model.getOWLDataFactory();
		final Relations relations = new Relations(f, curieHandler);
		
		// create bp
		Set<OWLAnnotation> bpAnnotations = null;
		final OWLNamedIndividual bpIndividual = manager.createIndividualNonReasoning(modelId, bp, bpAnnotations , metadata);
		
		// create gene products
		final Map<Bioentity, OWLNamedIndividual> gpIndividuals = new HashMap<>();
		for(Bioentity gp : geneProducts.keySet()) {
			List<GeneAnnotation> source = geneProducts.get(gp);
			
			// explicitly create OWL class for gene product
			final IRI gpIRI = curieHandler.getIRI(gp.getId());
			final OWLClass gpClass = f.getOWLClass(gpIRI);
			manager.addAxiom(model, f.getOWLDeclarationAxiom(gpClass), metadata);
			
			Set<OWLAnnotation> gpAnnotations = generateAnnotationAndEvidence(source, model, manager, metadata);
			OWLNamedIndividual gpIndividual = manager.createIndividualNonReasoning(modelId, gp.getId(), gpAnnotations, metadata);
			gpIndividuals.put(gp, gpIndividual);
		}
		
		Map<Bioentity, List<OWLNamedIndividual>> mfIndividuals = new HashMap<>();
		// add functions
		Map<Bioentity, List<GeneAnnotation>> functions = dataProvider.getFunctions(geneProducts.keySet());
		for(Bioentity gp : functions.keySet()) {
			List<GeneAnnotation> functionAnnotations = functions.get(gp);
			OWLNamedIndividual gpIndividual = gpIndividuals.get(gp);
			List<OWLNamedIndividual> mfIndividualList = new ArrayList<OWLNamedIndividual>(functionAnnotations.size());
			mfIndividuals.put(gp, mfIndividualList);
			
			// TODO choose one representative and preserve others as choice!
			// for now group to minimize mf individuals
			Map<String, List<GeneAnnotation>> mfGroups = removeRedundants(groupByCls(functionAnnotations), f);
			for(Entry<String, List<GeneAnnotation>> mfGroup : mfGroups.entrySet()) {
				String mf = mfGroup.getKey();
				Set<OWLAnnotation> mfAnnotations = generateAnnotationAndEvidence(mfGroup.getValue(),  model, manager, metadata);
				OWLNamedIndividual mfIndividual = manager.createIndividualNonReasoning(modelId, mf, mfAnnotations , metadata);
				mfIndividualList.add(mfIndividual);
				manager.addFact(model, relations.enabled_by, mfIndividual, gpIndividual, mfAnnotations, metadata);
				manager.addFact(model, relations.part_of, mfIndividual, bpIndividual, null, metadata);
				
				// TODO check c16 for 'occurs in'
			}
		}
		
//		// set GO:0003674 'molecular_function' for gp with unknown function
//		for(Bioentity gp : Sets.difference(geneProducts.keySet(), functions.keySet())) {
//			Pair<String, OWLNamedIndividual> gpIndividual = gpIndividuals.get(gp);
//			
//			Pair<String, OWLNamedIndividual> mfIndividual = manager.createIndividualNonReasoning(modelId, "GO:0003674", null, metadata);
//			mfIndividuals.put(gp, Collections.singletonList(mfIndividual));
//			manager.addFactNonReasoning(modelId, enabled_by_id, mfIndividual.getKey(), gpIndividual.getKey(), null, metadata);
//			manager.addFactNonReasoning(modelId, part_of_id, mfIndividual.getKey(), bpIndividual.getKey(), generateAnnotations(geneProducts.get(gp)), metadata);
//		}
		// remove individuals for gp with unknown function
		Set<Bioentity> unused = Sets.difference(geneProducts.keySet(), functions.keySet());
		for(Bioentity gp : unused) {
			OWLNamedIndividual gpIndividual = gpIndividuals.remove(gp);
			manager.deleteIndividual(modelId, gpIndividual, metadata);
		}
		
		// add locations
		Map<Bioentity, List<GeneAnnotation>> locations = dataProvider.getLocations(functions.keySet());
		for(Bioentity gp : locations.keySet()) {
			List<OWLNamedIndividual> relevantMfIndividuals = mfIndividuals.get(gp);
			if (relevantMfIndividuals == null) {
				continue;
			}
			List<GeneAnnotation> locationAnnotations = locations.get(gp);
			Map<String, List<GeneAnnotation>> locationGroups = removeRedundants(groupByCls(locationAnnotations), f);
			for(Entry<String, List<GeneAnnotation>> locationGroup : locationGroups.entrySet()) {
				String location = locationGroup.getKey();
				Set<OWLAnnotation> source = generateAnnotationAndEvidence(locationGroup.getValue(), model, manager, metadata);
				for(OWLNamedIndividual relevantMfIndividual : relevantMfIndividuals) {
					OWLNamedIndividual locationIndividual = manager.createIndividualNonReasoning(modelId, location, source, metadata);
					manager.addFact(model, relations.occurs_in, relevantMfIndividual, locationIndividual, source, metadata);
				}
			}
			
		}
		
		// add relations
		// TODO
	}
	
	static class Relations {
		final OWLObjectProperty part_of;
		final String part_of_id;
		final OWLObjectProperty enabled_by;
		final String enabled_by_id;
		final OWLObjectProperty occurs_in;
		final String occurs_in_id;
		
		Relations(OWLDataFactory f, CurieHandler curieHandler) {
			part_of = OBOUpperVocabulary.BFO_part_of.getObjectProperty(f);
			part_of_id = curieHandler.getCuri(part_of);
			occurs_in = OBOUpperVocabulary.BFO_occurs_in.getObjectProperty(f);
			occurs_in_id = curieHandler.getCuri(occurs_in);
			enabled_by = OBOUpperVocabulary.GOREL_enabled_by.getObjectProperty(f);
			enabled_by_id = curieHandler.getCuri(enabled_by);
		}
	}
	
	private Map<String, List<GeneAnnotation>> groupByCls(List<GeneAnnotation> annotations) {
		Map<String, List<GeneAnnotation>> groups = new HashMap<String, List<GeneAnnotation>>();
		for (GeneAnnotation annotation : annotations) {
			String cls = annotation.getCls();
			List<GeneAnnotation> group = groups.get(cls);
			if (group == null) {
				group = new ArrayList<GeneAnnotation>();
				groups.put(cls, group);
			}
			group.add(annotation);
		}
		return groups;
	}
	
	private Map<String, List<GeneAnnotation>> removeRedundants(Map<String, List<GeneAnnotation>> groups, final OWLDataFactory f) throws UnknownIdentifierException {
		// calculate all ancestors for each group
		Map<String, Set<String>> allAncestors = new HashMap<String, Set<String>>();
		for(String cls : groups.keySet()) {
			OWLClass owlCls = f.getOWLClass(curieHandler.getIRI(cls));
			Set<OWLClassExpression> superClassExpressions = reasoner.getSuperClassExpressions(owlCls, false);
			final Set<String> ancestors = new HashSet<String>();
			allAncestors.put(cls, ancestors);
			for (OWLClassExpression ce : superClassExpressions) {
				ce.accept(new OWLClassExpressionVisitorAdapter(){

					@Override
					public void visit(OWLClass desc) {
						ancestors.add(curieHandler.getCuri(desc));
					}

					@Override
					public void visit(OWLObjectSomeValuesFrom desc) {
						OWLClassExpression filler = desc.getFiller();
						filler.accept(new OWLClassExpressionVisitorAdapter(){
							@Override
							public void visit(OWLClass desc) {
								ancestors.add(curieHandler.getCuri(desc));
							}
							
						});
					}
					
				});
			}
		}
		// check that cls is not an ancestor in any other group
		Map<String, List<GeneAnnotation>> redundantFree = new HashMap<String, List<GeneAnnotation>>();
		for(String cls : groups.keySet()) {
			boolean nonRedundant = true;
			for(Entry<String, Set<String>> group : allAncestors.entrySet()) {
				if (group.getValue().contains(cls)) {
					nonRedundant = false;
					break;
				}
			}
			if (nonRedundant) {
				redundantFree.put(cls, groups.get(cls));
			}
		}
		
		return redundantFree;
	}
	
	private Set<OWLAnnotation> generateAnnotationAndEvidence(final List<GeneAnnotation> source, ModelContainer model, MolecularModelManager<METADATA> manager, final METADATA metadata) {
		final OWLDataFactory f = model.getOWLDataFactory();
		final OWLAnnotationProperty evidenceProperty = f.getOWLAnnotationProperty(AnnotationShorthand.evidence.getAnnotationProperty());
		final OWLAnnotationProperty sourceProperty = f.getOWLAnnotationProperty(AnnotationShorthand.source.getAnnotationProperty());
		final OWLAnnotationProperty withProperty = f.getOWLAnnotationProperty(AnnotationShorthand.with.getAnnotationProperty());
		final OWLAnnotationProperty contributorProperty = f.getOWLAnnotationProperty(AnnotationShorthand.contributor.getAnnotationProperty());
		Set<OWLAnnotation> annotations = new HashSet<OWLAnnotation>();
		
		if (source != null) {
			for (GeneAnnotation annotation : source) {
				OWLClass ecoCls = findEco(annotation, model);
				if (ecoCls != null) {
					final OWLNamedIndividual evidenceIndividual = manager.createIndividualNonReasoning(model, defaultAnnotations, metadata);
					manager.addType(model, evidenceIndividual, ecoCls, metadata);
					Set<OWLAnnotation> evidenceAnnotations = new HashSet<OWLAnnotation>();
					List<String> referenceIds = annotation.getReferenceIds();
					if (referenceIds != null) {
						for (String referenceId : referenceIds) {
							evidenceAnnotations.add(f.getOWLAnnotation(sourceProperty, f.getOWLLiteral(referenceId)));
						}
					}
					Collection<String> withInfos = annotation.getWithInfos();
					if (withInfos != null) {
						for (String withInfo : withInfos) {
							evidenceAnnotations.add(f.getOWLAnnotation(withProperty, f.getOWLLiteral(withInfo)));
						}
					}
					if (annotation.getAssignedBy() != null) {
						evidenceAnnotations.add(f.getOWLAnnotation(contributorProperty, f.getOWLLiteral(annotation.getAssignedBy())));
					}
					evidenceAnnotations.add(f.getOWLAnnotation(f.getRDFSComment(), f.getOWLLiteral("Generated from: "+annotation.toString())));
					manager.addAnnotations(model, evidenceIndividual, evidenceAnnotations , metadata);
					annotations.add(f.getOWLAnnotation(evidenceProperty, evidenceIndividual.getIRI()));
				}
			}
		}
		if (defaultAnnotations != null) {
			annotations.addAll(defaultAnnotations);
		}
		return annotations;
	}
	
	private OWLClass findEco(GeneAnnotation annotation, ModelContainer model) {
		OWLClass result = null;
		String ecoId = annotation.getEcoEvidenceCls();
		String goCode = annotation.getShortEvidence();
		if (ecoId == null && goCode != null) {
			List<String> referenceIds = annotation.getReferenceIds();
			if (referenceIds != null) {
				ecoId = ecoMapper.getEco(goCode, referenceIds);
			}
			if (ecoId == null) {
				ecoId = ecoMapper.getEco(goCode, (String) null);
			}
		}
		if (ecoId != null) {
			IRI ecoIRI;
			try {
				ecoIRI = curieHandler.getIRI(ecoId);
			} catch (UnknownIdentifierException e) {
				ecoIRI = null;
			}
			if (ecoIRI != null) {
				result = model.getOWLDataFactory().getOWLClass(ecoIRI);
			}
		}
		return result;
	}
}
