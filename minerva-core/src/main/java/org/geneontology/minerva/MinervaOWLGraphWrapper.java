package org.geneontology.minerva;


import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.SerializationUtils;
import org.apache.log4j.Logger;
import org.obolibrary.obo2owl.Obo2OWLConstants;
import org.obolibrary.obo2owl.Obo2OWLConstants.Obo2OWLVocabulary;
import org.obolibrary.oboformat.parser.OBOFormatConstants.OboFormatTag;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationSubject;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import com.google.common.collect.Sets;

import gnu.trove.set.hash.THashSet;
import owltools.io.ParserWrapper;
import owltools.util.OwlHelper;


/**
 * Consolidation of methods actually used in Minerva from the OWLTools OWLGraphWrapper class
 */
public class MinervaOWLGraphWrapper implements Closeable {

	private static final Logger LOG = Logger.getLogger(MinervaOWLGraphWrapper.class);
	private Map<String,OWLObject> altIdMap = null;
	private String defaultIDSpace = "";
	final Map<String,String> idSpaceMap;
	public OWLOntology sourceOntology; // graph is seeded from this ontology.
	public static Map<String,IRI> annotationPropertyMap = initAnnotationPropertyMap();
	public Set<OWLOntology> supportOntologySet = new HashSet<OWLOntology>();
	
	public MinervaOWLGraphWrapper(OWLOntology ontology) {
		super();
		idSpaceMap = new HashMap<String,String>();
		sourceOntology = ontology;
	}

	public MinervaOWLGraphWrapper(String iri) throws OWLOntologyCreationException {
		super();
		idSpaceMap = new HashMap<String,String>();
		ParserWrapper pw = new ParserWrapper();
		OWLOntologyManager manager = pw.getManager();
		sourceOntology = manager.createOntology(IRI.create(iri));
	}
	
	public static final String DEFAULT_IRI_PREFIX = Obo2OWLConstants.DEFAULT_IRI_PREFIX;

	/**
	 * Table 5.8 Translation of Annotation Vocabulary.
	 * 
	 * @return property map
	 */
	private static HashMap<String, IRI> initAnnotationPropertyMap() {

		HashMap<String, IRI> map = new HashMap<String, IRI>();
		map.put(OboFormatTag.TAG_IS_OBSELETE.getTag(),OWLRDFVocabulary.OWL_DEPRECATED.getIRI());
		map.put(OboFormatTag.TAG_NAME.getTag(),OWLRDFVocabulary.RDFS_LABEL.getIRI());
		map.put(OboFormatTag.TAG_COMMENT.getTag(),OWLRDFVocabulary.RDFS_COMMENT.getIRI());

		for(Obo2OWLVocabulary vac: Obo2OWLVocabulary.values()){
			map.put(vac.getMappedTag(), vac.getIRI());
		}

		/*	map.put("expand_expression_to",Obo2OWLVocabulary.IRI_IAO_0000424.getIRI());
		map.put("expand_assertion_to",Obo2OWLVocabulary.IRI_IAO_0000425.getIRI());
		map.put("def",Obo2OWLVocabulary.IRI_IAO_0000115.getIRI());
		map.put("synonym",Obo2OWLVocabulary.IRI_IAO_0000118.getIRI());
		map.put("is_anti_symmetric",Obo2OWLVocabulary.IRI_IAO_0000427.getIRI());
		map.put("replaced_by", Obo2OWLVocabulary.IRI_IAO_0100001.getIRI());*/

		return map;
	}

	/**
	 * Returns an OWLClass given an IRI
	 * <p>
	 * the class must be declared in either the source ontology, or in a support ontology,
	 * otherwise null is returned
	 *
	 * @param iri
	 * @return {@link OWLClass}
	 */
	public OWLClass getOWLClass(IRI iri) {
		OWLClass c = getDataFactory().getOWLClass(iri);
		for (OWLOntology o : getAllOntologies()) {
			if (o.getDeclarationAxioms(c).size() > 0) {
				return c;
			}
		}
		return null;
	}


