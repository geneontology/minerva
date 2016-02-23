package org.geneontology.minerva.legacy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.evidence.FindGoCodes;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.geneontology.minerva.taxon.FindTaxonTool;
import org.obolibrary.obo2owl.Obo2Owl;
import org.obolibrary.obo2owl.Owl2Obo;
import org.obolibrary.oboformat.parser.OBOFormatConstants.OboFormatTag;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValueVisitorEx;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import owltools.gaf.Bioentity;
import owltools.gaf.BioentityDocument;
import owltools.gaf.ExtensionExpression;
import owltools.gaf.GafDocument;
import owltools.gaf.GeneAnnotation;
import owltools.gaf.eco.SimpleEcoMapper;
import owltools.graph.OWLGraphWrapper;
import owltools.vocab.OBOUpperVocabulary;
import uk.ac.manchester.cs.owl.owlapi.ImplUtils;

abstract class AbstractLegoTranslator extends LegoModelWalker<AbstractLegoTranslator.Summary> {

	protected final OWLClass mf;
	protected final Set<OWLClass> mfSet;

	protected final OWLClass cc;
	protected final Set<OWLClass> ccSet;

	protected final OWLClass bp;
	protected final Set<OWLClass> bpSet;

	protected final FindGoCodes goCodes;
	protected final CurieHandler curieHandler;

	protected String assignedBy;

	protected AbstractLegoTranslator(OWLOntology model, CurieHandler curieHandler, SimpleEcoMapper mapper) {
		super(model.getOWLOntologyManager().getOWLDataFactory());
		this.curieHandler = curieHandler;
		goCodes = new FindGoCodes(mapper, curieHandler);

		mf = OBOUpperVocabulary.GO_molecular_function.getOWLClass(f);
		cc = f.getOWLClass(curieHandler.getIRI("GO:0005575"));
		bp = OBOUpperVocabulary.GO_biological_process.getOWLClass(f);
	
		bpSet = new HashSet<>();
		mfSet = new HashSet<>();
		ccSet = new HashSet<>();
	
		fillAspects(model, curieHandler, bpSet, mfSet, ccSet);

		assignedBy = "GO_Noctua";
	}

