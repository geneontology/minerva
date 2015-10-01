package org.geneontology.minerva;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.geneontology.minerva.FileBasedMolecularModelManager.PostLoadOntologyFilter;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValueVisitorEx;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.model.RemoveOntologyAnnotation;

public class ModelReaderHelper implements PostLoadOntologyFilter {
	
	public static final ModelReaderHelper INSTANCE = new ModelReaderHelper();
	
	private ModelReaderHelper() {
		// no public constructor, use instance
	}

	@Override
	public OWLOntology filter(OWLOntology model) {
		final OWLOntologyManager m = model.getOWLOntologyManager();
		final OWLDataFactory f = m.getOWLDataFactory();
		final OWLAnnotationProperty derivedProperty = f.getOWLAnnotationProperty(ModelWriterHelper.DERIVED_IRI);
		
		List<OWLOntologyChange> allChanges = new ArrayList<OWLOntologyChange>();
		
		// handle model annotations
		Set<OWLAnnotation> modelAnnotations = model.getAnnotations();
		for (OWLAnnotation modelAnnotation : modelAnnotations) {
			boolean isTagged = isTagged(modelAnnotation.getAnnotations(), derivedProperty);
			if (isTagged) {
				allChanges.add(new RemoveOntologyAnnotation(model, modelAnnotation));
			}
		}
		
		// handle axioms
		for(OWLAxiom ax : model.getAxioms()) {
			boolean isTagged = isTagged(ax.getAnnotations(), derivedProperty);
			if (isTagged) {
				allChanges.add(new RemoveAxiom(model, ax));
			}
		}
		
		// execute changes as batch to minimize change event generation in the owl-api
		if (allChanges.isEmpty() == false) {
			m.applyChanges(allChanges);
		}
		
		return model;
	}
	
	static boolean isTagged(Set<OWLAnnotation> annotations, OWLAnnotationProperty p) {
		if (annotations != null && !annotations.isEmpty()) {
			for (OWLAnnotation annotation : annotations) {
				if (p.equals(annotation.getProperty())) {
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
					if (value != null && ModelWriterHelper.DERIVED_VALUE.equalsIgnoreCase(value)) {
						return true;
					}
				}
		}
		}
		return false;
	}

}
