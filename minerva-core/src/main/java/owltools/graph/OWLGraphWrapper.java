package owltools.graph;


import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.obolibrary.obo2owl.Obo2OWLConstants;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;


/**
 * bastardization of owltools owlgraphwrapper
 */
public class OWLGraphWrapper implements Closeable {

	public static final String DEFAULT_IRI_PREFIX = Obo2OWLConstants.DEFAULT_IRI_PREFIX;

	@Deprecated
	OWLOntology ontology; // this is the ontology used for querying. may be the merge of sourceOntology+closure


	/**
	 * Create a new wrapper for an OWLOntology
	 * 
	 * @param ontology 
	 */
	public OWLGraphWrapper(OWLOntology ontology) {
	}


	public OWLGraphWrapper(OWLOntology parse, boolean isMergeImportClosure) {
		// TODO Auto-generated constructor stub
	}


	public OWLClass getOWLClass(IRI iri) {
		// TODO Auto-generated method stub
		return null;
	}


	public OWLObjectProperty getOWLObjectProperty(IRI iri) {
		// TODO Auto-generated method stub
		return null;
	}


	public String getLabel(OWLNamedObject i) {
		// TODO Auto-generated method stub
		return null;
	}


	public OWLOntology getSourceOntology() {
		// TODO Auto-generated method stub
		return null;
	}


	public OWLOntologyManager getManager() {
		// TODO Auto-generated method stub
		return null;
	}


	public void addSupportOntology(OWLOntology ontology2) {
		// TODO Auto-generated method stub
		
	}


	public Set<OWLOntology> getAllOntologies() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub
		
	}


	public Set<OWLClass> getAllOWLClasses() {
		// TODO Auto-generated method stub
		return null;
	}


	public OWLClass getOWLClassByIdentifier(String ecoId) {
		// TODO Auto-generated method stub
		return null;
	}


	public OWLObject getOWLObjectByIdentifier(String id) {
		// TODO Auto-generated method stub
		return null;
	}


	public String getLabel(OWLObject obj) {
		// TODO Auto-generated method stub
		return null;
	}


	public OWLDataFactory getDataFactory() {
		// TODO Auto-generated method stub
		return null;
	}


	public OWLObjectProperty getOWLObjectPropertyByIdentifier(String string) {
		// TODO Auto-generated method stub
		return null;
	}


	public void mergeImportClosure(boolean b) {
		// TODO Auto-generated method stub
		
	}


	public Map<String, OWLObject> getAllOWLObjectsByAltId() {
		// TODO Auto-generated method stub
		return null;
	}


	public OWLObject getOWLObjectByLabel(String rel) {
		// TODO Auto-generated method stub
		return null;
	}


	public void setSourceOntology(OWLOntology full_tbox) {
		// TODO Auto-generated method stub
		
	}


	public IRI getIRIByIdentifier(String string) {
		// TODO Auto-generated method stub
		return null;
	}


}