	static void fillAspects(OWLOntology model, CurieHandler curieHandler, Set<OWLClass> bpSet, Set<OWLClass> mfSet, Set<OWLClass> ccSet) {
		final IRI namespaceIRI = Obo2Owl.trTagToIRI(OboFormatTag.TAG_NAMESPACE.getTag());
		final OWLDataFactory df = model.getOWLOntologyManager().getOWLDataFactory();
		final OWLAnnotationProperty namespaceProperty = df.getOWLAnnotationProperty(namespaceIRI);
		final Set<OWLOntology> ontologies = model.getImportsClosure();
		for(OWLClass cls : model.getClassesInSignature(true)) {
			if (cls.isBuiltIn()) {
				continue;
			}
			String id = curieHandler.getCuri(cls);
			if (id.startsWith("GO:") == false) {
				continue;
			}
			for (OWLAnnotationAssertionAxiom ax : ImplUtils.getAnnotationAxioms(cls, ontologies)) {
				OWLAnnotation annotation = ax.getAnnotation();
				if (annotation.getProperty().equals(namespaceProperty)) {
					String value = annotation.getValue().accept(new OWLAnnotationValueVisitorEx<String>() {

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
						if ("molecular_function".equals(value)) {
							mfSet.add(cls);
						}
						else if ("biological_process".equals(value)) {
							bpSet.add(cls);
						}
						else if ("cellular_component".equals(value)) {
							ccSet.add(cls);
						}
					}
				}
			}
		}
	}
	
	protected static Set<OWLClass> getAllSubClasses(OWLClass cls, OWLReasoner r, boolean reflexive, String idSpace, CurieHandler curieHandler) {
		Set<OWLClass> allSubClasses = r.getSubClasses(cls, false).getFlattened();
		Iterator<OWLClass> it = allSubClasses.iterator();
		while (it.hasNext()) {
			OWLClass current = it.next();
			if (current.isBuiltIn()) {
				it.remove();
				continue;
			}
			String id = curieHandler.getCuri(current);
			if (id.startsWith(idSpace) == false) {
				it.remove();
				continue;
			}
		}
		if (reflexive) {
			allSubClasses.add(cls);
		}
		return allSubClasses;
	}

	protected class Summary {

		Set<Entry<OWLClass>> activities = null;
		Set<Entry<OWLClass>> locations = null;
		Set<Entry<OWLClass>> processes = null;
		OWLClass entity = null;
		String entityType = null;
		String entityTaxon = null;

		boolean addMf(OWLClass cls, Metadata metadata, Set<OWLObjectSomeValuesFrom> expressions) {
			if (isMf(cls)) {
				activities = addAnnotation(cls, metadata, expressions, activities);
				return true;
			}
			return false;
		}

		boolean addBp(OWLClass cls, Metadata metadata, Set<OWLObjectSomeValuesFrom> expressions) {
			if (isBp(cls)) {
				processes = addAnnotation(cls, metadata, expressions, processes);
				return true;
			}
			return false;
		}

		boolean addCc(OWLClass cls, Metadata metadata, Set<OWLObjectSomeValuesFrom> expressions) {
			if (isCc(cls)) {
				locations = addAnnotation(cls, metadata, expressions, locations);
				return true;
			}
			return false;
		}

		private <T> Set<Entry<T>> addAnnotation(T cls, Metadata metadata, Set<OWLObjectSomeValuesFrom> expressions, Set<Entry<T>> set) {
			if (set == null) {
				set = new HashSet<Entry<T>>();
			}
			Entry<T> entry = new Entry<T>();
			entry.value = cls;
			entry.metadata = metadata.copy();
			if (expressions != null) {
				entry.expressions = expressions;
			}
			set.add(entry);
			return set;
		}

		void addProcesses(Set<Entry<OWLClass>> processes, Metadata metadata) {
			if (processes != null) {
				if (this.processes == null) {
					this.processes = new HashSet<Entry<OWLClass>>();
				}
				for(Entry<OWLClass> process : processes) {
					Entry<OWLClass> newEntry = new Entry<OWLClass>();
					newEntry.value = process.value;
					newEntry.metadata = Metadata.combine(metadata, process.metadata);
					this.processes.add(newEntry);
				}
			}
		}

		void addLocations(Set<Entry<OWLClass>> locations) {
			if (locations != null) {
				if (this.locations == null) {
					this.locations = new HashSet<Entry<OWLClass>>(locations);
				}
				else {
					this.locations.addAll(locations);
				}
			}
		}
	}

	protected boolean isMf(OWLClass cls) {
		return mfSet.contains(cls);
	}

	protected boolean isBp(OWLClass cls) {
		return bpSet.contains(cls);
	}

	protected boolean isCc(OWLClass cls) {
		return ccSet.contains(cls);
	}

	public abstract void translate(OWLOntology modelAbox, ExternalLookupService lookup, GafDocument annotations, BioentityDocument entities, List<String> additionalRefs);

	/**
	 * Get the type of an enabled by entity, e.g. gene, protein
	 * 
	 * @param modelGraph 
	 * @param entity 
	 * @param individual
	 * @param lookup
	 * @return type
	 */
	protected String getEntityType(OWLClass entity, OWLNamedIndividual individual, OWLGraphWrapper modelGraph, ExternalLookupService lookup) {
		List<LookupEntry> result = lookup.lookup(entity.getIRI());
		if (result.isEmpty() == false) {
			LookupEntry entry = result.get(0);
			if ("protein".equalsIgnoreCase(entry.type)) {
				return "protein";
			}
			else if ("gene".equalsIgnoreCase(entry.type)) {
				return "gene";
			}
		}
		return "gene";
	}

	protected String getEntityTaxon(OWLClass entity, OWLOntology model) {
		if (entity == null) {
			return null;
		}
		FindTaxonTool tool = new FindTaxonTool(curieHandler, model.getOWLOntologyManager().getOWLDataFactory());
		return tool.getEntityTaxon(curieHandler.getCuri(entity), model);
	}

	public Pair<GafDocument, BioentityDocument> translate(String id, OWLOntology modelAbox, ExternalLookupService lookup, List<String> additionalReferences) {
		final GafDocument annotations = new GafDocument(id, null);
		final BioentityDocument entities = new BioentityDocument(id);
		translate(modelAbox, lookup, annotations, entities, additionalReferences);
		return Pair.of(annotations, entities);
	}

	protected GeneAnnotation createAnnotation(Entry<OWLClass> e, Bioentity entity, String aspect,
			List<String> additionalReferences,
			OWLGraphWrapper g, Collection<OWLObjectSomeValuesFrom> c16) {
		GeneAnnotation annotation = new GeneAnnotation();
		annotation.setBioentityObject(entity);
		annotation.setBioentity(entity.getId());
		annotation.setAspect(aspect);
		annotation.setAssignedBy(assignedBy);
		annotation.setCls(curieHandler.getCuri(e.value));
		
		if (e.metadata.modelId != null) {
			annotation.addProperty("lego-model-id", e.metadata.modelId);
		}
		if (e.metadata.contributors != null) {
			for(String contributor : e.metadata.contributors) {
				annotation.addProperty("contributor", contributor);
			}
		}
		if (e.metadata.individualIds != null) {
			for(IRI individual : e.metadata.individualIds) {
				annotation.addProperty("individual", individual.toString());
			}
		}

		if (e.metadata.evidence != null) {
			String ecoId = curieHandler.getCuri(e.metadata.evidence);
			if (ecoId != null) {
				String goCode = null;
				Pair<String, String> pair = goCodes.findShortEvidence(e.metadata.evidence, ecoId, g.getSourceOntology());
				if (pair != null) {
					goCode = pair.getLeft();
					String goRef = pair.getRight();
					if (goRef != null) {
						if (additionalReferences == null) {
							additionalReferences = Collections.singletonList(goRef);
						}
						else {
							additionalReferences = new ArrayList<String>(additionalReferences);
							additionalReferences.add(goRef);
						}
					}
				}
				annotation.setEvidence(goCode, ecoId);
			}
		}
		if (e.metadata.date != null) {
			// assumes that the date is YYYY-MM-DD
			// gene annotations require YYYYMMDD
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < e.metadata.date.length(); i++) {
				char c = e.metadata.date.charAt(i);
				if (Character.isDigit(c)) {
					sb.append(c);
				}
			}
			annotation.setLastUpdateDate(sb.toString());
		}
		
		if (e.metadata.with != null) {
			List<String> withInfos = new ArrayList<>(e.metadata.with);
			annotation.setWithInfos(withInfos);
		}

		String relation = "enables";
		if ("C".equals(aspect)) {
			relation = "part_of";
		}
		else if ("P".equals(aspect)) {
			relation = "involved_in";
		}
		annotation.setRelation(relation);
		if (e.metadata.sources != null) {
			annotation.addReferenceIds(e.metadata.sources);
		}
		if (additionalReferences != null) {
			for (String ref : additionalReferences) {
				annotation.addReferenceId(ref);
			}
		}

		if (c16 != null && !c16.isEmpty()) {
			List<ExtensionExpression> expressions = new ArrayList<ExtensionExpression>();
			for (OWLObjectSomeValuesFrom svf : c16) {
				OWLObjectPropertyExpression property = svf.getProperty();
				OWLClassExpression filler = svf.getFiller();
				if (property instanceof OWLObjectProperty && filler instanceof OWLClass) {
					String rel = getRelId(property, g);
					String objectId = curieHandler.getCuri((OWLClass) filler);
					ExtensionExpression expr = new ExtensionExpression(rel, objectId);
					expressions.add(expr);
				}
			}
			annotation.setExtensionExpressions(Collections.singletonList(expressions));
		}
		
		return annotation;
	}