	/**
	 * Returns the OWLObjectProperty with this IRI
	 * <p>
	 * Must have been declared in one of the ontologies
	 * 
	 * @param iri
	 * @return {@link OWLObjectProperty}
	 */
	public OWLObjectProperty getOWLObjectProperty(String iri) {
		return getOWLObjectProperty(IRI.create(iri));
	}

	public OWLObjectProperty getOWLObjectProperty(IRI iri) {
		OWLObjectProperty p = getDataFactory().getOWLObjectProperty(iri);
		for (OWLOntology o : getAllOntologies()) {
			if (o.getDeclarationAxioms(p).size() > 0) {
				return p;
			}
		}
		return null;
	}

	/**
	 * fetches the rdfs:label for an OWLObject
	 * <p>
	 * assumes zero or one rdfs:label
	 * 
	 * @param c
	 * @return label
	 */
	public String getLabel(OWLObject c) {
		return getAnnotationValue(c, getDataFactory().getRDFSLabel());
	}

	/**
	 * fetches the value of a single-valued annotation property for an OWLObject
	 * <p>
	 * TODO: provide a flag that determines behavior in the case of >1 value
	 * 
	 * @param c
	 * @param lap
	 * @return value
	 */
	public String getAnnotationValue(OWLObject c, OWLAnnotationProperty lap) {
		Set<OWLAnnotation>anns = new HashSet<OWLAnnotation>();
		if (c instanceof OWLEntity) {
			for (OWLOntology ont : getAllOntologies()) {
				anns.addAll(OwlHelper.getAnnotations((OWLEntity) c, lap, ont));
			}
		}
		else {
			return null;
		}
		for (OWLAnnotation a : anns) {
			if (a.getValue() instanceof OWLLiteral) {
				OWLLiteral val = (OWLLiteral) a.getValue();
				return (String) SerializationUtils.clone(val.getLiteral()); // return first - TODO - check zero or one
			}
		}

		return null;
	}

	/**
	 * Every OWLGraphWrapper objects wraps zero or one source ontologies.
	 * 
	 * @return ontology
	 */
	public OWLOntology getSourceOntology() {
		return sourceOntology;
	}

	public void setSourceOntology(OWLOntology sourceOntology) {
		this.sourceOntology = sourceOntology;
	}


	public OWLOntologyManager getManager() {
		return sourceOntology.getOWLOntologyManager();
	}


	public void addSupportOntology(OWLOntology o) {
		this.supportOntologySet.add(o);
	}
	public void removeSupportOntology(OWLOntology o) {
		this.supportOntologySet.remove(o);
	}


	/**
	 * in general application code need not call this - it is mostly used internally
	 * 
	 * @return union of source ontology plus all supporting ontologies plus their import closures
	 */
	public Set<OWLOntology> getAllOntologies() {
		Set<OWLOntology> all = new HashSet<OWLOntology>(getSupportOntologySet());
		for (OWLOntology o : getSupportOntologySet()) {
			all.addAll(o.getImportsClosure());
		}
		all.add(getSourceOntology());
		all.addAll(getSourceOntology().getImportsClosure());
		return all;
	}

	/**
	 * all operations are over a set of ontologies - the source ontology plus
	 * any number of supporting ontologies. The supporting ontologies may be drawn
	 * from the imports closure of the source ontology, although this need not be the case.
	 * 
	 * @return set of support ontologies
	 */
	public Set<OWLOntology> getSupportOntologySet() {
		return supportOntologySet;
	}

//	@Override
	public synchronized void close() throws IOException {
//		if (reasoner != null) {
//			reasoner.dispose();
//			reasoner = null;
//			isSynchronized = false;
//		}
//		neighborAxioms = null;
	}


	/**
	 * Fetch all {@link OWLClass} objects from all ontologies. 
	 * This set is a copy. Changes are not reflected in the ontologies.
	 * 
	 * @return set of all {@link OWLClass}
	 */
	public Set<OWLClass> getAllOWLClasses() {
		Set<OWLClass> owlClasses = new THashSet<OWLClass>();
		for (OWLOntology o : getAllOntologies()) {
			owlClasses.addAll(o.getClassesInSignature());
		}
		return owlClasses;
	}


