package org.geneontology.minerva;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.IRIDocumentSource;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationSubject;
import org.semanticweb.owlapi.model.OWLAnnotationSubjectVisitor;
import org.semanticweb.owlapi.model.OWLAnnotationValueVisitor;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyAlreadyExistsException;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyFormat;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.model.RemoveOntologyAnnotation;
import org.semanticweb.owlapi.model.SetOntologyID;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import owltools.graph.OWLGraphWrapper;
import owltools.vocab.OBOUpperVocabulary;

/**
 * Manager and core operations for in memory MolecularModels (aka lego diagrams).
 * 
 * Any number of models can be loaded at any time <br>
 * TODO - impose some limit to avoid using too much memory
 * 
 * each model has a generator, an OWLOntology (containing the set of class assertions)
 * and a reasoner associated with it<br>
 * TODO - test memory requirements
 * 
 * @param <METADATA> object for holding meta data associated with each operation
 */
public abstract class CoreMolecularModelManager<METADATA> {
	
	private static Logger LOG = Logger.getLogger(CoreMolecularModelManager.class);

	final OWLGraphWrapper graph;
	final OWLReasonerFactory rf;
	private final IRI tboxIRI;
	final Map<String, ModelContainer> modelMap = new HashMap<String, ModelContainer>();
	Set<IRI> additionalImports;

	/**
	 * Use start up time to create a unique prefix for id generation
	 */
	static String uniqueTop = Long.toHexString(Math.abs((System.currentTimeMillis()/1000)));
	static long instanceCounter = 0;
	
	/**
	 * Generate a new id from the unique server prefix and a global counter
	 * 
	 * @return id
	 */
	private static String localUnique(){
		instanceCounter++;
		String unique = uniqueTop + String.format("%08d", instanceCounter);
		return unique;		
	}
	
