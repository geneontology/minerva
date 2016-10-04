package org.geneontology.minerva.legacy;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.obolibrary.obo2owl.Obo2OWLConstants;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAnnotationValueVisitorEx;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
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

abstract class LegoModelWalker<PAYLOAD> {

	protected final OWLObjectProperty partOf;
	protected final OWLObjectProperty occursIn;
	protected final OWLObjectProperty enabledBy;
	protected final OWLObjectProperty hasSupportingRef;
	protected final OWLObjectProperty withSupportFrom;

	protected final OWLAnnotationProperty source_old;
	protected final OWLAnnotationProperty contributor;
	protected final OWLAnnotationProperty group;
	protected final OWLAnnotationProperty date;
	protected final OWLAnnotationProperty evidenceOld;
	protected final OWLAnnotationProperty axiomHasEvidence;
	protected final OWLAnnotationProperty with_old;

	private final OWLAnnotationProperty shortIdProp;

	protected final OWLDataFactory f;

	protected LegoModelWalker(OWLDataFactory df) {
		this.f = df;

		partOf = OBOUpperVocabulary.BFO_part_of.getObjectProperty(f);
		occursIn = OBOUpperVocabulary.BFO_occurs_in.getObjectProperty(f);

		enabledBy = OBOUpperVocabulary.GOREL_enabled_by.getObjectProperty(f);

		shortIdProp = df.getOWLAnnotationProperty(IRI.create(Obo2OWLConstants.OIOVOCAB_IRI_PREFIX+"id"));

		contributor = f.getOWLAnnotationProperty(AnnotationShorthand.contributor.getAnnotationProperty());
		date = f.getOWLAnnotationProperty(AnnotationShorthand.date.getAnnotationProperty());
		group = f.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/group")); // TODO place holder
		
		axiomHasEvidence = f.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/RO_0002612"));
		hasSupportingRef = f.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/SEPIO_0000124"));
		withSupportFrom = f.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/RO_0002614"));

		evidenceOld = f.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/evidence"));
		source_old = f.getOWLAnnotationProperty(AnnotationShorthand.source.getAnnotationProperty());
		with_old = f.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/evidence-with"));
	}

	protected static class Entry<T> {
		T value;
		Metadata metadata;
		List<Evidence> evidences;
		Set<OWLObjectSomeValuesFrom> expressions;
		// TODO multi-species interactions
	}

	protected static class Evidence {
		OWLClass evidenceCls = null;
		String source = null;
		String with = null;
		
		Evidence copy() {
			Evidence evidence = new Evidence();
			evidence.evidenceCls = this.evidenceCls;
			evidence.source = this.source;
			evidence.with = this.with;
			return evidence;
		}
	}

	protected static class Metadata {

		String modelId = null;
		Set<IRI> individualIds = null;
		Set<String> contributors = null;
		Set<String> groups = null;
		String date = null;
	}