	/**
	 * Given an OBO-style ID, return the corresponding OWLClass, if it is declared - otherwise null
	 * 
	 * @param id - e.g. GO:0008150
	 * @return OWLClass with id or null
	 */
	public OWLClass getOWLClassByIdentifier(String id) {
		return getOWLClassByIdentifier(id, false);
	}

	/**
	 * 
	 * As {@link #getOWLClassByIdentifier(String)} but include pre-resolution step
	 * using altId map.
	 * 
	 * Currently this additional boolean option is obo-format specific; in OBO,
	 * when a class A is merged into B, the OBO-ID of A is preserved with an hasAlternateId
	 * annotation on the IRI of B. Using this method, with isAutoResolve=true, a query for
	 * the OBO ID of A will return class B.
	 * 
	 * In future, analogous options will be added to IRI-based access to classes.
	 * 
	 * @param id
	 * @param isAutoResolve
	 * @return OWLClass with id or null
	 */
	public OWLClass getOWLClassByIdentifier(String id, boolean isAutoResolve) {
		IRI iri = getIRIByIdentifier(id, isAutoResolve);
		if (iri != null)
			return getOWLClass(iri);
		return null;
	}

	public IRI getIRIByIdentifier(String id, boolean isAutoResolve) {
		if (isAutoResolve) {
			OWLObject obj = this.getObjectByAltId(id);
			if (obj != null) {
				return ((OWLNamedObject) obj).getIRI();
			}
		}

		// special magic for finding IRIs from a non-standard identifier
		// This is the case for relations (OWLObject properties) with a short hand
		// or for relations with a non identifiers with-out a colon, e.g. negative_regulation
		// we first collect all candidate matching properties in candIRISet.
		Set<IRI> candIRISet = Sets.newHashSet();
		if (!id.contains(":")) {
			final OWLAnnotationProperty shortHand = getDataFactory().getOWLAnnotationProperty(Obo2OWLVocabulary.IRI_OIO_shorthand.getIRI());
			final OWLAnnotationProperty oboIdInOwl = getDataFactory().getOWLAnnotationProperty(trTagToIRI(OboFormatTag.TAG_ID.getTag()));
			for (OWLOntology o : getAllOntologies()) {
				for(OWLObjectProperty p : o.getObjectPropertiesInSignature()) {
					// check for short hand or obo ID in owl
					Set<OWLAnnotation> annotations = OwlHelper.getAnnotations(p, o);
					if (annotations != null) {
						for (OWLAnnotation owlAnnotation : annotations) {
							OWLAnnotationProperty property = owlAnnotation.getProperty();
							if ((shortHand != null && shortHand.equals(property)) 
									|| (oboIdInOwl != null && oboIdInOwl.equals(property)))
							{
								OWLAnnotationValue value = owlAnnotation.getValue();
								if (value != null && value instanceof OWLLiteral) {
									OWLLiteral literal = (OWLLiteral) value;
									String shortHandLabel = literal.getLiteral();
									if (id.equals(shortHandLabel)) {
										candIRISet.add(p.getIRI());
									}
								}
							}
						}
					}
				}
			}
		}

		// In the case where we find multiple candidate IRIs, we give priorities for IRIs from BFO or RO ontologies.
		IRI returnIRI = null;
		for (IRI iri: candIRISet) {
			String iriStr = iri.toString();
			if (iriStr.contains("BFO") || iriStr.contains("RO")) {
				returnIRI = iri;
			}
		}

		// If we were not able to find RO/BFO candidate IRIs for id
		if (returnIRI == null) {
			// We return it only if we have only one candidate. 
			if (candIRISet.size() == 1)
				return new ArrayList<IRI>(candIRISet).get(0);
			// This is the unexpected case. Multiple non-RO/BPO properties are mapped to given id and it's not clear what to return.
			else if (candIRISet.size() > 1)
				throw new RuntimeException("Multiple candidate IRIs are found for id: " +  id + ". None of them are from BFO or RO.");
		}
		// If we were able to find the property from RO/BFO, just return it. 
		else {
			return returnIRI;
		}

		// otherwise use the obo2owl method
		//Obo2Owl b = new Obo2Owl(getManager()); // re-use manager, creating a new one can be expensive as this is a highly used code path
		//b.setObodoc(new OBODoc());
		return oboIdToIRI(id);
	}
	
