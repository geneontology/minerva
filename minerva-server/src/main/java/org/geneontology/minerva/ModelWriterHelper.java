package org.geneontology.minerva;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.geneontology.minerva.BlazegraphMolecularModelManager.PreFileSaveHandler;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.json.JsonModel;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.geneontology.minerva.server.handler.OperationsTools;
import org.geneontology.minerva.taxon.FindTaxonTool;
import org.obolibrary.obo2owl.Obo2OWLConstants;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.util.OWLClassExpressionVisitorAdapter;

import owltools.util.OwlHelper;
import owltools.vocab.OBOUpperVocabulary;

import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ModelWriterHelper implements PreFileSaveHandler {

	public static final IRI DERIVED_IRI = IRI.create("http://geneontology.org/lego/derived");
	public static final String DERIVED_VALUE = "true";

	private final CurieHandler curieHandler;
	private final ExternalLookupService lookupService;
	private final IRI shortIdPropIRI;
	private final IRI enabledByIRI;

	public ModelWriterHelper(CurieHandler curieHandler, ExternalLookupService lookupService) {
		this.curieHandler = curieHandler;
		this.lookupService = lookupService;
		shortIdPropIRI = IRI.create(Obo2OWLConstants.OIOVOCAB_IRI_PREFIX+"id");
		enabledByIRI = OBOUpperVocabulary.GOREL_enabled_by.getIRI();
	}

	List<OWLOntologyChange> generateLabelsAndIds(OWLOntology model, ExternalLookupService lookupService) throws UnknownIdentifierException {
		if (curieHandler == null && lookupService == null) {
			return Collections.emptyList();
		}
		final OWLOntologyManager m = model.getOWLOntologyManager();
		final OWLDataFactory df = m.getOWLDataFactory();
		IRI displayLabelPropIri = df.getRDFSLabel().getIRI();
		OWLAnnotationProperty shortIdProp = df.getOWLAnnotationProperty(shortIdPropIRI);
		OWLAnnotationProperty displayLabelProp = df.getOWLAnnotationProperty(displayLabelPropIri);
		OWLObjectProperty enabledByProp = df.getOWLObjectProperty(enabledByIRI);
		// annotations to mark the axiom as generated
		final OWLAnnotationProperty tagProperty = df.getOWLAnnotationProperty(DERIVED_IRI);
		final Set<OWLAnnotation> tags = Collections.singleton(df.getOWLAnnotation(tagProperty, df.getOWLLiteral(DERIVED_VALUE)));
		
		// collect changes
		List<OWLOntologyChange> allChanges = new ArrayList<OWLOntologyChange>();
		
		// set model id and json model
		if (curieHandler != null) {
			Optional<IRI> ontologyIRI = model.getOntologyID().getOntologyIRI();
			if (ontologyIRI.isPresent()) {
				final String modelId = curieHandler.getCuri(ontologyIRI.get());
				final OWLAnnotation modelAnnotation = df.getOWLAnnotation(shortIdProp, df.getOWLLiteral(modelId), tags);
				allChanges.add(new AddOntologyAnnotation(model, modelAnnotation));		
			}
		}
		
		// find relevant classes
		final Set<OWLOntology> importsClosure = model.getImportsClosure();
		Set<OWLClass> usedClasses = new HashSet<OWLClass>();
		Set<OWLNamedIndividual> individuals = model.getIndividualsInSignature();
		for (OWLNamedIndividual individual : individuals) {
			Set<OWLClassAssertionAxiom> axioms = model.getClassAssertionAxioms(individual);
			for (OWLClassAssertionAxiom axiom : axioms) {
				usedClasses.addAll(axiom.getClassesInSignature());
			}
		}
		
		// find entity classes
		final Set<OWLClass> bioentityClasses = new HashSet<OWLClass>();
		Set<OWLObjectPropertyAssertionAxiom> candidateAxioms = model.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION);
		for (OWLObjectPropertyAssertionAxiom axiom : candidateAxioms) {
			if (enabledByProp.equals(axiom.getProperty())) {
				OWLIndividual object = axiom.getObject();
				if (object.isNamed()) {
					// assume named object for enabled_by is a bioentity
					OWLNamedIndividual o = object.asOWLNamedIndividual();
					Set<OWLClassExpression> types = OwlHelper.getTypes(o, model);
					for (OWLClassExpression ce : types) {
						ce.accept(new OWLClassExpressionVisitorAdapter(){

							@Override
							public void visit(OWLClass cls) {
								bioentityClasses.add(cls);
							}
						});
					}
				}
			}
		}
		usedClasses.addAll(bioentityClasses);
		FindTaxonTool taxonTool = new FindTaxonTool(curieHandler, model.getOWLOntologyManager().getOWLDataFactory());
		
		// check label and ids for used classes
		for (OWLClass cls : usedClasses) {
			boolean hasLabelAxiom = false;
			boolean hasShortIdAxiom = false;
			Set<OWLAnnotationAssertionAxiom> existingAnnotations = new HashSet<OWLAnnotationAssertionAxiom>();
			for(OWLOntology ont : importsClosure) {
				existingAnnotations.addAll(ont.getAnnotationAssertionAxioms(cls.getIRI()));
			}
			for (OWLAnnotationAssertionAxiom axiom : existingAnnotations) {
				if (shortIdProp.equals(axiom.getProperty())) {
					hasShortIdAxiom = true;
				}
				else if (displayLabelProp.equals(axiom.getProperty())) {
					hasLabelAxiom = true;
				}
			}
			if (bioentityClasses.contains(cls) && curieHandler != null && lookupService != null) {
				// check for taxon axiom
				String taxon = taxonTool.getEntityTaxon(curieHandler.getCuri(cls), model);
				if (taxon == null) {
					// find taxon via Golr
					List<LookupEntry> lookup = lookupService.lookup(cls.getIRI());
					if (lookup != null && !lookup.isEmpty()) {
						taxon = lookup.get(0).taxon;
						if (taxon != null) {
							OWLAxiom axiom = taxonTool.createTaxonAxiom(cls, taxon, model, tags);
							allChanges.add(new AddAxiom(model, axiom));
						}
					}
				}
			}
			if (hasLabelAxiom == false && lookupService != null) {
				// find label via Golr
				List<LookupEntry> lookup = lookupService.lookup(cls.getIRI());
				if (lookup != null && !lookup.isEmpty()) {
					String lbl = lookup.get(0).label;
					if (lbl != null) {
						OWLAxiom axiom = df.getOWLAnnotationAssertionAxiom(displayLabelProp, cls.getIRI(), df.getOWLLiteral(lbl), tags);
						allChanges.add(new AddAxiom(model, axiom));
					}
				}
			}
			if (hasShortIdAxiom == false && curieHandler != null) {
				// id shorthand via curie
				String curie = curieHandler.getCuri(cls);
				OWLAxiom axiom = df.getOWLAnnotationAssertionAxiom(shortIdProp, cls.getIRI(), df.getOWLLiteral(curie), tags);
				allChanges.add(new AddAxiom(model, axiom));
			}
		}
		if (allChanges.isEmpty() == false) {
			// add declaration axioms for annotation properties
			// this is a bug fix
			allChanges.add(new AddAxiom(model, df.getOWLDeclarationAxiom(tagProperty)));
			allChanges.add(new AddAxiom(model, df.getOWLDeclarationAxiom(shortIdProp)));
			allChanges.add(new AddAxiom(model, df.getOWLDeclarationAxiom(displayLabelProp)));
		}
		return allChanges;
	}

	@Override
	public List<OWLOntologyChange> handle(OWLOntology model) throws UnknownIdentifierException {
		List<OWLOntologyChange> allChanges = generateLabelsAndIds(model, lookupService);
		model.getOWLOntologyManager().applyChanges(allChanges);
		return allChanges;
	}
}