	protected String getRelId(OWLObjectPropertyExpression p, OWLGraphWrapper graph) {
		String relId = null;
		for(OWLOntology ont : graph.getAllOntologies()) {
			relId = Owl2Obo.getIdentifierFromObject(p, ont, null);
			if (relId != null && relId.indexOf(':') < 0) {
				return relId;
			}
		}
		return relId;
	}

	protected Bioentity createBioentity(OWLClass entityCls, String entityType, String taxon, OWLGraphWrapper g, ExternalLookupService lookup) {
		Bioentity bioentity = new Bioentity();
		BioentityStrings strings = getBioentityStrings(entityCls, entityType, taxon, g, lookup);
		String id = strings.id;
		bioentity.setId(id);
		if (strings.db != null) {
			bioentity.setDb(strings.db);
		}
		bioentity.setSymbol(strings.symbol);
		bioentity.setTypeCls(strings.type);
		if (taxon != null) {
			bioentity.setNcbiTaxonId(taxon);	
		}
		return bioentity;
	}

	protected static class BioentityStrings {
		String id;
		String db;
		String symbol;
		String type;
	}

	protected BioentityStrings getBioentityStrings(OWLClass entityCls, String entityType, String taxon, OWLGraphWrapper g, ExternalLookupService lookup) {
		BioentityStrings strings = new BioentityStrings();
		strings.id = curieHandler.getCuri(entityCls);
		strings.db = null;
		String[] split = StringUtils.split(strings.id, ":", 2);
		if (split.length == 2) {
			strings.db = split[0];
		}
		strings.symbol = getLabelForBioentity(entityCls, entityType, taxon, g, lookup);
		strings.type = entityType;
		return strings;
	}