	public static IRI trTagToIRI(String tag){
		IRI  iri = null;
		if (annotationPropertyMap.containsKey(tag)) {
			iri = annotationPropertyMap.get(tag);
		}
		else {
			//iri = IRI.create(Obo2OWLConstants.DEFAULT_IRI_PREFIX+"IAO_"+tag);
			iri = IRI.create(Obo2OWLConstants.OIOVOCAB_IRI_PREFIX+tag);

		}

		return iri;

	}
	
	public IRI oboIdToIRI(String id) {
		if (id.contains(" ")) {
			LOG.error("id contains space: \""+id+"\"");
			//throw new UnsupportedEncodingException();
			return null;
		}

		// No conversion is required if this is already an IRI (ID-as-URI rule)
		if (id.startsWith("http:")) { // TODO - roundtrip from other schemes
			return IRI.create(id);
		}
		else if (id.startsWith("https:")) { // TODO - roundtrip from other schemes
			return IRI.create(id);
		}
		else if (id.startsWith("ftp:")) { // TODO - roundtrip from other schemes
			return IRI.create(id);
		}
		else if (id.startsWith("urn:")) { // TODO - roundtrip from other schemes
			return IRI.create(id);
		}

		// TODO - treat_xrefs_as_equivalent
		// special case rule for relation xrefs:
		// 5.9.3. Special Rules for Relations
		if (!id.contains(":")) {
			String xid = translateShorthandIdToExpandedId(id);
			if (!xid.equals(id))
				return oboIdToIRI(xid);
		}

		String[] idParts = id.split(":", 2);
		String db;
		String localId;
		if (idParts.length > 1) {
			db = idParts[0];
			localId = idParts[1];
			if(localId.contains("_")){
				db += "#_"; // NonCanonical-Prefixed-ID
			}else
				db += "_";
		}
		else if (idParts.length == 0) {
			db = getDefaultIDSpace()+"#"; 
			localId = id;
		}
		else { // == 1
			// todo use owlOntology IRI
			db = getDefaultIDSpace()+"#";
			//	if(id.contains("_"))
			//	db += "_";

			localId = idParts[0]; // Unprefixed-ID
		}


		String uriPrefix = Obo2OWLConstants.DEFAULT_IRI_PREFIX+db;
		if (idSpaceMap.containsKey(db)) {
			uriPrefix = idSpaceMap.get(db);
		}

		String safeId;
		try {
			safeId = java.net.URLEncoder.encode(localId,"US-ASCII");
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			return null;
		}

		if (safeId.contains(" "))
			safeId = safeId.replace(" ", "_");
		IRI iri = null;
		try {
			iri = IRI.create(uriPrefix + safeId);
		} catch (IllegalArgumentException e) {
			// TODO - define new exception class for this
			// throw new UnsupportedEncodingException();
			return null;
		}

		return  iri;	
	}
	
	// 5.9.3. Special Rules for Relations
	private String translateShorthandIdToExpandedId(String id) {
		if (id.contains(":")) {
			return id;
		}else {
			System.err.println("line 467 translateShorthandIdToExpandedId fail on need for obo");
			System.exit(-1);
		}
		return null;
/*		Frame tdf = obodoc.getTypedefFrame(id);
		if (tdf == null)
			return id;
		Collection<Xref> xrefs = tdf.getTagValues(OboFormatTag.TAG_XREF, Xref.class);
		String matchingExpandedId = null;
		for (Xref xref : xrefs) {
			//System.err.println("ID:"+id+" xref:"+xref);

			if (xref != null) {
				String xid = xref.getIdref();
				//System.err.println(" ID:"+id+" xid:"+xid);
				if (xid.equals(id))
					continue;
				if (matchingExpandedId == null) {
					matchingExpandedId = xid;
				}
				else {
					// RO and BFO take precedence over others
					if ((xid.startsWith("RO") ||
							xid.startsWith("BFO"))) {
						matchingExpandedId = xid;
					}
				}
			}
		}
		if (matchingExpandedId == null)
			return id;
		//System.err.println("  ID:"+id+" matching:"+matchingExpandedId);
		return matchingExpandedId;
	*/
	}

