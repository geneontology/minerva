package org.geneontology.minerva;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.util.ManchesterSyntaxTool;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import owltools.graph.OWLGraphWrapper;
import owltools.vocab.OBOUpperVocabulary;

/**
 * Convenience layer for operations on collections of MolecularModels (aka lego diagrams)
 * 
 * This manager is intended to be used within a web server. Multiple clients can
 * contact the same manager instance through services
 * 
 * @param <METADATA> 
 * @see CoreMolecularModelManager
 * @see FileBasedMolecularModelManager
 */
public class MolecularModelManager<METADATA> extends FileBasedMolecularModelManager<METADATA> {
	
	private final CurieHandler curieHandler;

	public static class UnknownIdentifierException extends Exception {

		// generated
		private static final long serialVersionUID = -847970910712518838L;

		/**
		 * @param message
		 * @param cause
		 */
		public UnknownIdentifierException(String message, Throwable cause) {
			super(message, cause);
		}

		/**
		 * @param message
		 */
		public UnknownIdentifierException(String message) {
			super(message);
		}

	}
	
	/**
	 * @param graph
	 * @param rf
	 * @param curieHandler
	 * @param modelIdPrefix 
	 * @throws OWLOntologyCreationException
	 */
	public MolecularModelManager(OWLGraphWrapper graph, OWLReasonerFactory rf,
			CurieHandler curieHandler, String modelIdPrefix) throws OWLOntologyCreationException {
		super(graph, rf, modelIdPrefix);
		this.curieHandler = curieHandler;
	}

	/**
	 * @return the curieHandler
	 */
	public CurieHandler getCuriHandler() {
		return curieHandler;
	}

	/**
	 * @param modelId
	 * @param qs
	 * @return all individuals in the model that satisfy q
	 * @throws UnknownIdentifierException
	 */
	public Set<OWLNamedIndividual> getIndividualsByQuery(IRI modelId, String qs) throws UnknownIdentifierException {
		ModelContainer mod = checkModelId(modelId);
		ManchesterSyntaxTool mst = new ManchesterSyntaxTool(new OWLGraphWrapper(mod.getAboxOntology()), false);
		OWLClassExpression q = mst.parseManchesterExpression(qs);
		return getIndividualsByQuery(mod, q);
	}

	/**
	 * Shortcut for {@link CoreMolecularModelManager#createIndividual}
	 * 
	 * @param modelId
	 * @param cid
	 * @param annotations
	 * @param metadata
	 * @return id and individual
	 * @throws UnknownIdentifierException 
	 */
	public OWLNamedIndividual createIndividual(IRI modelId, String cid, Set<OWLAnnotation> annotations, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		OWLClass cls = getClass(cid, model);
		if (cls == null) {
			throw new UnknownIdentifierException("Could not find a class for id: "+cid);
		}
		OWLNamedIndividual i = createIndividual(model, cls, annotations , true, metadata);
		return i;
	}
	
	
	/**
	 * Shortcut for {@link CoreMolecularModelManager#createIndividual}.
	 * 
	 * @param modelId
	 * @param cid
	 * @param annotations
	 * @param metadata
	 * @return id and created individual
	 * @throws UnknownIdentifierException 
	 */
	public OWLNamedIndividual createIndividualNonReasoning(IRI modelId, String cid, Set<OWLAnnotation> annotations, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		OWLClass cls = getClass(cid, model);
		if (cls == null) {
			throw new UnknownIdentifierException("Could not find a class for id: "+cid);
		}
		return createIndividualNonReasoning(modelId, cls, annotations, metadata);
	}
	
	/**
	 * Shortcut for {@link CoreMolecularModelManager#createIndividual}.
	 * 
	 * @param modelId
	 * @param ce
	 * @param annotations
	 * @param metadata
	 * @return id and created individual
	 * @throws UnknownIdentifierException 
	 */
	public OWLNamedIndividual createIndividualNonReasoning(IRI modelId, OWLClassExpression ce, Set<OWLAnnotation> annotations, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		OWLNamedIndividual i = createIndividual(model, ce, annotations, false, metadata);
		return i;
	}
	
