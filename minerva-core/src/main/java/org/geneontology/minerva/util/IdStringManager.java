package org.geneontology.minerva.util;

import org.apache.commons.lang3.StringUtils;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;

import owltools.graph.OWLGraphWrapper;
import owltools.vocab.OBOUpperVocabulary;

/**
 * @deprecated This class needs to be replaced with a consistent method to convert ids
 * (classes, properties, individuals) to a short form or recognize long forms
 * and just create an IRI from it.
 */
@Deprecated
public class IdStringManager {

//	/**
//	 * @param i
//	 * @param graph 
//	 * @return id
//	 * 
//	 * @see IdStringManager#getIRI
//	 */
//	@Deprecated
//	public static String getId(OWLNamedObject i, OWLGraphWrapper graph) {
//		if (i instanceof OWLObjectProperty) {
//			String relId = graph.getIdentifier(i);
//			return relId;
//		}
//		
//		if (i instanceof OWLNamedIndividual) {
//			return getId((OWLNamedIndividual) i);
//		}
//		IRI iri = i.getIRI();
//		return getId(iri);
//	}
	
	@Deprecated
	public static String getId(OWLObjectProperty p, OWLGraphWrapper graph) {
		String relId = graph.getIdentifier(p);
		return relId;
	}
	
	/**
	 * @param i
	 * @return id
	 * 
	 * @see IdStringManager#getIRI
	 */
	public static String getId(OWLNamedIndividual i) {
		IRI iri = i.getIRI();
		return iri.toString();
	}
	
	public static String getId(OWLClass cls) {
		return getId(cls.getIRI());
	}

	/**
	 * @param iri
	 * @return id
	 */
	@Deprecated
	private static String getId(IRI iri) {
		String iriString = iri.toString();
		// remove obo prefix from IRI
		String full = StringUtils.removeStart(iriString, OBOUpperVocabulary.OBO);
		String replaced;
		if (full.startsWith("#")) {
			replaced = StringUtils.removeStart(full, "#");
		}
		else {
			// replace first '_' char with ':' char
			replaced = StringUtils.replaceOnce(full, "_", ":");
		}
		return replaced;
	}

	/**
	 * Inverse method to {@link #getId}
	 * 
	 * @param id
	 * @param graph
	 * @return IRI
	 * 
	 * @see IdStringManager#getId
	 */
	@Deprecated
	public static IRI getIRI(String id, OWLGraphWrapper graph) {
		if (id.indexOf(':') < 0) {
			return graph.getIRIByIdentifier(id);
		}
		if(id.startsWith(OBOUpperVocabulary.OBO) ){
			return IRI.create(id);
		}
		String fullIRI = OBOUpperVocabulary.OBO + StringUtils.replaceOnce(id, ":", "_");
		return IRI.create(fullIRI);
	}

	/**
	 * Inverse method to {@link #getId} for IRIs only!
	 * 
	 * @param id
	 * @return IRI
	 * 
	 * @see IdStringManager#getId
	 */
	@Deprecated
	public static IRI getIRI(String id) {
		if(id.startsWith(OBOUpperVocabulary.OBO) ){
			return IRI.create(id);
		}
		String fullIRI = OBOUpperVocabulary.OBO + StringUtils.replaceOnce(id, ":", "_");
		return IRI.create(fullIRI);
	}

	public IdStringManager() {
		super();
	}

}