	private String getDefaultIDSpace() {
		return defaultIDSpace;
	}
	
	/**
	 * @param altId
	 * @return OWLObject that has matching altId, or null if not found
	 */
	public OWLObject getObjectByAltId(String altId) {
		Map<String, OWLObject> m = getAltIdMap(false);
		if (m.containsKey(altId))
			return m.get(altId);
		else
			return null;
	}
	
	private Map<String,OWLObject> getAltIdMap(boolean isReset) {
		if (isReset)
			altIdMap = null;
		if (altIdMap == null) {
			altIdMap = getAllOWLObjectsByAltId();
		}
		return altIdMap;
	}
	
	/**
	 * Given an OBO-style ID, return the corresponding OWLObject, if it is declared - otherwise null
	 * 
	 * @param id - e.g. GO:0008150
	 * @return object with id or null
	 */
	public OWLObject getOWLObjectByIdentifier(String id) {
		IRI iri = getIRIByIdentifier(id);
		if (iri != null)
			return getOWLObject(iri);
		return null;
	}

	/**
	 * Returns the OWLObject with this IRI
	 * <p>
	 * Must have been declared in one of the ontologies
	 * <p>
	 * Currently OWLObject must be one of OWLClass, OWLObjectProperty or OWLNamedIndividual
	 * <p>
	 * If the ontology employs punning and there different entities with the same IRI, then
	 * the order of precedence is OWLClass then OWLObjectProperty then OWLNamedIndividual
	 *
	 * @param s entity IRI
	 * @return {@link OWLObject}
	 */
	public OWLObject getOWLObject(IRI s) {
		OWLObject o;
		o = getOWLClass(s);
		if (o == null) {
			o = getOWLIndividual(s);
		}
		if (o == null) {
			o = getOWLObjectProperty(s);
		}
		if (o == null) {
			o = getOWLAnnotationProperty(s);
		}
		return o;
	}
	
	public OWLAnnotationProperty getOWLAnnotationProperty(IRI iri) {
		OWLAnnotationProperty p = getDataFactory().getOWLAnnotationProperty(iri);
		for (OWLOntology o : getAllOntologies()) {
			if (o.getDeclarationAxioms(p).size() > 0) {
				return p;
			}
		}
		return null;
	}

	/**
	 * Returns an OWLNamedIndividual with this IRI <b>if it has been declared</b>
	 * in the source or support ontologies. Returns null otherwise.
	 * 
	 * @param iri
	 * @return {@link OWLNamedIndividual}
	 */
	public OWLNamedIndividual getOWLIndividual(IRI iri) {
		OWLNamedIndividual c = getDataFactory().getOWLNamedIndividual(iri);
		for (OWLOntology o : getAllOntologies()) {
			for (OWLDeclarationAxiom da : o.getDeclarationAxioms(c)) {
				if (da.getEntity() instanceof OWLNamedIndividual) {
					return (OWLNamedIndividual) da.getEntity();
				}
			}
		}
		return null;
	}

	public OWLDataFactory getDataFactory() {
		return getManager().getOWLDataFactory();
	}


	/**
	 * Given an OBO-style ID, return the corresponding OWLObjectProperty, if it is declared - otherwise null
	 * 
	 * @param id - e.g. GO:0008150
	 * @return OWLObjectProperty with id or null
	 */
	public OWLObjectProperty getOWLObjectPropertyByIdentifier(String id) {
		IRI iri = getIRIByIdentifier(id);
		if (iri != null) 
			return getOWLObjectProperty(iri);
		return null;
	}


//	public void mergeImportClosure(boolean b) {
//		// TODO Auto-generated method stub
//		
//	}