	/**
	 * Shortcut for {@link CoreMolecularModelManager#createIndividual}.
	 * 
	 * @param model
	 * @param annotations
	 * @param metadata
	 * @return id and created individual
	 */
	public OWLNamedIndividual createIndividualNonReasoning(ModelContainer model, Set<OWLAnnotation> annotations, METADATA metadata) {
		OWLNamedIndividual i = createIndividual(model, (OWLClassExpression)null, annotations, false, metadata);
		return i;
	}
	
	/**
	 * Shortcut for {@link CoreMolecularModelManager#createIndividual}.
	 * 
	 * @param model
	 * @param individualIRI
	 * @param annotations
	 * @param metadata
	 * @return id and created individual
	 * @throws UnknownIdentifierException 
	 */
	public OWLNamedIndividual createIndividualNonReasoning(ModelContainer model, IRI individualIRI, Set<OWLAnnotation> annotations, METADATA metadata) throws UnknownIdentifierException {
		OWLNamedIndividual i = createIndividualWithIRI(model, individualIRI, annotations, false, metadata);
		return i;
	}

	public OWLNamedIndividual getNamedIndividual(ModelContainer model, String iid) throws UnknownIdentifierException {
		OWLNamedIndividual i = getIndividual(iid, model);
		if (i == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+iid);
		}
		return i;
	}
	
	/**
	 * Deletes an individual and return all IRIs used as an annotation value
	 * 
	 * @param modelId
	 * @param iid
	 * @param metadata
	 * @return delete information
	 * @throws UnknownIdentifierException
	 */
	public DeleteInformation deleteIndividual(IRI modelId, String iid, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		OWLNamedIndividual i = getIndividual(iid, model);
		if (i == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+iid);
		}
		return deleteIndividual(model, i, true, metadata);
	}
	/**
	 * Deletes an individual and return all IRIs used as an annotation value
	 * 
	 * @param model
	 * @param i
	 * @param metadata
	 * @return delete information
	 */
	public DeleteInformation deleteIndividualNonReasoning(ModelContainer model, OWLNamedIndividual i, METADATA metadata) {
		return deleteIndividual(model, i, false, metadata);
	}
	
	/**
	 * Deletes an individual and return all IRIs used as an annotation value
	 * 
	 * @param modelId
	 * @param i
	 * @param metadata
	 * @return delete information
	 * @throws UnknownIdentifierException
	 */
	public DeleteInformation deleteIndividualNonReasoning(IRI modelId, OWLNamedIndividual i, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		return deleteIndividual(model, i, false, metadata);
	}
	
	/**
	 * Deletes an individual
	 * 
	 * @param modelId
	 * @param iri
	 * @param metadata
	 * @throws UnknownIdentifierException
	 */
	public void deleteIndividualNonReasoning(IRI modelId, IRI iri, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		OWLNamedIndividual i = getIndividual(iri, model);
		if (i == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+iri);
		}
		deleteIndividual(model, i, false, metadata);
	}
	
	public OWLNamedIndividual addAnnotations(IRI modelId, String iid, 
			Set<OWLAnnotation> annotations, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		OWLNamedIndividual i = getIndividual(iid, model);
		if (i == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+iid);
		}
		if (annotations != null && !annotations.isEmpty()) {
			addAnnotations(model, i.getIRI(), annotations, metadata);
		}
		return i;
	}
	
	public void addAnnotations(IRI modelId, IRI subject, 
			Set<OWLAnnotation> annotations, METADATA metadata) throws UnknownIdentifierException {
		if (annotations != null && !annotations.isEmpty()) {
			ModelContainer model = checkModelId(modelId);
			addAnnotations(model, subject, annotations, metadata);
		}
	}
	
	public OWLNamedIndividual updateAnnotation(ModelContainer model, OWLNamedIndividual i, 
			OWLAnnotation annotation, METADATA metadata) {
		if (annotation != null) {
			updateAnnotation(model, i.getIRI(), annotation, metadata);
		}
		return i;
	}
	
	public void updateAnnotation(IRI modelId, IRI subject, 
			OWLAnnotation annotation, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		if (annotation != null) {
			updateAnnotation(model, subject, annotation, metadata);
		}
	}
	
	public OWLNamedIndividual removeAnnotations(ModelContainer model, OWLNamedIndividual i,
			Set<OWLAnnotation> annotations, METADATA metadata) {
		if (annotations != null && !annotations.isEmpty()) {
			removeAnnotations(model, i.getIRI(), annotations, metadata);
		}
		return i;
	}
	
	/**
	 * @param id
	 */
	public void deleteModel(String id) {
		modelMap.remove(id);
	}
	
	public Set<IRI> searchModels(Collection<String> ids) throws IOException {
		final Set<IRI> resultSet = new HashSet<>();
		// create IRIs
		Set<IRI> searchIRIs = new HashSet<IRI>();
		for(String id : ids) {
			searchIRIs.add(graph.getIRIByIdentifier(id));
		}
		
		if (!searchIRIs.isEmpty()) {
			// search for IRI usage in models
			final Set<IRI> allModelIds = getAvailableModelIds();
			for (IRI modelId : allModelIds) {
				final ModelContainer model = getModel(modelId);
				final OWLOntology aboxOntology = model.getAboxOntology();
				Set<OWLEntity> signature = aboxOntology.getSignature();
				for (OWLEntity entity : signature) {
					if (searchIRIs.contains(entity.getIRI())) {
						resultSet.add(modelId);
						break;
					}
				}
			}
		}
		// return results
		return resultSet;
	}
	
	private OWLNamedIndividual getIndividual(String indId, ModelContainer model) {
		IRI iri = curieHandler.getIRI(indId);
		return getIndividual(iri, model);
	}
	public OWLNamedIndividual getIndividual(IRI iri, ModelContainer model) {
		// check that individual is actually declared
		boolean containsIRI = model.getAboxOntology().containsEntityInSignature(iri);
		if (containsIRI == false) {
			return null;
		}
		OWLNamedIndividual individual = model.getOWLDataFactory().getOWLNamedIndividual(iri);
		return individual;
	}
	private OWLClass getClass(String cid, ModelContainer model) {
		OWLGraphWrapper graph = new OWLGraphWrapper(model.getAboxOntology());
		return getClass(cid, graph);
	}
	private OWLClass getClass(String cid, OWLGraphWrapper graph) {
		IRI iri = curieHandler.getIRI(cid);
		return graph.getOWLClass(iri);
	}
	public OWLObjectProperty getObjectProperty(String pid, ModelContainer model) {
		OWLGraphWrapper graph = new OWLGraphWrapper(model.getAboxOntology());
		IRI iri = curieHandler.getIRI(pid);
		return graph.getOWLObjectProperty(iri);
	}
	
	public ModelContainer checkModelId(IRI modelId) throws UnknownIdentifierException {
		ModelContainer model = getModel(modelId);
		if (model == null) {
			throw new UnknownIdentifierException("Could not find a model for id: "+modelId);
		}
		return model;
	}

	private OWLObjectPropertyExpression getObjectProperty(OBOUpperVocabulary vocabElement,
			ModelContainer model) {
		return vocabElement.getObjectProperty(model.getAboxOntology());
	}

	/**
	 * Convenience wrapper for {@link CoreMolecularModelManager#addType}
	 * 
	 * @param modelId
	 * @param iid
	 * @param cid
	 * @param metadata
	 * @throws UnknownIdentifierException 
	 */
	public void addType(IRI modelId, String iid, String cid, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		OWLNamedIndividual individual = getIndividual(iid, model);
		if (individual == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+iid);
		}
		OWLClass cls = getClass(cid, model);
		if (cls == null) {
			throw new UnknownIdentifierException("Could not find a class for id: "+cid);
		}
		addType(model, individual, cls, true, metadata);
	}
	
	/**
	 * @param model
	 * @param individual
	 * @param clsExp
	 * @param metadata
	 * @return individual
	 */
	public OWLNamedIndividual addTypeNonReasoning(ModelContainer model, OWLNamedIndividual individual, OWLClassExpression clsExp, METADATA metadata) {
		addType(model, individual, clsExp, false, metadata);
		return individual;
	}
	
	/**
	 * Convenience wrapper for {@link CoreMolecularModelManager#addType}.
	 * 
	 * @param modelId
	 * @param iid
	 * @param pid
	 * @param cid
	 * @param metadata
	 * @throws UnknownIdentifierException 
	 */
	public void addType(IRI modelId,
			String iid, String pid, String cid, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		OWLNamedIndividual individual = getIndividual(iid, model);
		if (individual == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+iid);
		}
		OWLObjectProperty property = getObjectProperty(pid, model);
		if (property == null) {
			throw new UnknownIdentifierException("Could not find a property for id: "+pid);
		}
		OWLClass cls = getClass(cid, model);
		if (cls == null) {
			throw new UnknownIdentifierException("Could not find a class for id: "+cid);
		}
		addType(model, individual, property, cls, true, metadata);
	}
	
	public OWLNamedIndividual addTypeNonReasoning(IRI modelId,
			String iid, String pid, OWLClassExpression ce, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		OWLNamedIndividual individual = getIndividual(iid, model);
		if (individual == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+iid);
		}
		OWLObjectProperty property = getObjectProperty(pid, model);
		if (property == null) {
			throw new UnknownIdentifierException("Could not find a property for id: "+pid);
		}
		addType(model, individual, property, ce, false, metadata);
		return individual;
	}
	
	/**
	 * Convenience wrapper for {@link CoreMolecularModelManager#removeType}
	 * 
	 * @param modelId
	 * @param iid
	 * @param cid
	 * @param metadata
	 * @throws UnknownIdentifierException 
	 */
	public void removeType(IRI modelId, String iid, String cid, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		OWLNamedIndividual individual = getIndividual(iid, model);
		if (individual == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+iid);
		}
		OWLClass cls = getClass(cid, model);
		if (cls == null) {
			throw new UnknownIdentifierException("Could not find a class for id: "+cid);
		}
		removeType(model, individual, cls, true, metadata);
	}
	
	public OWLNamedIndividual removeTypeNonReasoning(ModelContainer model, OWLNamedIndividual individual, OWLClassExpression clsExp, METADATA metadata) {
		removeType(model, individual, clsExp, false, metadata);
		return individual;
	}
	
	/**
	 * Convenience wrapper for {@link CoreMolecularModelManager#addFact}
	 * 
	 * @param modelId
	 * @param pid
	 * @param iid
	 * @param jid
	 * @param annotations 
	 * @param metadata
	 * @return relevant individuals
	 * @throws UnknownIdentifierException 
	 */
	public List<OWLNamedIndividual> addFact(IRI modelId, String pid,	String iid, String jid,
			Set<OWLAnnotation> annotations, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		OWLObjectProperty property = getObjectProperty(pid, model);
		if (property == null) {
			throw new UnknownIdentifierException("Could not find a property for id: "+pid);
		}
		OWLNamedIndividual individual1 = getIndividual(iid, model);
		if (individual1 == null) {
			throw new UnknownIdentifierException("Could not find a individual (1) for id: "+iid);
		}
		OWLNamedIndividual individual2 = getIndividual(jid, model);
		if (individual2 == null) {
			throw new UnknownIdentifierException("Could not find a individual (2) for id: "+jid);
		}
		addFact(model, property, individual1, individual2, annotations, true, metadata);
		return Arrays.asList(individual1, individual2);
	}

	/**
	 * Convenience wrapper for {@link CoreMolecularModelManager#addFact}
	 * 
	 * @param model
	 * @param pid
	 * @param iid
	 * @param jid
	 * @param annotations 
	 * @param metadata
	 * @return relevant individuals
	 * @throws UnknownIdentifierException 
	 */
	public List<OWLNamedIndividual> addFactNonReasoning(ModelContainer model, String pid, String iid, String jid,
			Set<OWLAnnotation> annotations, METADATA metadata) throws UnknownIdentifierException {
		OWLObjectProperty property = getObjectProperty(pid, model);
		if (property == null) {
			throw new UnknownIdentifierException("Could not find a property for id: "+pid);
		}
		OWLNamedIndividual individual1 = getIndividual(iid, model);
		if (individual1 == null) {
			throw new UnknownIdentifierException("Could not find a individual (1) for id: "+iid);
		}
		OWLNamedIndividual individual2 = getIndividual(jid, model);
		if (individual2 == null) {
			throw new UnknownIdentifierException("Could not find a individual (2) for id: "+jid);
		}
		addFact(model, property, individual1, individual2, annotations, false, metadata);
		return Arrays.asList(individual1, individual2);
	}
	
	/**
	 * Convenience wrapper for {@link CoreMolecularModelManager#addFact}
	 * 
	 * @param model
	 * @param property 
	 * @param individual1 
	 * @param individual2 
	 * @param annotations 
	 * @param metadata
	 */
	public void addFactNonReasoning(ModelContainer model, OWLObjectProperty property, 
			OWLNamedIndividual individual1, OWLNamedIndividual individual2,
			Set<OWLAnnotation> annotations, METADATA metadata) {
		addFact(model, property, individual1, individual2, annotations, false, metadata);
	}
	
	/**
	 * Convenience wrapper for {@link CoreMolecularModelManager#addFact}
	 * 
	 * @param modelId
	 * @param vocabElement
	 * @param iid
	 * @param jid
	 * @param annotations
	 * @param metadata
	 * @return relevant individuals
	 * @throws UnknownIdentifierException
	 */
	public List<OWLNamedIndividual> addFact(IRI modelId, OBOUpperVocabulary vocabElement,
			String iid, String jid, Set<OWLAnnotation> annotations, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		OWLObjectPropertyExpression property = getObjectProperty(vocabElement, model);
		if (property == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+vocabElement);
		}
		OWLNamedIndividual individual1 = getIndividual(iid, model);
		if (individual1 == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+iid);
		}
		OWLNamedIndividual individual2 = getIndividual(jid, model);
		if (individual2 == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+jid);
		}
		addFact(model, property, individual1, individual2, annotations, true, metadata);
		return Arrays.asList(individual1, individual2);
	}
	
	/**
	 * @param modelId
	 * @param pid
	 * @param iid
	 * @param jid
	 * @param metadata
	 * @return response info
	 * @throws UnknownIdentifierException 
	 */
	public List<OWLNamedIndividual> removeFact(IRI modelId, String pid,
			String iid, String jid, METADATA metadata) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		OWLObjectProperty property = getObjectProperty(pid, model);
		if (property == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+pid);
		}
		OWLNamedIndividual individual1 = getIndividual(iid, model);
		if (individual1 == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+iid);
		}
		OWLNamedIndividual individual2 = getIndividual(jid, model);
		if (individual2 == null) {
			throw new UnknownIdentifierException("Could not find a individual for id: "+jid);
		}
		removeFact(model, property, individual1, individual2, true, metadata);
		return Arrays.asList(individual1, individual2);
	}
	
	public Set<IRI> removeFactNonReasoning(ModelContainer model, OWLObjectProperty property,
			OWLNamedIndividual individual1, OWLNamedIndividual individual2, METADATA metadata) throws UnknownIdentifierException {
		Set<IRI> iriSet = removeFact(model, property, individual1, individual2, false, metadata);
		return iriSet;
	}

	public List<OWLNamedIndividual> addAnnotations(ModelContainer model, String pid, 
			String iid, String jid, Set<OWLAnnotation> annotations, METADATA metadata) throws UnknownIdentifierException {
		OWLObjectProperty property = getObjectProperty(pid, model);
		if (property == null) {
			throw new UnknownIdentifierException("Could not find a property for id: "+pid);
		}
		OWLNamedIndividual individual1 = getIndividual(iid, model);
		if (individual1 == null) {
			throw new UnknownIdentifierException("Could not find a individual (1) for id: "+iid);
		}
		OWLNamedIndividual individual2 = getIndividual(jid, model);
		if (individual2 == null) {
			throw new UnknownIdentifierException("Could not find a individual (2) for id: "+jid);
		}
		addAnnotations(model, property, individual1, individual2, annotations, false, metadata);

		return Arrays.asList(individual1, individual2);
	}
	
	public void addAnnotations(ModelContainer model, Set<OWLObjectPropertyAssertionAxiom> axioms, Set<OWLAnnotation> annotations, METADATA metadata) {
		for (OWLObjectPropertyAssertionAxiom axiom : axioms) {
			addAnnotations(model, axiom, annotations, false, metadata);	
		}
	}
	
	public void updateAnnotation(ModelContainer model, OWLObjectProperty property, 
			OWLNamedIndividual individual1, OWLNamedIndividual individual2, OWLAnnotation annotation, METADATA metadata) {
		updateAnnotation(model, property, individual1, individual2, annotation, false, metadata);
	}
	
	public Set<OWLObjectPropertyAssertionAxiom> updateAnnotation(ModelContainer model, Set<OWLObjectPropertyAssertionAxiom> axioms, OWLAnnotation annotation, METADATA metadata) {
		Set<OWLObjectPropertyAssertionAxiom> newAxioms = new HashSet<OWLObjectPropertyAssertionAxiom>();
		for (OWLObjectPropertyAssertionAxiom axiom : axioms) {
			OWLObjectPropertyAssertionAxiom newAxiom = 
					updateAnnotation(model, axiom, annotation, false, metadata);
			if (newAxiom != null) {
				newAxioms.add(newAxiom);
			}
		}
		return newAxioms;
	}
	
	public void removeAnnotations(ModelContainer model, OWLObjectProperty property, 
			OWLNamedIndividual individual1, OWLNamedIndividual individual2, Set<OWLAnnotation> annotations, METADATA metadata) {
		removeAnnotations(model, property, individual1, individual2, annotations, false, metadata);
	}
	
	public OWLNamedIndividual addDataProperties(ModelContainer model, OWLNamedIndividual i,
			Map<OWLDataProperty, Set<OWLLiteral>> dataProperties, METADATA token) {
		if (dataProperties != null && !dataProperties.isEmpty()) {
			for(Entry<OWLDataProperty, Set<OWLLiteral>> entry : dataProperties.entrySet()) {
				for(OWLLiteral literal : entry.getValue()) {
					addDataProperty(model, i, entry.getKey(), literal, false, token);
				}
			}
		}
		return i;
	}
	
	public OWLNamedIndividual removeDataProperties(ModelContainer model, OWLNamedIndividual i,
			Map<OWLDataProperty, Set<OWLLiteral>> dataProperties, METADATA token) {
		if (dataProperties != null && !dataProperties.isEmpty()) {
			for(Entry<OWLDataProperty, Set<OWLLiteral>> entry : dataProperties.entrySet()) {
				for(OWLLiteral literal : entry.getValue()) {
					removeDataProperty(model, i, entry.getKey(), literal, false, token);
				}
			}
		}
		return i;
	}
	
	/**
	 * This method will check the given model and update the import declarations.
	 * It will add missing IRIs and remove obsolete ones.
	 * 
	 * @param modelId 
	 * @throws UnknownIdentifierException
	 * @see #additionalImports
	 * @see #addImports(Iterable)
	 */
	public void updateImports(IRI modelId) throws UnknownIdentifierException {
		ModelContainer model = checkModelId(modelId);
		updateImports(model);
	}
	
}
