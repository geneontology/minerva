package org.geneontology.minerva.legacy;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.util.OWLClassExpressionVisitorAdapter;

import owltools.graph.OWLGraphWrapper;
import owltools.vocab.OBOUpperVocabulary;

import com.google.common.collect.Sets;

abstract class LegoModelWalker<PAYLOAD> {

	protected final OWLObjectProperty partOf;
	protected final OWLObjectProperty occursIn;
	protected final OWLObjectProperty enabledBy;

	protected final OWLAnnotationProperty source;
	protected final OWLAnnotationProperty contributor;
	protected final OWLAnnotationProperty date;
	protected final OWLAnnotationProperty evidence;
	protected final OWLAnnotationProperty with;

	protected final OWLDataFactory f;

	protected LegoModelWalker(OWLDataFactory df) {
		this.f = df;
		
		partOf = OBOUpperVocabulary.BFO_part_of.getObjectProperty(f);
		occursIn = OBOUpperVocabulary.BFO_occurs_in.getObjectProperty(f);
	
	
		enabledBy = OBOUpperVocabulary.GOREL_enabled_by.getObjectProperty(f);
	
		source = f.getOWLAnnotationProperty(AnnotationShorthand.source.getAnnotationProperty());
		contributor = f.getOWLAnnotationProperty(AnnotationShorthand.contributor.getAnnotationProperty());
		date = f.getOWLAnnotationProperty(AnnotationShorthand.date.getAnnotationProperty());
		evidence = f.getOWLAnnotationProperty(AnnotationShorthand.evidence.getAnnotationProperty());
		with = f.getOWLAnnotationProperty(AnnotationShorthand.with.getAnnotationProperty());
	}

	protected static class Entry<T> {
		T value;
		Metadata metadata;
		Set<OWLObjectSomeValuesFrom> expressions;
		// TODO multi-species interactions
	}

	protected static class Metadata {
		OWLClass evidence = null;
		Set<String> contributors = null;
		String date = null;
		Set<String> sources = null;
		Set<String> with = null;

		Metadata copy() {
			Metadata metadata = new Metadata();
			metadata.evidence = this.evidence;
			metadata.contributors = copy(this.contributors);
			metadata.date = this.date;
			metadata.sources = copy(this.sources);
			metadata.with = copy(this.with);
			return metadata;
		}

		private static Set<String> copy(Set<String> c) {
			if (c == null) {
				return null;
			}
			return new HashSet<String>(c);
		}

		static Metadata combine(Metadata primary, Metadata secondary) {
			if (primary.evidence != null) {
				return primary.copy();
			}
			if (secondary.evidence != null) {
				return secondary.copy();
			}
			if (primary.sources != null && !primary.sources.isEmpty()) {
				return primary.copy();
			}
			if (primary.with != null && !primary.with.isEmpty()) {
				return primary.copy();
			}
			return secondary.copy();
		}

		static Metadata mergeMetadata(Metadata...data) {
			Metadata result = null;
			for (Metadata metadata : data) {
				if (metadata != null) {
					if (result == null) {
						result = metadata.copy();
					}
					else {
						if (result.evidence == null && metadata.evidence != null) {
							Metadata oldResult = result;
							result = metadata.copy();
							if (oldResult.sources != null && !oldResult.sources.isEmpty()) {
								if (result.sources == null) {
									result.sources = Sets.newHashSet(oldResult.sources);
								}
								else {
									result.sources.addAll(oldResult.sources);
								}
							}
							if (oldResult.with != null && !oldResult.with.isEmpty()) {
								if (result.with == null) {
									result.with = Sets.newHashSet(oldResult.with);
								}
								else {
									result.with.addAll(oldResult.with);
								}
							}
						}
					}
				}
			}
			return result;
		}
	}

