package org.geneontology.minerva;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import com.google.common.base.Optional;

import owltools.gaf.Bioentity;
import owltools.gaf.ExtensionExpression;
import owltools.gaf.GafDocument;
import owltools.gaf.GeneAnnotation;
import owltools.graph.OWLGraphWrapper;
import owltools.vocab.OBOUpperVocabulary;


/**
 * Simple translation of {@link GeneAnnotation} to the all individual lego annotation model.
 *
 */
public class GafToLegoIndividualTranslator {
	
	private static Logger logger = Logger.getLogger(GafToLegoIndividualTranslator.class);
	
	private final OWLGraphWrapper graph;
	private final CurieHandler curieHandler;
	private final OWLObjectProperty partOf;
	private final OWLObjectProperty occursIn;
	private final OWLObjectProperty inTaxon;
	private OWLClass mf;
	private OWLObjectProperty enabledBy;

	private Map<String, OWLObject> allOWLObjectsByAltId;

	private final boolean addLineNumber;

	public GafToLegoIndividualTranslator(OWLGraphWrapper graph, CurieHandler curieHandler, boolean addLineNumber) {
		this.graph = graph;
		this.curieHandler = curieHandler;
		this.addLineNumber = addLineNumber;
		allOWLObjectsByAltId = graph.getAllOWLObjectsByAltId();
		OWLDataFactory df = graph.getDataFactory();
		partOf = OBOUpperVocabulary.BFO_part_of.getObjectProperty(df);
		occursIn = OBOUpperVocabulary.BFO_occurs_in.getObjectProperty(df);
		
		mf = OBOUpperVocabulary.GO_molecular_function.getOWLClass(df);
		enabledBy = OBOUpperVocabulary.GOREL_enabled_by.getObjectProperty(df);
		inTaxon = graph.getOWLObjectPropertyByIdentifier("RO:0002162"); // in taxon
	}
	
	protected void reportError(String error, GeneAnnotation annotation) {
		logger.error(error+" \t Annotation: "+annotation.toString());
	}
	
	protected void reportWarn(String warning, GeneAnnotation annotation) {
		logger.warn(warning+" \t Annotation: "+annotation.toString());
	}
	
	/**
	 * Translate the given {@link GafDocument} into an OWL representation of the LEGO model.
	 * 
	 * @param gaf
	 * @return lego ontology
	 * @throws OWLException
	 * @throws UnknownIdentifierException 
	 */
	public OWLOntology translate(GafDocument gaf) throws OWLException, UnknownIdentifierException {
		final OWLOntologyManager m = graph.getManager();
		OWLOntology lego = m.createOntology(IRI.generateDocumentIRI());
		OWLOntology sourceOntology = graph.getSourceOntology();
		OWLOntologyID ontologyID = sourceOntology.getOntologyID();
		if (ontologyID != null) {
			Optional<IRI> ontologyIRI = ontologyID.getOntologyIRI();
			if (ontologyIRI.isPresent()) {
				OWLDataFactory f = m.getOWLDataFactory();
				OWLImportsDeclaration importDeclaration = f.getOWLImportsDeclaration(ontologyIRI.get());
				m.applyChange(new AddImport(lego, importDeclaration ));
			}
		}
		translate(gaf.getGeneAnnotations(), lego);
		return lego;
	}
	
	/**
	 * Translate the given annotations ({@link GeneAnnotation}) into an OWL representation of the LEGO model.
	 * 
	 * @param annotations
	 * @param lego
	 * @throws OWLException 
	 * @throws UnknownIdentifierException 
	 */
	public void translate(Collection<GeneAnnotation> annotations, final OWLOntology lego) throws OWLException, UnknownIdentifierException {
		final OWLOntologyManager m = graph.getManager();

		Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
		for(GeneAnnotation annotation : annotations) {
			translate(annotation, axioms);
		}
		m.addAxioms(lego, axioms);
	}
	
