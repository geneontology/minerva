package org.geneontology.minerva.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.obolibrary.obo2owl.OWLAPIObo2Owl;
import org.obolibrary.obo2owl.Obo2OWLConstants.Obo2OWLVocabulary;
import org.obolibrary.oboformat.model.OBODoc;
import org.obolibrary.oboformat.parser.OBOFormatConstants.OboFormatTag;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.search.EntitySearcher;


public class OntUtil {
	
	public static String getLabel(OWLEntity term, OWLOntology ont, Imports useImportsClosure) {
		final Set<OWLOntology> onts;
		OWLDataFactory factory = ont.getOWLOntologyManager().getOWLDataFactory();
		if (useImportsClosure.equals(Imports.INCLUDED)) {
			onts = ont.getImportsClosure();
		} else {
			onts = Collections.singleton(ont);
		}
		return EntitySearcher.getAnnotations(term, onts, factory.getRDFSLabel()).stream()
				.flatMap(ann -> ann.getValue().asLiteral().asSet().stream())
				.findAny()
				.map(OWLLiteral::getLiteral)
				.orElse(null);		
	}
	
	public static IRI getIRIByIdentifier(String id, OWLOntology ont) {
		// special magic for finding IRIs from a non-standard identifier
		// This is the case for relations (OWLObject properties) with a short hand
		// or for relations with a non identifiers with-out a colon, e.g. negative_regulation
		OWLDataFactory factory = ont.getOWLOntologyManager().getOWLDataFactory();
		if (!id.contains(":")) {
			final OWLAnnotationProperty shortHand = factory.getOWLAnnotationProperty(Obo2OWLVocabulary.IRI_OIO_shorthand.getIRI());
			final OWLAnnotationProperty oboIdInOwl = factory.getOWLAnnotationProperty(OWLAPIObo2Owl.trTagToIRI(OboFormatTag.TAG_ID.getTag()));
			for (OWLOntology o : ont.getImportsClosure()) {
				for(OWLObjectProperty p : o.getObjectPropertiesInSignature()) {
					// check for short hand or obo ID in owl
					Collection<OWLAnnotation> annotations = EntitySearcher.getAnnotations(p, o);
					if (annotations != null) {
						for (OWLAnnotation owlAnnotation : annotations) {
							OWLAnnotationProperty property = owlAnnotation.getProperty();
							if ((shortHand != null && shortHand.equals(property)) 
									|| (oboIdInOwl != null && oboIdInOwl.equals(property))) {
								OWLAnnotationValue value = owlAnnotation.getValue();
								if (value != null && value instanceof OWLLiteral) {
									OWLLiteral literal = (OWLLiteral) value;
									String shortHandLabel = literal.getLiteral();
									if (id.equals(shortHandLabel)) {
										return p.getIRI();
									}
								}
							}
						}
					}
				}
			}
		}
		// otherwise use the obo2owl method
		OWLAPIObo2Owl b = new OWLAPIObo2Owl(ont.getOWLOntologyManager()); // re-use manager, creating a new one can be expensive as this is a highly used code path
		b.setObodoc(new OBODoc());
		return b.oboIdToIRI(id);
	}

}