	public void walkModel(OWLOntology model, ExternalLookupService lookup, Collection<PAYLOAD> allPayloads) {
		final OWLGraphWrapper modelGraph = new OWLGraphWrapper(model);

		final Set<OWLNamedIndividual> annotationIndividuals = new HashSet<OWLNamedIndividual>();
		final Map<IRI, Metadata> evidenceIndividuals = new HashMap<IRI, Metadata>();

		for(OWLNamedIndividual individual : model.getIndividualsInSignature()) {
			Set<OWLClass> individualTypes = getTypes(individual, model);
			OWLClass eco = getEco(individualTypes);
			if (eco != null) {
				// is eco
				Metadata metadata = extractMetadata(individual, modelGraph, null);
				metadata.evidence = eco;
				evidenceIndividuals.put(individual.getIRI(), metadata);
			}
			else {
				// assume annotation (for now)
				annotationIndividuals.add(individual);
			}
		}

		final Map<OWLNamedIndividual,Metadata> allMetadata = new HashMap<OWLNamedIndividual, Metadata>();
		for(OWLNamedIndividual individual : annotationIndividuals) {
			Metadata metadata = extractMetadata(individual, modelGraph, evidenceIndividuals);
			allMetadata.put(individual, metadata);
		}

		for (OWLObjectPropertyAssertionAxiom axiom : model.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
			final OWLObjectPropertyExpression p = axiom.getProperty();
			if (enabledBy.equals(p)) {
				// gene/protein/complex
				final OWLNamedIndividual object = axiom.getObject().asOWLNamedIndividual();
				Set<OWLObjectSomeValuesFrom> expressions = getSvfTypes(object, model);
				Set<OWLClass> objectTypes = getTypes(object, model);
				for (OWLClass objectType : objectTypes) {
					final PAYLOAD payload = initPayload(object, objectType, model, modelGraph, lookup);
					allPayloads.add(payload);

					final OWLNamedIndividual subject = axiom.getSubject().asOWLNamedIndividual();

					// get associated meta data
					final Metadata linkMetadata = extractMetadata(axiom.getAnnotations(), modelGraph, evidenceIndividuals);
					final Metadata objectMetadata = allMetadata.get(object);
					final Metadata subjectMetadata = allMetadata.get(subject);
					final Metadata mfMetadata = Metadata.mergeMetadata(linkMetadata, objectMetadata, subjectMetadata);

					// get all OWLObjectPropertyAssertionAxiom for subject
					Set<OWLObjectPropertyAssertionAxiom> subjectAxioms = model.getObjectPropertyAssertionAxioms(subject);
					for(OWLObjectPropertyAssertionAxiom current : subjectAxioms) {
						final Metadata currentLinkMetadata = extractMetadata(current.getAnnotations(), modelGraph, evidenceIndividuals);
						final OWLObjectPropertyExpression currentP = current.getProperty();
						final OWLNamedIndividual currentObj = current.getObject().asOWLNamedIndividual();
						final Metadata currentObjMetadata = allMetadata.get(currentObj);
						if (occursIn.equals(currentP)) {
							// check for cc for subject (occurs in)
							final Metadata metadata = Metadata.mergeMetadata(currentObjMetadata, currentLinkMetadata);
							for(OWLClass cls : getTypes(currentObj, model)) {
								boolean added = handleCC(payload, cls, metadata, getExpressions(currentObj, model));
								if (!added) {
									expressions.add(createSvf(occursIn, cls));
								}
							}
						}
						else if (partOf.equals(currentP)) {
							// check for bp for subject (part_of)
							final Metadata metadata = Metadata.mergeMetadata(currentObjMetadata, currentLinkMetadata);
							for(OWLClass cls : getTypes(currentObj, model)) {
								boolean added = handleBP(payload, cls, metadata, getExpressions(currentObj, model));;
								if (!added) {
									expressions.add(createSvf(partOf, cls));
								}
							}
							
						}else if (enabledBy.equals(currentP)) {
							// do nothing
						}
						else {
							Set<OWLClass> types = getTypes(currentObj, model);
							for (OWLClass cls : types) {
								expressions.add(createSvf(currentP, cls));
							}
						}
					}

					// handle types
					for(OWLClass cls : getTypes(subject, model)) {
						handleMF(payload, cls, mfMetadata, expressions);
					}
				}
			}
		}
	}
	
	private Set<OWLObjectSomeValuesFrom> getSvfTypes(OWLNamedIndividual i, OWLOntology model) {
		Set<OWLClassAssertionAxiom> axioms = model.getClassAssertionAxioms(i);
		final Set<OWLObjectSomeValuesFrom> svfs = new HashSet<OWLObjectSomeValuesFrom>();
		for (OWLClassAssertionAxiom axiom : axioms) {
			axiom.getClassExpression().accept(new OWLClassExpressionVisitorAdapter(){

				@Override
				public void visit(OWLObjectSomeValuesFrom svf) {
					svfs.add(svf);
				}
			});
		}
		return svfs;
	}
	
	protected abstract boolean isEco(OWLClass cls);

	private OWLClass getEco(Set<OWLClass> set) {
		for (OWLClass cls : set) {
			if (isEco(cls)) {
				return cls;
			}
		}
		return null;
	}

	private Set<OWLClass> getTypes(OWLNamedIndividual i, OWLOntology model) {
		Set<OWLClassAssertionAxiom> axioms = model.getClassAssertionAxioms(i);
		Set<OWLClass> types = new HashSet<OWLClass>();
		for (OWLClassAssertionAxiom axiom : axioms) {
			OWLClassExpression ce = axiom.getClassExpression();
			if (ce instanceof OWLClass) {
				OWLClass cls = ce.asOWLClass();
				if (cls.isBuiltIn() == false) {
					types.add(cls);
				}
			}
		}
		return types;
	}
	
