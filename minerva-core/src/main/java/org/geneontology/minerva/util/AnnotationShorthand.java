package org.geneontology.minerva.util;

import org.geneontology.minerva.curie.CurieHandler;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * Set of short hands for longer IRIs. This list is not exhaustive (and does not
 * need to be), as full IRIs can still be used.<br>
 * <br>
 * This improves readability and reduces clutter in the JSON and related JS.
 *
 */
public enum AnnotationShorthand {
	
	x(IRI.create("http://geneontology.org/lego/hint/layout/x"), "hint-layout-x"),
	y(IRI.create("http://geneontology.org/lego/hint/layout/y"), "hint-layout-y"),
	comment(OWLRDFVocabulary.RDFS_COMMENT.getIRI()), // arbitrary String
	evidence(IRI.create("http://geneontology.org/lego/evidence")), // eco class iri
	date(IRI.create("http://purl.org/dc/elements/1.1/date")), // arbitrary string at the moment, define date format?
	// DC recommends http://www.w3.org/TR/NOTE-datetime, one example format is YYYY-MM-DD
	source(IRI.create("http://purl.org/dc/elements/1.1/source")), // arbitrary string, such as PMID:000000
	contributor(IRI.create("http://purl.org/dc/elements/1.1/contributor")), // who contributed to the annotation
	title(IRI.create("http://purl.org/dc/elements/1.1/title")), // title (of the model)
	deprecated(OWLRDFVocabulary.OWL_DEPRECATED.getIRI()); // model annotation to indicate deprecated models
	
	
	private final IRI annotationProperty;
	private final String othername;
	
	AnnotationShorthand(IRI annotationProperty) {
		this(annotationProperty, null);
	}
	
	AnnotationShorthand(IRI annotationProperty, String othername) {
		this.annotationProperty = annotationProperty;
		this.othername = othername;
	}
	
	public IRI getAnnotationProperty() {
		return annotationProperty;
	}
	
	public String getShorthand() {
		return othername != null ? othername : name(); 
	}
	
	public static AnnotationShorthand getShorthand(IRI iri) {
		for (AnnotationShorthand type : AnnotationShorthand.values()) {
			if (type.annotationProperty.equals(iri)) {
				return type;
			}
		}
		return null;
	}
	
	public static AnnotationShorthand getShorthand(String name, CurieHandler curieHandler) {
		if (name != null) {
			IRI iri = curieHandler.getIRI(name);
			for (AnnotationShorthand type : AnnotationShorthand.values()) {
				if (type.name().equals(name) || (type.othername != null && type.othername.equals(name))) {
					return type;
				}
				else if (iri.equals(type.annotationProperty)) {
					return type;
				}
			}
		}
		return null;
	}
}