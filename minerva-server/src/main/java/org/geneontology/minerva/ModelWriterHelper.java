package org.geneontology.minerva;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.geneontology.minerva.FileBasedMolecularModelManager.PreFileSaveHandler;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.obolibrary.obo2owl.Obo2OWLConstants;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;

public class ModelWriterHelper implements PreFileSaveHandler {
	
	public static final IRI DERIVED_IRI = IRI.create("http://geneontology.org/lego/derived");
	public static final String DERIVED_VALUE = "true";
	
	private final CurieHandler curieHandler;
	private final ExternalLookupService lookupService;

	public ModelWriterHelper(CurieHandler curieHandler, ExternalLookupService lookupService) {
		this.curieHandler = curieHandler;
		this.lookupService = lookupService;
	}

	public static List<OWLOntologyChange> generateLabelsAndIds(OWLOntology model, CurieHandler curieHandler, ExternalLookupService lookupService) {
		if (curieHandler == null && lookupService == null) {
			return Collections.emptyList();
		}
		final OWLOntologyManager m = model.getOWLOntologyManager();
		final OWLDataFactory df = m.getOWLDataFactory();
		IRI displayLabelPropIri = df.getRDFSLabel().getIRI();
		IRI shortIdPropIri = IRI.create(Obo2OWLConstants.OIOVOCAB_IRI_PREFIX+"id");
		OWLAnnotationProperty shortIdProp = df.getOWLAnnotationProperty(shortIdPropIri);
		OWLAnnotationProperty displayLabelProp = df.getOWLAnnotationProperty(displayLabelPropIri);
		// annotations to mark the axiom as generated
		final OWLAnnotationProperty tagProperty = df.getOWLAnnotationProperty(DERIVED_IRI);
		final Set<OWLAnnotation> tags = Collections.singleton(df.getOWLAnnotation(tagProperty, df.getOWLLiteral(DERIVED_VALUE)));
		
		// collect changes
		List<OWLOntologyChange> allChanges = new ArrayList<OWLOntologyChange>();
		
		// set model id
		if (curieHandler != null) {
			final String modelId = curieHandler.getCuri(model.getOntologyID().getOntologyIRI());
			final OWLAnnotation modelAnnotation = df.getOWLAnnotation(shortIdProp, df.getOWLLiteral(modelId), tags);
			allChanges.add(new AddOntologyAnnotation(model, modelAnnotation));
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
		
		return allChanges;
	}

	@Override
	public List<OWLOntologyChange> handle(OWLOntology model) {
		List<OWLOntologyChange> allChanges = generateLabelsAndIds(model, curieHandler, lookupService);
		List<OWLOntologyChange> appliedChanges = model.getOWLOntologyManager().applyChanges(allChanges);
		return appliedChanges;
	}
}