	private Set<OWLObjectSomeValuesFrom> getExpressions(OWLNamedIndividual i, OWLOntology model) {
		Set<OWLObjectSomeValuesFrom> result = new HashSet<OWLObjectSomeValuesFrom>();
		Set<OWLObjectPropertyAssertionAxiom> axioms = model.getObjectPropertyAssertionAxioms(i);
		for (OWLObjectPropertyAssertionAxiom ax : axioms) {
			if (enabledBy.equals(ax.getProperty())) {
				continue;
			}
			OWLIndividual object = ax.getObject();
			if (object.isNamed()) {
				Set<OWLClass> types = getTypes(object.asOWLNamedIndividual(), model);
				for (OWLClass cls : types) {
					result.add(createSvf(ax.getProperty(), cls));
				}
			}
		}
		return result;
	}
	
	protected abstract PAYLOAD initPayload(OWLNamedIndividual object, OWLClass objectType, OWLOntology model, OWLGraphWrapper modelGraph, ExternalLookupService lookup);
	
	protected abstract boolean handleCC(PAYLOAD payload, OWLClass cls, Metadata metadata, Set<OWLObjectSomeValuesFrom> expressions);
	
	protected abstract boolean handleMF(PAYLOAD payload, OWLClass cls, Metadata metadata, Set<OWLObjectSomeValuesFrom> expressions);
	
	protected abstract boolean handleBP(PAYLOAD payload, OWLClass cls, Metadata metadata, Set<OWLObjectSomeValuesFrom> expressions);
	
	private OWLObjectSomeValuesFrom createSvf(OWLObjectPropertyExpression p, OWLClass c) {
		return f.getOWLObjectSomeValuesFrom(p, c);
	}
	
	private Metadata extractMetadata(OWLNamedIndividual individual, OWLGraphWrapper modelGraph, Map<IRI, Metadata> allEvidences) {
		Metadata metadata = new Metadata();
		Set<OWLAnnotationAssertionAxiom> assertionAxioms = modelGraph.getSourceOntology().getAnnotationAssertionAxioms(individual.getIRI());
		for (OWLAnnotationAssertionAxiom axiom : assertionAxioms) {
			OWLAnnotationProperty currentProperty = axiom.getProperty();
			OWLAnnotationValue value = axiom.getValue();
			extractMetadata(currentProperty, value, metadata, allEvidences);
		}
		return metadata;
	}

	private void extractMetadata(OWLAnnotationProperty p, OWLAnnotationValue v, final Metadata metadata, Map<IRI, Metadata> allEvidences) {
		if (this.evidence.equals(p) && allEvidences != null && metadata.evidence == null) {
			Metadata evidenceMetadata = allEvidences.get(v);
			if (evidenceMetadata != null) {
				metadata.evidence = evidenceMetadata.evidence;
				metadata.sources = evidenceMetadata.sources;
				metadata.with = evidenceMetadata.with;
			}
		}
		else if (this.contributor.equals(p)) {
			if (v instanceof OWLLiteral) {
				String contributor = ((OWLLiteral) v).getLiteral();
				if (metadata.contributors == null) {
					metadata.contributors = new HashSet<String>();
				}
				metadata.contributors.add(contributor);
			}
		}
		else if (this.date.equals(p)) {
			if (v instanceof OWLLiteral) {
				metadata.date = ((OWLLiteral) v).getLiteral();
			}
		}
		else if (this.source.equals(p)) {
			if (v instanceof OWLLiteral) {
				String source = ((OWLLiteral) v).getLiteral();
				if (metadata.sources == null) {
					metadata.sources = new HashSet<String>();
				}
				metadata.sources.add(source);
			}
		}
		else if (this.with.equals(p)) {
			if (v instanceof OWLLiteral) {
				String with = ((OWLLiteral) v).getLiteral();
				if (metadata.with == null) {
					metadata.with = new HashSet<String>();
				}
				metadata.with.add(with);
			}
		}
	}

	private Metadata extractMetadata(Collection<OWLAnnotation> annotations, OWLGraphWrapper modelGraph, Map<IRI, Metadata> allEvidences) {
		Metadata metadata = new Metadata();
		if (annotations != null && !annotations.isEmpty()) {
			for (OWLAnnotation owlAnnotation : annotations) {
				OWLAnnotationProperty currentProperty = owlAnnotation.getProperty();
				OWLAnnotationValue value = owlAnnotation.getValue();
				extractMetadata(currentProperty, value, metadata, allEvidences);
			}
		}
		return metadata;
	}

}