	private String getLabelForBioentity(OWLClass entityCls, String entityType, String taxon, OWLGraphWrapper g, ExternalLookupService lookup) {
		String lbl = g.getLabel(entityCls);
		if (lbl == null && lookup != null) {
			List<LookupEntry> result = lookup.lookup(entityCls.getIRI());
			if (!result.isEmpty()) {
				LookupEntry entry = result.get(0);
				lbl = entry.label;
			}
		}
		return lbl;
	}

	protected void addAnnotations(OWLGraphWrapper modelGraph, ExternalLookupService lookup,
			Summary summary, List<String> additionalRefs,
			GafDocument annotations, BioentityDocument entities) 
	{
		Bioentity entity = createBioentity(summary.entity, summary.entityType, summary.entityTaxon , modelGraph, lookup);
		entities.addBioentity(entity);
		annotations.addBioentity(entity);
		
		if (summary.activities != null) {
			for (Entry<OWLClass> e: summary.activities) {
				boolean renderActivity = true;
				if (mf.equals(e.value)) {
					// special handling for top level molecular functions
					// only add as annotation, if there is more than one annotation
					// otherwise they tend to be redundant with the bp or cc annotation
					if (e.expressions == null || e.expressions.isEmpty()) {
						renderActivity = false;
					}
				}
				if (renderActivity) {
					GeneAnnotation annotation = createAnnotation(e, entity, "F", additionalRefs, modelGraph, e.expressions);
					annotations.addGeneAnnotation(annotation);
				}
			}
		}
		if (summary.processes != null) {
			for (Entry<OWLClass> e : summary.processes) {
				GeneAnnotation annotation = createAnnotation(e, entity, "P", additionalRefs, modelGraph, e.expressions);
				annotations.addGeneAnnotation(annotation);
			}
		}
		if (summary.locations != null) {
			for (Entry<OWLClass> e : summary.locations) {
				if (isCc(e.value)) {
					GeneAnnotation annotation = createAnnotation(e, entity, "C", additionalRefs, modelGraph, e.expressions);
					annotations.addGeneAnnotation(annotation);
				}
			}
		}
	}
}