	/**
	 * Find all corresponding {@link OWLObject}s with an OBO-style alternate identifier.
	 * <p>
	 * WARNING: This methods scans all object annotations in all ontologies. 
	 * This is an expensive method.
	 * 
	 * @return map of altId to OWLObject (never null)
	 */
	public Map<String, OWLObject> getAllOWLObjectsByAltId() {
		final Map<String, OWLObject> results = new HashMap<String, OWLObject>();
		final OWLAnnotationProperty altIdProperty = getAnnotationProperty(OboFormatTag.TAG_ALT_ID.getTag());
		if (altIdProperty == null) {
			return Collections.emptyMap();
		}
		for (OWLOntology o : getAllOntologies()) {
			Set<OWLAnnotationAssertionAxiom> aas = o.getAxioms(AxiomType.ANNOTATION_ASSERTION);
			for (OWLAnnotationAssertionAxiom aa : aas) {
				OWLAnnotationValue v = aa.getValue();
				OWLAnnotationProperty property = aa.getProperty();
				if (altIdProperty.equals(property) && v instanceof OWLLiteral) {
					String altId = ((OWLLiteral)v).getLiteral();
					OWLAnnotationSubject subject = aa.getSubject();
					if (subject instanceof IRI) {
						OWLObject obj = getOWLObject((IRI) subject);
						if (obj != null) {
							results.put(altId, obj);
						}
					}
				}
			}
		}
		return results;
	}

	/**
	 * It translates a oboformat tag into an OWL annotation property
	 * 
	 * @param tag
	 * @return {@link OWLAnnotationProperty}
	 */
	public OWLAnnotationProperty getAnnotationProperty(String tag){
		return getDataFactory().getOWLAnnotationProperty(trTagToIRI(tag));
	}

	/**
	 * fetches an OWL Object by rdfs:label
	 * <p>
	 * if there is >1 match, return the first one encountered
	 * 
	 * @param label
	 * @return object or null
	 */
	public OWLObject getOWLObjectByLabel(String label) {
		IRI iri = getIRIByLabel(label);
		if (iri != null)
			return getOWLObject(iri);
		return null;
	}

	/**
	 * fetches an OWL IRI by rdfs:label
	 * 
	 * @param label
	 * @return IRI or null
	 */
	public IRI getIRIByLabel(String label) {
		try {
			return getIRIByLabel(label, false);
		} catch (Exception e) {
			// note that it should be impossible to reach this point
			// if getIRIByLabel is called with isEnforceUnivocal = false
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * fetches an OWL IRI by rdfs:label, optionally testing for uniqueness
	 * <p>
	 * TODO: index labels. This currently scans all labels in the ontology, which is expensive
	 * 
	 * @param label
	 * @param isEnforceUnivocal
	 * @return IRI or null
	 * @throws SharedLabelException if >1 IRI shares input label
	 */
	public IRI getIRIByLabel(String label, boolean isEnforceUnivocal) throws Exception {
		IRI iri = null;
		for (OWLOntology o : getAllOntologies()) {
			Set<OWLAnnotationAssertionAxiom> aas = o.getAxioms(AxiomType.ANNOTATION_ASSERTION);
			for (OWLAnnotationAssertionAxiom aa : aas) {
				OWLAnnotationValue v = aa.getValue();
				OWLAnnotationProperty property = aa.getProperty();
				if (property.isLabel() && v instanceof OWLLiteral) {
					if (label.equals( ((OWLLiteral)v).getLiteral())) {
						OWLAnnotationSubject subject = aa.getSubject();
						if (subject instanceof IRI) {
							if (isEnforceUnivocal) {
								if (iri != null && !iri.equals((IRI)subject)) {
									throw new Exception();
								}
								iri = (IRI)subject;
							}
							else {
								return (IRI)subject;
							}
						}
						else {
							//return null;
						}
					}
				}
			}
		}
		return iri;
	}

	public IRI getIRIByIdentifier(String id) {
		return getIRIByIdentifier(id, false);
	}


}