	public void walkModel(OWLOntology model, ExternalLookupService lookup, Collection<PAYLOAD> allPayloads) throws UnknownIdentifierException {
		final OWLGraphWrapper modelGraph = new OWLGraphWrapper(model);
		
		String modelId = null;
		for(OWLAnnotation modelAnnotation : model.getAnnotations()) {
			if (shortIdProp.equals(modelAnnotation.getProperty())) {
				modelId = modelAnnotation.getValue().accept(new OWLAnnotationValueVisitorEx<String>() {

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
			}
		}

		final Set<OWLNamedIndividual> annotationIndividuals = new HashSet<OWLNamedIndividual>();
		final Map<IRI, Evidence> evidenceIndividuals = new HashMap<IRI, Evidence>();

		for(OWLNamedIndividual individual : model.getIndividualsInSignature()) {
			Set<OWLClass> individualTypes = getTypes(individual, model);
			OWLClass eco = getEco(individualTypes);
			if (eco != null) {
				// is eco
				Evidence evidence = assembleEvidence(individual, eco, model);
				evidenceIndividuals.put(individual.getIRI(), evidence);
			}
			else if (isAnnotationIndividual(individual, individualTypes)) {
				annotationIndividuals.add(individual);
			}
		}

		final Map<OWLNamedIndividual,Metadata> allMetadata = new HashMap<OWLNamedIndividual, Metadata>();
		for(OWLNamedIndividual individual : annotationIndividuals) {
			Metadata metadata = extractMetadata(individual, modelGraph, modelId);
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
					final Metadata linkMetadata = extractMetadata(axiom.getAnnotations(), modelGraph, modelId);
					final Set<Evidence> linkEvidences = getEvidences(axiom, evidenceIndividuals);

					// get all OWLObjectPropertyAssertionAxiom for subject
					Set<OWLObjectPropertyAssertionAxiom> subjectAxioms = model.getObjectPropertyAssertionAxioms(subject);
					for(OWLObjectPropertyAssertionAxiom current : subjectAxioms) {
						final Metadata currentMetadata = extractMetadata(current.getAnnotations(), modelGraph, modelId);
						final Set<Evidence> currentEvidences = getEvidences(current, evidenceIndividuals);
						final OWLObjectPropertyExpression currentP = current.getProperty();
						final OWLNamedIndividual currentObj = current.getObject().asOWLNamedIndividual();
						
						if (occursIn.equals(currentP)) {
							// check for cc for subject (occurs in)
							for(OWLClass cls : getTypes(currentObj, model)) {
								boolean added = handleCC(payload, cls, currentMetadata, currentEvidences, getExpressions(currentObj, model));
								if (!added) {
									expressions.add(createSvf(occursIn, cls));
								}
							}
						}
						else if (partOf.equals(currentP)) {
							// check for bp for subject (part_of)
							for(OWLClass cls : getTypes(currentObj, model)) {
								boolean added = handleBP(payload, cls, currentMetadata, currentEvidences, getExpressions(currentObj, model));;
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
						handleMF(payload, cls, linkMetadata, linkEvidences, expressions);
					}
				}
			}
		}
	}
	
	private Evidence assembleEvidence(OWLNamedIndividual individual, OWLClass eco, OWLOntology model) {
		Evidence evidence = new Evidence();
		evidence.evidenceCls = eco;
		evidence.source = null;
		evidence.with = null;
		Set<OWLObjectPropertyAssertionAxiom> evidenceLinks = model.getObjectPropertyAssertionAxioms(individual);
		for(OWLObjectPropertyAssertionAxiom ax : evidenceLinks) {
			OWLObjectPropertyExpression p = ax.getProperty();
			if (hasSupportingRef.equals(p)) {
				OWLIndividual object = ax.getObject();
				if (object.isNamed()) {
					OWLNamedIndividual namedIndividual = object.asOWLNamedIndividual();
					evidence.source = getShortHand(namedIndividual.getIRI());
				}
			}
			else if (withSupportFrom.equals(p)) {
				OWLIndividual object = ax.getObject();
				if (object.isNamed()) {
					Set<OWLClass> types = getTypes(object.asOWLNamedIndividual(), model);
					for (OWLClass cls : types) {
						evidence.with = getShortHand(cls.getIRI());
					}
				}
			}
		}
		if (evidence.source == null) {
			// check old type of modelling as annotations
			for (OWLAnnotationAssertionAxiom annotation : model.getAnnotationAssertionAxioms(individual.getIRI())) {
				OWLAnnotationProperty p = annotation.getProperty();
				if (source_old.equals(p)) {
					evidence.source = getStringValue(annotation);
				}
				else if (with_old.equals(p)) {
					evidence.with = getStringValue(annotation);
				}
			}
		}
		
		return evidence;
	}
	
	private String getStringValue(OWLAnnotationAssertionAxiom ax) {
		OWLAnnotationValue value = ax.getValue();
		String stringValue = value.accept(new OWLAnnotationValueVisitorEx<String>() {

			@Override
			public String visit(IRI iri) {
				return iri.toString();
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
		return stringValue;
	}

	private Set<Evidence> getEvidences(OWLObjectPropertyAssertionAxiom axiom, Map<IRI, Evidence> evidenceIndividuals) {
		Set<Evidence> evidences = new HashSet<>();
		for (OWLAnnotation annotation : axiom.getAnnotations()) {
			OWLAnnotationProperty property = annotation.getProperty();
			if (evidenceOld.equals(property) || hasSupportingRef.equals(property)) {
				IRI iri = annotation.getValue().accept(new OWLAnnotationValueVisitorEx<IRI>() {

					@Override
					public IRI visit(IRI iri) {
						return iri;
					}

					@Override
					public IRI visit(OWLAnonymousIndividual individual) {
						return null;
					}

					@Override
					public IRI visit(OWLLiteral literal) {
						return null;
					}
				});
				if (iri != null) {
					Evidence evidence = evidenceIndividuals.get(iri);
					if (evidence != null) {
						evidences.add(evidence);
					}
				}
			}
		}
		return evidences;
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
	
	protected abstract boolean isAnnotationIndividual(OWLNamedIndividual i, Set<OWLClass> types);

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
	
	protected abstract PAYLOAD initPayload(OWLNamedIndividual object, OWLClass objectType, OWLOntology model, OWLGraphWrapper modelGraph, ExternalLookupService lookup) throws UnknownIdentifierException;
	
	protected abstract boolean handleCC(PAYLOAD payload, OWLClass cls, Metadata metadata, Set<Evidence> evidences, Set<OWLObjectSomeValuesFrom> expressions);
	
	protected abstract boolean handleMF(PAYLOAD payload, OWLClass cls, Metadata metadata, Set<Evidence> evidences, Set<OWLObjectSomeValuesFrom> expressions);
	
	protected abstract boolean handleBP(PAYLOAD payload, OWLClass cls, Metadata metadata, Set<Evidence> evidences, Set<OWLObjectSomeValuesFrom> expressions);
	
	protected abstract String getShortHand(IRI iri);
	
	private OWLObjectSomeValuesFrom createSvf(OWLObjectPropertyExpression p, OWLClass c) {
		return f.getOWLObjectSomeValuesFrom(p, c);
	}
	
	private Metadata extractMetadata(OWLNamedIndividual individual, OWLGraphWrapper modelGraph, String modelId) {
		Metadata metadata = new Metadata();
		metadata.modelId = modelId;
		metadata.individualIds = new HashSet<IRI>();
		metadata.individualIds.add(individual.getIRI());
		Set<OWLAnnotationAssertionAxiom> assertionAxioms = modelGraph.getSourceOntology().getAnnotationAssertionAxioms(individual.getIRI());
		for (OWLAnnotationAssertionAxiom axiom : assertionAxioms) {
			OWLAnnotationProperty currentProperty = axiom.getProperty();
			OWLAnnotationValue value = axiom.getValue();
			extractMetadata(currentProperty, value, metadata);
		}
		return metadata;
	}

	private void extractMetadata(OWLAnnotationProperty p, OWLAnnotationValue v, final Metadata metadata) {
		if (this.contributor.equals(p)) {
			if (v instanceof OWLLiteral) {
				String contributor = ((OWLLiteral) v).getLiteral();
				if (metadata.contributors == null) {
					metadata.contributors = new HashSet<>();
				}
				metadata.contributors.add(contributor);
			}
		}
		else if (this.date.equals(p)) {
			if (v instanceof OWLLiteral) {
				metadata.date = ((OWLLiteral) v).getLiteral();
			}
		}
		else if (this.group.equals(p)) {
			if (v instanceof OWLLiteral) {
				String group = ((OWLLiteral) v).getLiteral();
				if(metadata.groups == null) {
					metadata.groups = new HashSet<>();
				}
 				metadata.groups.add(group);
			}
		}
	}

	private Metadata extractMetadata(Collection<OWLAnnotation> annotations, OWLGraphWrapper modelGraph, String modelId) {
		Metadata metadata = new Metadata();
		metadata.modelId = modelId;
		if (annotations != null && !annotations.isEmpty()) {
			for (OWLAnnotation owlAnnotation : annotations) {
				OWLAnnotationProperty currentProperty = owlAnnotation.getProperty();
				OWLAnnotationValue value = owlAnnotation.getValue();
				extractMetadata(currentProperty, value, metadata);
			}
		}
		return metadata;
	}

}