	/**
	 * Check that the given string looks similar to a local unique id
	 * 
	 * @param s
	 * @return true if the string looks like a generated id
	 */
	static boolean isLocalUnique(String s) {
		boolean result = false;
		if (s != null && s.length() > 8) {
			result = true;
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				if (isHex(c) == false) {
					result = false;
					break;
				}
			}
		}
		return result;
	}
	
	private static boolean isHex(char c) {
		// check that char is a digit or a-e
		boolean result = false;
		if (Character.isDigit(c)) {
			result = true;
		}
		else if (c == 'a' || c == 'b' || c == 'c' || c == 'd' || c == 'e' || c == 'f') {
			result = true;
		}
		return result;
	}
	
	/**
	 * Generate an id and prepend the given prefixes.
	 * 
	 * This method must should be used for model identifiers and individual identifiers.
	 * 
	 * @param prefixes
	 * @return id
	 */
	static String generateId(CharSequence...prefixes) {
		StringBuilder sb = new StringBuilder();
		for (CharSequence prefix : prefixes) {
			sb.append(prefix);
		}
		sb.append(localUnique());
		return sb.toString();
	}

	/**
	 * @param graph
	 * @param rf
	 * @throws OWLOntologyCreationException
	 */
	public CoreMolecularModelManager(OWLGraphWrapper graph, OWLReasonerFactory rf) throws OWLOntologyCreationException {
		super();
		this.graph = graph;
		this.rf = rf;
		tboxIRI = getTboxIRI(graph);
		init();
	}
	
	/**
	 * Executed before the init call {@link #init()}.
	 * 
	 * @param graph
	 * @return IRI, never null
	 * @throws OWLOntologyCreationException
	 */
	protected IRI getTboxIRI(OWLGraphWrapper graph) throws OWLOntologyCreationException {
		OWLOntology tbox = graph.getSourceOntology();
		OWLOntologyID ontologyID = tbox.getOntologyID();
		if (ontologyID != null) {
			IRI ontologyIRI = ontologyID.getOntologyIRI();
			if (ontologyIRI != null) {
				return ontologyIRI;
			}
		}
		throw new OWLOntologyCreationException("No ontology id available for tbox. An ontology IRI is required for the import into the abox.");
	}

	/**
	 * @throws OWLOntologyCreationException
	 */
	protected void init() throws OWLOntologyCreationException {
		// set default imports
		additionalImports = new HashSet<IRI>();
	}


	/**
	 * @return graph wrapper for core/source ontology
	 */
	public OWLGraphWrapper getGraph() {
		return graph;
	}

	/**
	 * @return core/source ontology
	 */
	public OWLOntology getOntology() {
		return graph.getSourceOntology();
	}


	/**
	 * Add additional import declarations for any newly generated model.
	 * 
	 * @param imports
	 */
	public void addImports(Iterable<String> imports) {
		if (imports != null) {
			for (String importIRIString : imports) {
				additionalImports.add(IRI.create(importIRIString));
			}
		}
	}
	
	public Collection<IRI> getImports() {
		Set<IRI> allImports = new HashSet<IRI>();
		allImports.add(tboxIRI);
		allImports.addAll(additionalImports);
		return allImports;
	}

	/**
	 * 
	 * @param modelId
	 * @return all individuals in the model
	 */
	public Set<OWLNamedIndividual> getIndividuals(String modelId) {
		ModelContainer mod = getModel(modelId);
		return mod.getAboxOntology().getIndividualsInSignature();
	}

	
	/**
	 * @param modelId
	 * @param q
	 * @return all individuals in the model that satisfy q
	 */
	public Set<OWLNamedIndividual> getIndividualsByQuery(String modelId, OWLClassExpression q) {
		ModelContainer mod = getModel(modelId);
		return mod.getReasoner().getInstances(q, false).getFlattened();
	}

	/**
	 * @param model
	 * @param ce
	 * @param metadata
	 * @return individual
	 */
	public OWLNamedIndividual createIndividual(ModelContainer model, OWLClassExpression ce, METADATA metadata) {
		OWLNamedIndividual individual = createIndividual(model, ce, null, true, metadata);
		return individual;
	}
	
	OWLNamedIndividual createIndividual(ModelContainer model, OWLClassExpression ce, Set<OWLAnnotation> annotations, boolean flushReasoner, METADATA metadata) {
		Pair<OWLNamedIndividual, Set<OWLAxiom>> pair = createIndividual(model.getModelId(), model.getAboxOntology(), ce, annotations);
		addAxioms(model, pair.getRight(), flushReasoner, metadata);
		return pair.getLeft();
	}
	
	OWLNamedIndividual createIndividualWithIRI(ModelContainer model, IRI individualIRI, Set<OWLAnnotation> annotations, boolean flushReasoner, METADATA metadata) {
		Pair<OWLNamedIndividual, Set<OWLAxiom>> pair = createIndividual(individualIRI, model.getAboxOntology(), null, annotations);
		addAxioms(model, pair.getRight(), flushReasoner, metadata);
		return pair.getLeft();
	}
	
	public static Pair<OWLNamedIndividual, Set<OWLAxiom>> createIndividual(String modelId, OWLOntology abox, OWLClassExpression ce, Set<OWLAnnotation> annotations) {
		String iid = generateId(modelId, "/");
		IRI iri = IRI.create(iid);
		return createIndividual(iri, abox, ce, annotations);
	}
	
	private static Pair<OWLNamedIndividual, Set<OWLAxiom>> createIndividual(IRI iri, OWLOntology abox, OWLClassExpression ce, Set<OWLAnnotation> annotations) {
		OWLDataFactory f = abox.getOWLOntologyManager().getOWLDataFactory();
		OWLNamedIndividual i = f.getOWLNamedIndividual(iri);
		
		// create axioms
		Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
		// declaration
		axioms.add(f.getOWLDeclarationAxiom(i));
		// annotation assertions
		if(annotations != null) {
			for(OWLAnnotation annotation : annotations) {
				axioms.add(f.getOWLAnnotationAssertionAxiom(iri, annotation));
			}
		}
		
		if (ce != null) {
			OWLClassAssertionAxiom typeAxiom = createType(f, i, ce);
			if (typeAxiom != null) {
				axioms.add(typeAxiom);
			}
		}
		
		return Pair.of(i, axioms);
	}
	
	/**
	 * Deletes an individual and return all IRIs used as an annotation value
	 * 
	 * @param model
	 * @param i
	 * @param metadata
	 * @return set of IRIs used in annotations
	 */
	public DeleteInformation deleteIndividual(ModelContainer model, OWLNamedIndividual i, METADATA metadata) {
		return deleteIndividual(model, i, true, metadata);
	}
	
	public static class DeleteInformation {
		public final Set<IRI> usedIRIs = new HashSet<IRI>();
		public final Set<OWLObjectPropertyAssertionAxiom> updated = new HashSet<OWLObjectPropertyAssertionAxiom>();
		public final Set<IRI> touched = new HashSet<IRI>();
	}
	
	/**
	 * Deletes an individual and return all IRIs used as an annotation value.
	 * Also tries to delete all annotations (OWLObjectPropertyAssertionAxiom
	 * annotations and OWLAnnotationAssertionAxiom) with the individual IRI as
	 * value.
	 * 
	 * @param model
	 * @param i
	 * @param flushReasoner
	 * @param metadata
	 * @return set of IRIs used in annotations
	 */
	public DeleteInformation deleteIndividual(ModelContainer model, OWLNamedIndividual i, boolean flushReasoner, METADATA metadata) {
		Set<OWLAxiom> toRemoveAxioms = new HashSet<OWLAxiom>();
		final DeleteInformation deleteInformation = new DeleteInformation();
		
		final OWLOntology ont = model.getAboxOntology();
		final OWLDataFactory f = model.getOWLDataFactory();
		
		// Declaration axiom
		toRemoveAxioms.add(model.getOWLDataFactory().getOWLDeclarationAxiom(i));
		
		// Logic axiom
		for (OWLAxiom ax : ont.getAxioms(i)) {
			extractIRIValues(ax.getAnnotations(), deleteInformation.usedIRIs);
			toRemoveAxioms.add(ax);
		}
		
		// OWLObjectPropertyAssertionAxiom
		Set<OWLObjectPropertyAssertionAxiom> allAssertions = ont.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION);
		final IRI iIRI = i.getIRI();
		for (OWLObjectPropertyAssertionAxiom ax : allAssertions) {
			if (toRemoveAxioms.contains(ax) == false) {
				Set<OWLNamedIndividual> currentIndividuals = ax.getIndividualsInSignature();
				if (currentIndividuals.contains(i)) {
					extractIRIValues(ax.getAnnotations(), deleteInformation.usedIRIs);
					toRemoveAxioms.add(ax);
					continue;
				}
				// check annotations for deleted individual IRI
				Set<OWLAnnotation> annotations = ax.getAnnotations();
				Set<OWLAnnotation> removeAnnotations = new HashSet<OWLAnnotation>();
				for (OWLAnnotation annotation : annotations) {
					if (iIRI.equals(annotation.getValue())) {
						removeAnnotations.add(annotation);
					}
				}
				// if there is an annotations that needs to be removed, 
				// recreate axiom with cleaned annotation set
				if (removeAnnotations.isEmpty() == false) {
					annotations.removeAll(removeAnnotations);
					toRemoveAxioms.add(ax);
					deleteInformation.updated.add(f.
							getOWLObjectPropertyAssertionAxiom(
									ax.getProperty(), ax.getSubject(), ax.getObject(), annotations));
				}
			}
		}
		// OWLAnnotationAssertionAxiom
		Set<OWLAnnotationAssertionAxiom> annotationAssertionAxioms = ont.getAnnotationAssertionAxioms(i.getIRI());
		for (OWLAnnotationAssertionAxiom axiom : annotationAssertionAxioms) {
			extractIRIValues(axiom.getAnnotation(), deleteInformation.usedIRIs);
			toRemoveAxioms.add(axiom);	
		}
		
		// search for all annotations which use individual IRI as value
		Set<OWLAnnotationAssertionAxiom> axioms = ont.getAxioms(AxiomType.ANNOTATION_ASSERTION);
		for (OWLAnnotationAssertionAxiom ax : axioms) {
			if (toRemoveAxioms.contains(ax) == false) {
				if (iIRI.equals(ax.getValue())) {
					toRemoveAxioms.add(ax);
					OWLAnnotationSubject subject = ax.getSubject();
					subject.accept(new OWLAnnotationSubjectVisitor() {
						
						@Override
						public void visit(OWLAnonymousIndividual individual) {
							// do nothing
						}
						
						@Override
						public void visit(IRI iri) {
							// check if they subject is a declared named individual
							if (ont.containsIndividualInSignature(iri)) {
								deleteInformation.touched.add(iri);
							}
						}
					});
				}
			}
		}
		
		removeAxioms(model, toRemoveAxioms, flushReasoner, metadata);
		if (deleteInformation.updated.isEmpty() == false) {
			addAxioms(model, deleteInformation.updated, flushReasoner, metadata);
		}
		
		return deleteInformation;
	}
	
	public static Set<IRI> extractIRIValues(Set<OWLAnnotation> annotations) {
		if (annotations == null || annotations.isEmpty()) {
			return Collections.emptySet();
		}
		Set<IRI> iriSet = new HashSet<IRI>();
		extractIRIValues(annotations, iriSet);
		return iriSet;
	}
	
	private static void extractIRIValues(Set<OWLAnnotation> annotations, final Set<IRI> iriSet) {
		if (annotations != null) {
			for (OWLAnnotation annotation : annotations) {
				extractIRIValues(annotation, iriSet);
			}
		}
	}
	
	private static void extractIRIValues(OWLAnnotation annotation, final Set<IRI> iriSet) {
		if (annotation != null) {
			annotation.getValue().accept(new OWLAnnotationValueVisitor() {

				@Override
				public void visit(OWLLiteral literal) {
					// ignore
				}

				@Override
				public void visit(OWLAnonymousIndividual individual) {
					// ignore
				}

				@Override
				public void visit(IRI iri) {
					iriSet.add(iri);
				}
			});
		}
	}
	
	public void addAnnotations(ModelContainer model, OWLNamedIndividual i, Collection<OWLAnnotation> annotations, METADATA metadata) {
		addAnnotations(model, i.getIRI(), annotations, metadata);
	}
	
	public void addAnnotations(ModelContainer model, IRI subject, Collection<OWLAnnotation> annotations, METADATA metadata) {
		Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
		OWLDataFactory f = model.getOWLDataFactory();
		for (OWLAnnotation annotation : annotations) {
			axioms.add(f.getOWLAnnotationAssertionAxiom(subject, annotation));
		}
		addAxioms(model, axioms, false, metadata);
	}
	
	public void updateAnnotation(ModelContainer model, IRI subject, OWLAnnotation update, METADATA metadata) {
		Set<OWLAxiom> removeAxioms = new HashSet<OWLAxiom>();
		OWLDataFactory f = model.getOWLDataFactory();
		Set<OWLAnnotationAssertionAxiom> existing = model.getAboxOntology().getAnnotationAssertionAxioms(subject);
		OWLAnnotationProperty target = update.getProperty();
		for (OWLAnnotationAssertionAxiom axiom : existing) {
			if (target.equals(axiom.getProperty())) {
				removeAxioms.add(axiom);
			}
		}
		removeAxioms(model, removeAxioms, false, metadata);
		addAxiom(model, f.getOWLAnnotationAssertionAxiom(subject, update), false, metadata);
	}
	
	public void addModelAnnotations(ModelContainer model, Collection<OWLAnnotation> annotations, METADATA metadata) {
		OWLOntology aBox = model.getAboxOntology();
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		for (OWLAnnotation annotation : annotations) {
			changes.add(new AddOntologyAnnotation(aBox, annotation));
		}
		applyChanges(model, changes, false, metadata);
	}
	
	public void updateAnnotation(ModelContainer model, OWLAnnotation update, METADATA metadata) {
		OWLOntology aBox = model.getAboxOntology();
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		Set<OWLAnnotation> existing = model.getAboxOntology().getAnnotations();
		OWLAnnotationProperty target = update.getProperty();
		for (OWLAnnotation annotation : existing) {
			if (target.equals(annotation.getProperty())) {
				changes.add(new RemoveOntologyAnnotation(aBox, annotation));
			}
		}
		changes.add(new AddOntologyAnnotation(aBox, update));
		applyChanges(model, changes, false, metadata);
	}

	public void removeAnnotations(ModelContainer model, OWLNamedIndividual i, Collection<OWLAnnotation> annotations, METADATA metadata) {
		removeAnnotations(model, i.getIRI(), annotations, metadata);
	}
	
	void removeAnnotations(ModelContainer model, IRI subject, Collection<OWLAnnotation> annotations, METADATA metadata) {
		OWLOntology ont = model.getAboxOntology();
		Set<OWLAxiom> toRemove = new HashSet<OWLAxiom>();
		Set<OWLAnnotationAssertionAxiom> candidates = ont.getAnnotationAssertionAxioms(subject);
		for (OWLAnnotationAssertionAxiom axiom : candidates) {
			OWLAnnotation annotation = axiom.getAnnotation();
			if (annotations.contains(annotation)) {
				toRemove.add(axiom);
			}
		}
		removeAxioms(model, toRemove, false, metadata);
	}

	public void removeAnnotations(ModelContainer model, Collection<OWLAnnotation> annotations, METADATA metadata) {
		OWLOntology aBox = model.getAboxOntology();
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		for (OWLAnnotation annotation : annotations) {
			changes.add(new RemoveOntologyAnnotation(aBox, annotation));
		}
		applyChanges(model, changes, false, metadata);
	}
	
	public void addDataProperty(ModelContainer model,
			OWLNamedIndividual i, OWLDataProperty prop, OWLLiteral literal,
			boolean flushReasoner, METADATA metadata) {
		OWLAxiom axiom = model.getOWLDataFactory().getOWLDataPropertyAssertionAxiom(prop, i, literal);
		addAxiom(model, axiom, flushReasoner, metadata);
	}
	
	public void removeDataProperty(ModelContainer model,
			OWLNamedIndividual i, OWLDataProperty prop, OWLLiteral literal,
			boolean flushReasoner, METADATA metadata) {
		OWLAxiom toRemove = null;
		Set<OWLDataPropertyAssertionAxiom> existing = model.getAboxOntology().getDataPropertyAssertionAxioms(i);
		for (OWLDataPropertyAssertionAxiom ax : existing) {
			if (prop.equals(ax.getProperty()) && literal.equals(ax.getObject())) {
				toRemove = ax;
				break;
			}
		}
		
		if (toRemove != null) {
			removeAxiom(model, toRemove, flushReasoner, metadata);
		}
	}
	
	/**
	 * Fetches a model by its Id
	 * 
	 * @param id
	 * @return wrapped model
	 */
	public ModelContainer getModel(String id)  {
		if (!modelMap.containsKey(id)) {
			try {
				loadModel(id, false);
			} catch (OWLOntologyCreationException e) {
				LOG.info("Could not load model with id: "+id, e);
			}
		}
		return modelMap.get(id);
	}
	
	/**
	 * Retrieve the abox ontology. May skip loading the imports.
	 * This method is mostly intended to read metadata from a model.
	 * 
	 * @param id
	 * @return abox, maybe without any imports loaded
	 */
	public OWLOntology getModelAbox(String id) {
		ModelContainer model = modelMap.get(id);
		if (model != null) {
			return model.getAboxOntology();
		}
		OWLOntology abox = null;
		try {
			abox = loadModelABox(id);
		} catch (OWLOntologyCreationException e) {
			LOG.info("Could not load model with id: "+id, e);
		}
		return abox;
	}
	
	/**
	 * @param modelId
	 * @return ontology
	 * @throws OWLOntologyCreationException
	 */
	protected abstract OWLOntology loadModelABox(String modelId) throws OWLOntologyCreationException;
	
	/**
	 * @param id
	 */
	public void unlinkModel(String id) {
		ModelContainer model = modelMap.get(id);
		model.dispose();
		modelMap.remove(id);
	}
	
	/**
	 * @return ids for all loaded models
	 */
	public Set<String> getModelIds() {
		return modelMap.keySet();
	}
	
	/**
	 * internal method to cleanup this instance
	 */
	public void dispose() {
		Set<String> ids = new HashSet<String>(getModelIds());
		for (String id : ids) {
			unlinkModel(id);
		}
	}