	/**
	 * Translate theGeneAnnotation into an OWL representation of the LEGO model.
	 * 
	 * @param annotation
	 * @param axioms
	 * @throws OWLException 
	 * @throws UnknownIdentifierException 
	 */
	public void translate(GeneAnnotation annotation, Set<OWLAxiom> axioms) throws OWLException, UnknownIdentifierException {
		// skip ND annotations
		//if ("ND".equals(annotation.getShortEvidence())) {
		//	reportWarn("Skipping ND annotation", annotation);
		//	return;
		//}
		// skip annotation using a qualifier
		String rel = StringUtils.trimToNull(annotation.getRelation());
		if (rel != null) {
			if ("enables".equals(rel) == false && "part_of".equals(rel) == false && "involved_in".equals(rel) == false) {
				reportWarn("Skipping annotation with unsupported relation: "+annotation.getRelation(), annotation);
				return;
			}
		}
		
		final OWLDataFactory f = graph.getDataFactory();
		

		final String annotationClsString = annotation.getCls();
		final OWLClass c = getOwlClass(annotationClsString);
		if (c == null) {
			reportError("Could not find a class for the given identifier: "+annotationClsString, annotation);
			return;
		}

		List<List<ExtensionExpression>> extensionExpressionGroups = annotation.getExtensionExpressions();
		if (extensionExpressionGroups != null && !extensionExpressionGroups.isEmpty()) {
			Set<OWLClassExpression> parsedGroups = new HashSet<OWLClassExpression>();
			for(List<ExtensionExpression> group : extensionExpressionGroups) {
				Set<OWLClassExpression> operands = new HashSet<OWLClassExpression>();
				for(ExtensionExpression extension : group) {
					final String extensionClsString = extension.getCls();
					final String extensionRelationString = extension.getRelation();
					OWLClass extensionCls = getOwlClass(extensionClsString);
					if (extensionCls == null) {
						try {
						IRI extensionIRI = curieHandler.getIRI(extensionClsString);
						extensionCls = f.getOWLClass(extensionIRI);
						} catch (UnknownIdentifierException e) {
							reportError("Could not find the extension class IRI: " + extensionClsString, annotation);
							return;
						}
					}
					final OWLObjectProperty extensionRelation = graph.getOWLObjectPropertyByIdentifier(extensionRelationString);
					if (extensionRelation == null) {
						reportError("Could not find a class for the given extension relation identifier: "+extensionRelationString, annotation);
						continue;
					}
					operands.add(f.getOWLObjectSomeValuesFrom(extensionRelation, extensionCls));
				}
				operands.add(c);
				parsedGroups.add(f.getOWLObjectIntersectionOf(operands));
			}
			if (annotation.isNegated()) {
				OWLClassExpression union = f.getOWLObjectUnionOf(parsedGroups);
				translate(annotation, union, axioms);
			}
			else {
				for(OWLClassExpression ce : parsedGroups) {
					translate(annotation, ce, axioms);
				}
			}
		}
		else {
			translate(annotation, c, axioms);
		}
	}
	
	
	private void translate(GeneAnnotation annotation, OWLClassExpression ce, Set<OWLAxiom> axioms) throws OWLException, UnknownIdentifierException {
		final OWLDataFactory f = graph.getDataFactory();

		// # STEP 1 - Bioentity instance
		final Bioentity bioentity = annotation.getBioentityObject();
		final String isoForm = StringUtils.trimToNull(annotation.getGeneProductForm());

		final OWLClass bioentityClass;
		if (isoForm == null) {
			// option #1: default bioentity id
			try {
				bioentityClass = addBioentityCls(bioentity.getId(), bioentity.getSymbol(), bioentity.getNcbiTaxonId(), axioms, f);
			} catch (UnknownIdentifierException e) {
				reportError("Could not find a class for the Bioentity: " + bioentity, annotation);
				return;
			}
		}
		else {
			// option #2: ISO-form as subclass of bioentity
			try {
				bioentityClass = addBioentityCls(isoForm, bioentity.getSymbol()+" ISOForm "+isoForm, bioentity.getNcbiTaxonId(), axioms, f);
			} catch (UnknownIdentifierException e) {
				reportError("Could not find a class for the Bioentity: " + bioentity, annotation);
				return;
			}
			try {
				OWLClass bioentityClassSuper = addBioentityCls(bioentity.getId(), bioentity.getSymbol(), bioentity.getNcbiTaxonId(), axioms, f);

				axioms.add(f.getOWLDeclarationAxiom(bioentityClassSuper));
				axioms.add(f.getOWLSubClassOfAxiom(bioentityClass, bioentityClassSuper));
			} catch (UnknownIdentifierException e) {
				reportError("Could not find a class for the Bioentity: " + bioentity, annotation);
				return;
			}
		}

		IRI bioentityInstanceIRI = generateNewIRI("bioentity", bioentityClass);
		OWLNamedIndividual bioentityInstance = f.getOWLNamedIndividual(bioentityInstanceIRI);
		axioms.add(f.getOWLDeclarationAxiom(bioentityInstance));
		axioms.add(f.getOWLClassAssertionAxiom(bioentityClass, bioentityInstance));

		// # STEP 2 - create instance:

		// use Aspect to switch between the three options: P == BP, C == CC, F = MF
		String aspect = annotation.getAspect();
		if (aspect == null) {
			reportError("Error, no aspect defined.", annotation);
			return;
		}

		// TODO evidence
		Set<OWLAnnotation> annotations = Collections.emptySet();
		if (addLineNumber) {
			int lineNumber = annotation.getSource().getLineNumber();
			OWLAnnotation source = f.getOWLAnnotation(getLineNumberProperty(axioms, f), f.getOWLLiteral(lineNumber));
			annotations = Collections.singleton(source);
		}
		boolean negated = annotation.isNegated();
		if (negated) {
			handleNegated(bioentityClass, aspect, annotations, ce, axioms, f);
			return;
		}

		//List<String> sources = annotation.getReferenceIds();
		if ("F".equals(aspect)) {
			// create individual
			OWLNamedIndividual individual = f.getOWLNamedIndividual(generateNewIRI("mf", ce));
			axioms.add(f.getOWLDeclarationAxiom(individual));

			// types
			axioms.add(f.getOWLClassAssertionAxiom(ce, individual));

			// link instances

			axioms.add(f.getOWLObjectPropertyAssertionAxiom(enabledBy, individual, bioentityInstance, annotations));
		}				
		else if ("C".equals(aspect)) {
			// generic mf instance
			OWLNamedIndividual mfIndividual = f.getOWLNamedIndividual(generateNewIRI("mf", mf));
			axioms.add(f.getOWLDeclarationAxiom(mfIndividual));

			// generic mf type
			axioms.add(f.getOWLClassAssertionAxiom(mf, mfIndividual));

			// link mf to bioentity
			axioms.add(f.getOWLObjectPropertyAssertionAxiom(enabledBy, mfIndividual, bioentityInstance));

			// cc instance
			OWLNamedIndividual ccIndividual = f.getOWLNamedIndividual(generateNewIRI("cc", ce));
			axioms.add(f.getOWLDeclarationAxiom(ccIndividual));

			// cc type
			axioms.add(f.getOWLClassAssertionAxiom(ce, ccIndividual));

			// link cc and mf
			axioms.add(f.getOWLObjectPropertyAssertionAxiom(occursIn, ccIndividual, mfIndividual, annotations));


		}
		else if ("P".equals(aspect)) {
			// generic mf instance
			OWLNamedIndividual mfIndividual = f.getOWLNamedIndividual(generateNewIRI("mf", mf));
			axioms.add(f.getOWLDeclarationAxiom(mfIndividual));

			// generic mf type
			axioms.add(f.getOWLClassAssertionAxiom(mf, mfIndividual));

			// link mf to bioentity
			axioms.add(f.getOWLObjectPropertyAssertionAxiom(enabledBy, mfIndividual, bioentityInstance));

			// cc instance
			OWLNamedIndividual bpIndividual = f.getOWLNamedIndividual(generateNewIRI("bp", ce));
			axioms.add(f.getOWLDeclarationAxiom(bpIndividual));

			// cc type
			axioms.add(f.getOWLClassAssertionAxiom(ce, bpIndividual));

			// link cc and mf
			axioms.add(f.getOWLObjectPropertyAssertionAxiom(partOf, bpIndividual, mfIndividual, annotations));
		}
	}