//	private synchronized SimpleEcoMapper getSimpleEcoMapper() throws IOException {
//		if (simpleEcoMapper == null) {
//			simpleEcoMapper = EcoMapperFactory.createSimple();
//		}
//		return simpleEcoMapper;
//	}
//	
//	/**
//	 * Export the model (ABox) in a legacy format, such as GAF or GPAD.
//	 * 
//	 * @param modelId
//	 * @param model
//	 * @param format format name or null for default
//	 * @return modelContent
//	 * @throws IOException
//	 */
//	public String exportModelLegacy(String modelId, ModelContainer model, String format) throws IOException {
//		final OWLOntology aBox = model.getAboxOntology();
//		SimpleEcoMapper ecoMapper = getSimpleEcoMapper();
//		LegoToGeneAnnotationTranslator translator = new LegoToGeneAnnotationTranslator(graph, model.getReasoner(), ecoMapper);
//		Pair<GafDocument,BioentityDocument> pair = translator.translate(modelId, aBox, null);
//		ByteArrayOutputStream outputStream = null;
//		try {
//			outputStream = new ByteArrayOutputStream();
//			if (format == null || "gaf".equalsIgnoreCase(format)) {
//				// GAF
//				GafWriter writer = new GafWriter();
//				try {
//					writer.setStream(new PrintStream(outputStream));
//					GafDocument gafdoc = pair.getLeft();
//					writer.write(gafdoc);
//				}
//				finally {
//					writer.close();
//				}
//
//			}
//			else if ("gpad".equalsIgnoreCase(format)) {
//				// GPAD version 1.2
//				GpadWriter writer = new GpadWriter(new PrintWriter(outputStream) , 1.2);
//				writer.write(pair.getLeft());
//			}
//			else {
//				throw new IOException("Unknown legacy format: "+format);
//			}
//			return outputStream.toString();
//		}
//		finally {
//			IOUtils.closeQuietly(outputStream);
//		}
//	}
	
	/**
	 * Export the ABox, will try to set the ontologyID to the given modelId (to
	 * ensure import assumptions are met)
	 * 
	 * @param model
	 * @param ontologyFormat
	 * @return modelContent
	 * @throws OWLOntologyStorageException
	 */
	public String exportModel(ModelContainer model, OWLOntologyFormat ontologyFormat) throws OWLOntologyStorageException {
		final OWLOntology aBox = model.getAboxOntology();
		final OWLOntologyManager manager = aBox.getOWLOntologyManager();
		
		// make sure the exported ontology has an ontologyId and that it maps to the modelId
		final IRI expectedABoxIRI = IRI.create(model.getModelId());
		OWLOntologyID ontologyID = aBox.getOntologyID();
		if (ontologyID == null) {
			manager.applyChange(new SetOntologyID(aBox, expectedABoxIRI));
		}
		else {
			IRI currentABoxIRI = ontologyID.getOntologyIRI();
			if (expectedABoxIRI.equals(currentABoxIRI) == false) {
				ontologyID = new OWLOntologyID(expectedABoxIRI, ontologyID.getVersionIRI());
				manager.applyChange(new SetOntologyID(aBox, ontologyID));
			}
		}

		// write the model into a buffer
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		if (ontologyFormat != null) {
			manager.saveOntology(aBox, ontologyFormat, outputStream);
		}
		else {
			manager.saveOntology(aBox, outputStream);
		}
		
		// extract the string from the buffer
		String modelString = outputStream.toString();
		return modelString;
	}
	
	/**
	 * Try to load (or replace) a model with the given ontology. It is expected
	 * that the content is an A-Box ontology, which imports the T-BOX. Also the
	 * ontology ID is used to extract the modelId.<br>
	 * <br>
	 * This method will currently <b>NOT<b> work due to a bug in the OWL-API.
	 * The functional syntax parser does not properly report the exceptions and
	 * will return an ontology with an wrong ontology ID!
	 * 
	 * @param modelData
	 * @return modelId
	 * @throws OWLOntologyCreationException
	 */
	public ModelContainer importModel(String modelData) throws OWLOntologyCreationException {
		// load data from String
		final OWLOntologyManager manager = graph.getManager();
		final OWLOntologyDocumentSource documentSource = new StringDocumentSource(modelData);
		OWLOntology modelOntology;
		try {
			modelOntology = manager.loadOntologyFromOntologyDocument(documentSource);
		}
		catch (OWLOntologyAlreadyExistsException e) {
			// exception is thrown if there is an ontology with the same ID already in memory 
			OWLOntologyID id = e.getOntologyID();
			String existingModelId = id.getOntologyIRI().toString();

			// remove the existing memory model
			unlinkModel(existingModelId);

			// try loading the import version (again)
			modelOntology = manager.loadOntologyFromOntologyDocument(documentSource);
		}
		
		// try to extract modelId
		String modelId = null;
		OWLOntologyID ontologyId = modelOntology.getOntologyID();
		if (ontologyId != null) {
			IRI iri = ontologyId.getOntologyIRI();
			if (iri != null) {
				modelId = iri.toString();
			}
		}
		if (modelId == null) {
			throw new OWLOntologyCreationException("Could not extract the modelId from the given model");
		}
		// paranoia check
		ModelContainer existingModel = modelMap.get(modelId);
		if (existingModel != null) {
			unlinkModel(modelId);
		}
		
		// add to internal model
		ModelContainer newModel = addModel(modelId, modelOntology);
		
		// update imports
		updateImports(newModel);
		
		return newModel;
	}
	
	protected abstract void loadModel(String modelId, boolean isOverride) throws OWLOntologyCreationException;

	ModelContainer addModel(String modelId, OWLOntology abox) throws OWLOntologyCreationException {
		OWLOntology tbox = graph.getSourceOntology();
		ModelContainer m = new ModelContainer(modelId, tbox, abox, rf);
		modelMap.put(modelId, m);
		return m;
	}

	
	/**
	 * @param modelId
	 * @return data factory for the specified model
	 */
	public OWLDataFactory getOWLDataFactory(String modelId) {
		ModelContainer model = getModel(modelId);
		return model.getOWLDataFactory();
	}

	protected OWLOntologyManager getOWLOntologyManager(String modelId) {
		ModelContainer model = getModel(modelId);
		return model.getAboxOntology().getOWLOntologyManager();
	}

	/**
	 * Adds ClassAssertion(c,i) to specified model
	 * 
	 * @param modelId
	 * @param i
	 * @param c
	 * @param metadata
	 */
	public void addType(String modelId, OWLNamedIndividual i, OWLClass c, METADATA metadata) {
		ModelContainer model = getModel(modelId);
		addType(model, i, c, true, metadata);
	}
	
	/**
	 * Adds ClassAssertion(c,i) to specified model
	 * 
	 * @param model
	 * @param i
	 * @param c
	 * @param flushReasoner
	 * @param metadata
	 */
	void addType(ModelContainer model, OWLIndividual i, 
			OWLClassExpression c, boolean flushReasoner, METADATA metadata) {
		OWLClassAssertionAxiom axiom = createType(model.getOWLDataFactory(), i, c);
		addAxiom(model, axiom, flushReasoner, metadata);
	}

	/**
	 * @param f
	 * @param i
	 * @param c
	 * @return axiom
	 */
	public static OWLClassAssertionAxiom createType(OWLDataFactory f, OWLIndividual i, OWLClassExpression c) {
		OWLClassAssertionAxiom axiom = f.getOWLClassAssertionAxiom(c,i);
		return axiom;
	}

	/**
	 * Adds a ClassAssertion, where the class expression instantiated is an
	 * ObjectSomeValuesFrom expression
	 * 
	 * Example: Individual: i Type: enabledBy some PRO_123 
	 * 
	 * @param modelId
	 * @param i
	 * @param p
	 * @param filler
	 * @param metadata
	 */
	public void addType(String modelId,
			OWLNamedIndividual i, 
			OWLObjectPropertyExpression p,
			OWLClassExpression filler,
			METADATA metadata) {
		ModelContainer model = getModel(modelId);
		addType(model, i, p, filler, true, metadata);
	}
	
	/**
	 * Adds a ClassAssertion, where the class expression instantiated is an
	 * ObjectSomeValuesFrom expression
	 * 
	 * Example: Individual: i Type: enabledBy some PRO_123
	 *  
	 * @param model
	 * @param i
	 * @param p
	 * @param filler
	 * @param flushReasoner
	 * @param metadata
	 */
	void addType(ModelContainer model,
			OWLIndividual i, 
			OWLObjectPropertyExpression p,
			OWLClassExpression filler,
			boolean flushReasoner,
			METADATA metadata) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Adding "+i+ " type "+p+" some "+filler);	
		}
		OWLDataFactory f = model.getOWLDataFactory();
		OWLObjectSomeValuesFrom c = f.getOWLObjectSomeValuesFrom(p, filler);
		OWLClassAssertionAxiom axiom = f.getOWLClassAssertionAxiom(c, i);
		addAxiom(model, axiom, flushReasoner, metadata);
	}
	
	/**
	 * remove ClassAssertion(c,i) to specified model
	 * 
	 * @param model
	 * @param i
	 * @param c
	 * @param metadata
	 */
	public void removeType(ModelContainer model, OWLNamedIndividual i, OWLClass c, METADATA metadata) {
		removeType(model, i, c, true, metadata);
	}

	/**
	 * remove ClassAssertion(c,i) from the model
	 * 
	 * @param model
	 * @param i
	 * @param ce
	 * @param flushReasoner
	 * @param metadata
	 */
	void removeType(ModelContainer model, OWLIndividual i, 
			OWLClassExpression ce, boolean flushReasoner, METADATA metadata) {
		Set<OWLClassAssertionAxiom> allAxioms = model.getAboxOntology().getClassAssertionAxioms(i);
		// use search to remove also axioms with annotations
		for (OWLClassAssertionAxiom ax : allAxioms) {
			if (ce.equals(ax.getClassExpression())) {
				removeAxiom(model, ax, flushReasoner, metadata);
			}
		}
		
	}
	
	/**
	 * Removes a ClassAssertion, where the class expression instantiated is an
	 * ObjectSomeValuesFrom expression
	 * 
	 * @param model
	 * @param i
	 * @param p
	 * @param filler
	 * @param metadata
	 */
	public void removeType(ModelContainer model,
			OWLNamedIndividual i, 
			OWLObjectPropertyExpression p,
			OWLClassExpression filler,
			METADATA metadata) {
		removeType(model, i, p, filler, true, metadata);
	}
	
	
	void removeType(ModelContainer model,
			OWLIndividual i, 
			OWLObjectPropertyExpression p,
			OWLClassExpression filler,
			boolean flushReasoner,
			METADATA metadata) {
		OWLDataFactory f = model.getOWLDataFactory();
		OWLClassAssertionAxiom axiom = f.getOWLClassAssertionAxiom(f.getOWLObjectSomeValuesFrom(p, filler), i);
		removeAxiom(model, axiom, flushReasoner, metadata);
	}

	/**
	 * Adds triple (i,p,j) to specified model
	 * 
	 * @param model
	 * @param p
	 * @param i
	 * @param j
	 * @param annotations
	 * @param metadata
	 */
	public void addFact(ModelContainer model, OWLObjectPropertyExpression p,
			OWLNamedIndividual i, OWLNamedIndividual j, Set<OWLAnnotation> annotations, METADATA metadata) {
		addFact(model, p, i, j, annotations, true, metadata);
	}
	
	public void addFact(ModelContainer model, OBOUpperVocabulary vocabElement,
			OWLNamedIndividual i, OWLNamedIndividual j, Set<OWLAnnotation> annotations, METADATA metadata) {
		OWLObjectProperty p = vocabElement.getObjectProperty(model.getAboxOntology());
		addFact(model, p, i, j, annotations, true, metadata);
	}

	void addFact(ModelContainer model, OWLObjectPropertyExpression p,
			OWLIndividual i, OWLIndividual j, Set<OWLAnnotation> annotations, boolean flushReasoner, METADATA metadata) {
		OWLObjectPropertyAssertionAxiom axiom = createFact(model.getOWLDataFactory(), p, i,	j, annotations);
		addAxiom(model, axiom, flushReasoner, metadata);
	}

	/**
	 * @param f
	 * @param p
	 * @param i
	 * @param j
	 * @param annotations
	 * @return axiom
	 */
	public static OWLObjectPropertyAssertionAxiom createFact(OWLDataFactory f,
			OWLObjectPropertyExpression p, OWLIndividual i, OWLIndividual j,
			Set<OWLAnnotation> annotations) {
		final OWLObjectPropertyAssertionAxiom axiom;
		if (annotations != null && !annotations.isEmpty()) {
			axiom = f.getOWLObjectPropertyAssertionAxiom(p, i, j, annotations);	
		}
		else {
			axiom = f.getOWLObjectPropertyAssertionAxiom(p, i, j);
		}
		return axiom;
	}

	public void removeFact(ModelContainer model, OWLObjectPropertyExpression p,
			OWLNamedIndividual i, OWLNamedIndividual j, METADATA metadata) {
		removeFact(model, p, i, j, true, metadata);
	}

	Set<IRI> removeFact(ModelContainer model, OWLObjectPropertyExpression p,
			OWLIndividual i, OWLIndividual j, boolean flushReasoner, METADATA metadata) {
		OWLDataFactory f = model.getOWLDataFactory();
		
		OWLOntology ont = model.getAboxOntology();
		OWLAxiom toRemove = null;
		Set<IRI> iriSet = new HashSet<IRI>();
		Set<OWLObjectPropertyAssertionAxiom> candidates = ont.getObjectPropertyAssertionAxioms(i);
		for (OWLObjectPropertyAssertionAxiom axiom : candidates) {
			if (p.equals(axiom.getProperty()) && j.equals(axiom.getObject())) {
				toRemove = axiom;
				extractIRIValues(axiom.getAnnotations(), iriSet);
				break;
			}
		}
		if (toRemove == null) {
			// fall back solution
			toRemove = f.getOWLObjectPropertyAssertionAxiom(p, i, j);
		}
		removeAxiom(model, toRemove, flushReasoner, metadata);
		return iriSet;
	}
	
	public void addAnnotations(ModelContainer model, OWLObjectPropertyExpression p,
			OWLNamedIndividual i, OWLNamedIndividual j, Set<OWLAnnotation> annotations, METADATA metadata) {
		addAnnotations(model, p, i, j, annotations, true, metadata);
	}
	
	void addAnnotations(ModelContainer model, OWLObjectPropertyExpression p,
			OWLNamedIndividual i, OWLNamedIndividual j, Set<OWLAnnotation> annotations,
			boolean flushReasoner, METADATA metadata) {
		OWLOntology ont = model.getAboxOntology();
		Set<OWLObjectPropertyAssertionAxiom> axioms = ont.getObjectPropertyAssertionAxioms(i);
		OWLObjectPropertyAssertionAxiom toModify = null;
		for (OWLObjectPropertyAssertionAxiom axiom : axioms) {
			if (p.equals(axiom.getProperty()) && j.equals(axiom.getObject())) {
				toModify = axiom;
				break;
			}
		}
		addAnnotations(model, toModify, annotations, flushReasoner, metadata);
	}
	
	void addAnnotations(ModelContainer model, OWLObjectPropertyAssertionAxiom toModify,
			Set<OWLAnnotation> annotations, boolean flushReasoner, METADATA metadata) {
		if (toModify != null) {
			Set<OWLAnnotation> combindedAnnotations = new HashSet<OWLAnnotation>(annotations);
			combindedAnnotations.addAll(toModify.getAnnotations());
			modifyAnnotations(toModify, combindedAnnotations, model, flushReasoner, metadata);
		}
	}
	
	void updateAnnotation(ModelContainer model, OWLObjectPropertyExpression p,
			OWLNamedIndividual i, OWLNamedIndividual j, OWLAnnotation update,
			boolean flushReasoner, METADATA metadata) {
		OWLOntology ont = model.getAboxOntology();
		Set<OWLObjectPropertyAssertionAxiom> axioms = ont.getObjectPropertyAssertionAxioms(i);
		OWLObjectPropertyAssertionAxiom toModify = null;
		for (OWLObjectPropertyAssertionAxiom axiom : axioms) {
			if (p.equals(axiom.getProperty()) && j.equals(axiom.getObject())) {
				toModify = axiom;
				break;
			}
		}
		updateAnnotation(model, toModify, update, flushReasoner, metadata);
	}
	
	OWLObjectPropertyAssertionAxiom updateAnnotation(ModelContainer model, 
			OWLObjectPropertyAssertionAxiom toModify, OWLAnnotation update,
			boolean flushReasoner, METADATA metadata) {
		OWLObjectPropertyAssertionAxiom newAxiom = null;
		if (toModify != null) {
			Set<OWLAnnotation> combindedAnnotations = new HashSet<OWLAnnotation>();
			OWLAnnotationProperty target = update.getProperty();
			for(OWLAnnotation existing : toModify.getAnnotations()) {
				if (target.equals(existing.getProperty()) == false) {
					combindedAnnotations.add(existing);
				}
			}
			combindedAnnotations.add(update);
			newAxiom = modifyAnnotations(toModify, combindedAnnotations, model, flushReasoner, metadata);
		}
		return newAxiom;
	}
	
	OWLObjectPropertyAssertionAxiom removeAnnotations(ModelContainer model, OWLObjectPropertyExpression p,
			OWLNamedIndividual i, OWLNamedIndividual j, Set<OWLAnnotation> annotations,
			boolean flushReasoner, METADATA metadata) {
		OWLOntology ont = model.getAboxOntology();
		Set<OWLObjectPropertyAssertionAxiom> axioms = ont.getObjectPropertyAssertionAxioms(i);
		OWLObjectPropertyAssertionAxiom toModify = null;
		for (OWLObjectPropertyAssertionAxiom axiom : axioms) {
			if (p.equals(axiom.getProperty()) && j.equals(axiom.getObject())) {
				toModify = axiom;
				break;
			}
		}
		OWLObjectPropertyAssertionAxiom newAxiom = null;
		if (toModify != null) {
			Set<OWLAnnotation> combindedAnnotations = new HashSet<OWLAnnotation>(toModify.getAnnotations());
			combindedAnnotations.removeAll(annotations);
			newAxiom = modifyAnnotations(toModify, combindedAnnotations, model, flushReasoner, metadata);
		}
		return newAxiom;
	}
	
	private OWLObjectPropertyAssertionAxiom modifyAnnotations(OWLObjectPropertyAssertionAxiom axiom, 
			Set<OWLAnnotation> replacement, 
			ModelContainer model, boolean flushReasoner, METADATA metadata) {
		OWLOntology ont = model.getAboxOntology();
		OWLDataFactory f = model.getOWLDataFactory();
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>(2);
		changes.add(new RemoveAxiom(ont, axiom));
		OWLObjectPropertyAssertionAxiom newAxiom = 
				f.getOWLObjectPropertyAssertionAxiom(axiom.getProperty(), axiom.getSubject(), axiom.getObject(), replacement);
		changes.add(new AddAxiom(ont, newAxiom));
		applyChanges(model, changes, flushReasoner, metadata);
		return newAxiom;
	}
	
	public void addAxiom(ModelContainer model, OWLAxiom axiom, 
			boolean flushReasoner, METADATA metadata) {
		OWLOntology ont = model.getAboxOntology();
		List<OWLOntologyChange> changes = Collections.<OWLOntologyChange>singletonList(new AddAxiom(ont, axiom));
		synchronized (ont) {
			/*
			 * all changes to the ontology are synchronized via the ontology object
			 */
			applyChanges(model, ont.getOWLOntologyManager(), changes, metadata);	
		}
		if (flushReasoner) {
			model.getReasoner().flush();
		}
	}
	
	void addAxioms(ModelContainer model, Set<? extends OWLAxiom> axioms, 
			boolean flushReasoner, METADATA metadata) {
		OWLOntology ont = model.getAboxOntology();
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>(axioms.size());
		for(OWLAxiom axiom : axioms) {
			changes.add(new AddAxiom(ont, axiom));
		}
		synchronized (ont) {
			/*
			 * all changes to the ontology are synchronized via the ontology object
			 */
			applyChanges(model, ont.getOWLOntologyManager(), changes, metadata);
		}
		if (flushReasoner) {
			model.getReasoner().flush();
		}
	}
	
	void removeAxiom(ModelContainer model, OWLAxiom axiom, 
			boolean flushReasoner, METADATA metadata) {
		OWLOntology ont = model.getAboxOntology();
		List<OWLOntologyChange> changes = Collections.<OWLOntologyChange>singletonList(new RemoveAxiom(ont, axiom));
		synchronized (ont) {
			/*
			 * all changes to the ontology are synchronized via the ontology object
			 */
			applyChanges(model, ont.getOWLOntologyManager(), changes, metadata);
		}
		if (flushReasoner) {
			model.getReasoner().flush();
		}
	}

	void removeAxioms(String modelId, Set<OWLAxiom> axioms, boolean flushReasoner, METADATA metadata) {
		ModelContainer model = getModel(modelId);
		removeAxioms(model, axioms, flushReasoner, metadata);
	}
	
	void removeAxioms(ModelContainer model, Set<OWLAxiom> axioms, 
			boolean flushReasoner, METADATA metadata) {
		OWLOntology ont = model.getAboxOntology();
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>(axioms.size());
		for(OWLAxiom axiom : axioms) {
			changes.add(new RemoveAxiom(ont, axiom));
		}
		synchronized (ont) {
			/*
			 * all changes to the ontology are synchronized via the ontology object
			 */
			applyChanges(model, ont.getOWLOntologyManager(), changes, metadata);
		}
		if (flushReasoner) {
			model.getReasoner().flush();
		}
	}

	private void applyChanges(ModelContainer model, 
			List<OWLOntologyChange> changes, boolean flushReasoner, METADATA metadata) {
		OWLOntology ont = model.getAboxOntology();
		synchronized (ont) {
			/*
			 * all changes to the ontology are synchronized via the ontology object
			 */
			applyChanges(model, ont.getOWLOntologyManager(), changes, metadata);
		}
		if (flushReasoner) {
			model.getReasoner().flush();
		}
	}
	
	private void applyChanges(ModelContainer model, OWLOntologyManager m, 
			List<? extends OWLOntologyChange> changes, METADATA metadata) {
		List<OWLOntologyChange> appliedChanges = m.applyChanges(changes);
		addToHistory(model, appliedChanges, metadata);
	}
	
	/**
	 * Hook for implementing an undo and redo.
	 * 
	 * @param model
	 * @param appliedChanges
	 * @param metadata
	 */
	protected void addToHistory(ModelContainer model, 
			List<OWLOntologyChange> appliedChanges, METADATA metadata) {
		// do nothing, for now
	}
	
	protected OWLOntology loadOntologyIRI(final IRI sourceIRI, boolean minimal) throws OWLOntologyCreationException {
		// silence the OBO parser in the OWL-API
		java.util.logging.Logger.getLogger("org.obolibrary").setLevel(java.util.logging.Level.SEVERE);
		
		// load model from source
		OWLOntologyDocumentSource source = new IRIDocumentSource(sourceIRI);
		if (minimal == false) {
			// add the obsolete imports to the ignored imports
			OWLOntology abox = graph.getManager().loadOntologyFromOntologyDocument(source);
			return abox;
		}
		else {
			// only load the model, skip imports
			// approach: return an empty ontology IRI for any IRI mapping request using.
			final OWLOntologyManager m = OWLManager.createOWLOntologyManager();
			final Set<IRI> emptyOntologies = new HashSet<IRI>();
			m.addIRIMapper(new OWLOntologyIRIMapper() {
				
				@Override
				public IRI getDocumentIRI(IRI ontologyIRI) {
					
					// quick check:
					// do nothing for the original IRI and known empty ontologies
					if (sourceIRI.equals(ontologyIRI) || emptyOntologies.contains(ontologyIRI)) {
						return null;
					}
					emptyOntologies.add(ontologyIRI);
					try {
						OWLOntology emptyOntology = m.createOntology(ontologyIRI);
						return emptyOntology.getOntologyID().getDefaultDocumentIRI();
					} catch (OWLOntologyCreationException e) {
						throw new RuntimeException(e);
					}
				}
			});
			OWLOntology minimalAbox = m.loadOntologyFromOntologyDocument(source);
			return minimalAbox;
		}
	}
	
	/**
	 * This method will check the given model and update the import declarations.
	 * It will add missing IRIs and remove obsolete ones.
	 * 
	 * @param model
	 * @see #additionalImports
	 * @see #addImports(Iterable)
	 */
	public void updateImports(ModelContainer model) {
		updateImports(model.getAboxOntology(), tboxIRI, additionalImports);
	}
	
	static void updateImports(final OWLOntology aboxOntology, IRI tboxIRI, Set<IRI> additionalImports) {
		List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
		
		Set<IRI> missingImports = new HashSet<IRI>();
		missingImports.add(tboxIRI);
		missingImports.addAll(additionalImports);
		Set<OWLImportsDeclaration> importsDeclarations = aboxOntology.getImportsDeclarations();
		for (OWLImportsDeclaration decl : importsDeclarations) {
			IRI iri = decl.getIRI();
			missingImports.remove(iri);
		}
		final OWLOntologyManager m = aboxOntology.getOWLOntologyManager();
		if (!missingImports.isEmpty()) {
			OWLDataFactory f = m.getOWLDataFactory();
			for(IRI missingImport : missingImports) {
				OWLImportsDeclaration decl = f.getOWLImportsDeclaration(missingImport);
				changes.add(new AddImport(aboxOntology, decl));
			}
		}
		
		if (!changes.isEmpty()) {
			m.applyChanges(changes);
		}
	}
	
}