	private void handleNegated(OWLClass bioentityClass, String aspect, Set<OWLAnnotation> annotations, 
			OWLClassExpression ce, Set<OWLAxiom> axioms, OWLDataFactory f) {
		if ("F".equals(aspect)) {
			OWLClassExpression notCE = f.getOWLObjectComplementOf(f.getOWLObjectSomeValuesFrom(enabledBy, ce));
			axioms.add(f.getOWLSubClassOfAxiom(bioentityClass, notCE, annotations));
		}
		else if ("C".equals(aspect)) {
			OWLClassExpression notCE = f.getOWLObjectComplementOf(f.getOWLObjectSomeValuesFrom(occursIn, ce));
			axioms.add(f.getOWLSubClassOfAxiom(bioentityClass, notCE, annotations));
		}
		else if ("P".equals(aspect)) {
			OWLClassExpression notCE = f.getOWLObjectComplementOf(f.getOWLObjectSomeValuesFrom(partOf, ce));
			axioms.add(f.getOWLSubClassOfAxiom(bioentityClass, notCE, annotations));
		}
	}

	private OWLClass addBioentityCls(String id, String lbl, String taxon, Set<OWLAxiom> axioms, OWLDataFactory f) throws UnknownIdentifierException {
		IRI iri = curieHandler.getIRI(id);
		OWLClass cls = f.getOWLClass(iri);
		boolean add = axioms.add(f.getOWLDeclarationAxiom(cls));
		if (add) {
			OWLAnnotation annotation = f.getOWLAnnotation(f.getRDFSLabel(), f.getOWLLiteral(lbl));
			axioms.add(f.getOWLAnnotationAssertionAxiom(iri, annotation));
			if (taxon != null) {
				OWLClass taxonClass = f.getOWLClass(curieHandler.getIRI(taxon));
				axioms.add(f.getOWLDeclarationAxiom(taxonClass));
				axioms.add(f.getOWLSubClassOfAxiom(cls,
						f.getOWLObjectSomeValuesFrom(inTaxon, taxonClass)));
			}
		}
		return cls;
	}
	
	/**
	 * @param id
	 * @return cls or null
	 */
	private OWLClass getOwlClass(String id) {
		OWLClass cls = graph.getOWLClassByIdentifier(id);
		if (cls == null) {
			// check alt ids
			OWLObject owlObject = allOWLObjectsByAltId.get(id);
			if (owlObject != null && owlObject instanceof OWLClass) {
				cls = (OWLClass) owlObject;
			}
		}
		return cls;
	}
	

	private static final IRI GAF_LINE_NUMBER = IRI.create("http://gaf/line_number");
	
	private OWLAnnotationProperty getLineNumberProperty(Set<OWLAxiom> axioms, OWLDataFactory f) {
		OWLAnnotationProperty p = f.getOWLAnnotationProperty(GAF_LINE_NUMBER);
		axioms.add(f.getOWLDeclarationAxiom(p));
		return p;
	}

	private IRI generateNewIRI(String type, OWLClassExpression ce) {
		if( ce.isAnonymous() == false) {
			OWLClass c = ce.asOWLClass();
			String id = StringUtils.replace(curieHandler.getCuri(c), ":", "_");
			type = type + "-" + id;
		}
		return IRI.create("http://geneontology.org/lego/"+type+"-"+UUID.randomUUID().toString());
		
	}
	
}
