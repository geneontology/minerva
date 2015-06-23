package org.geneontology.minerva;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyChangeListener;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

public class ModelContainer {

	private static Logger LOG = Logger.getLogger(ModelContainer.class);
	
	private OWLReasonerFactory reasonerFactory = null;
	
	private volatile OWLReasoner reasoner = null;
	private final Object reasonerMutex = new Object();
	
	private volatile OWLReasoner moduleReasoner = null;
	private volatile OWLOntologyChangeListener moduleListener = null;
	private final Object moduleReasonerMutex = new Object();
	
	private final String modelId;
	private OWLOntology aboxOntology = null;
	private OWLOntology tboxOntology = null;
	private OWLOntology queryOntology = null;
	private Map<OWLClass,OWLClassExpression> queryClassMap = null;
	Map<OWLOntology,Set<OWLAxiom>> collectedAxioms = new HashMap<OWLOntology,Set<OWLAxiom>>();
	
	/**
	 * The container is seeded with a tbox (i.e. ontology). An abox will be created
	 * automatically.
	 * 
	 * A default reasoner factory will be selected (Elk)
	 * @param modelId 
	 * @param tbox
	 * @throws OWLOntologyCreationException
	 */
	public ModelContainer(String modelId, OWLOntology tbox) throws OWLOntologyCreationException {
		tboxOntology = tbox;
		this.modelId = modelId;
		reasonerFactory = new ElkReasonerFactory();
		init();
	}
	/**
	 * Creates a container with a pre-defined tbox (ontology) and abox (instance store).
	 * Note the abox should import the tbox (directly or indirectly).
	 * 
	 * The abox may be identical to the tbox, in which case individuals are added to
	 * the same ontology
	 * 
	 * @param modelId 
	 * @param tbox
	 * @param abox
	 * @throws OWLOntologyCreationException
	 */
	public ModelContainer(String modelId, OWLOntology tbox, OWLOntology abox) throws OWLOntologyCreationException {
		tboxOntology = tbox;
		aboxOntology = abox;
		this.modelId = modelId;
		reasonerFactory = new ElkReasonerFactory();
		init();
	}

	/**
	 * The container is seeded with a tbox (i.e. ontology). An abox will be created
	 * automatically.
	 * 
	 * @param modelId 
	 * @param tbox
	 * @param reasonerFactory
	 * @throws OWLOntologyCreationException
	 */
	public ModelContainer(String modelId, OWLOntology tbox,
			OWLReasonerFactory reasonerFactory) throws OWLOntologyCreationException {
		tboxOntology = tbox;
		this.modelId = modelId;
		this.reasonerFactory = reasonerFactory;
		init();
	}
	
	/**
	 * Creates a container with a pre-defined tbox (ontology) and abox (instance store).
	 * Note the abox should import the tbox (directly or indirectly).
	 * 
	 * The abox may be identical to the tbox, in which case individuals are added to
	 * the same ontology
	 * 
	 * @param modelId
	 * @param tbox
	 * @param abox
	 * @param rf
	 * @throws OWLOntologyCreationException
	 */
	public ModelContainer(String modelId, OWLOntology tbox, OWLOntology abox,
			OWLReasonerFactory rf) throws OWLOntologyCreationException {
		tboxOntology = tbox;
		aboxOntology = abox;
		this.modelId = modelId;
		reasonerFactory = rf;
		init();
	}
	
	/**
	 * Initialization consists of:
	 * 
	 * <ul>
	 * <li>Setting aboxOntology, if not set - defaults to a new ontology using tbox.IRI as base. 
	 *   Adds import to tbox.
	 * <li>Setting queryOntology, if not set. Adds abox imports queryOntology declaration
	 * </ul>
	 * 
	 * @throws OWLOntologyCreationException
	 */
	private void init() throws OWLOntologyCreationException {
		// reasoner -> query -> abox -> tbox
		if (aboxOntology == null) {
			LOG.debug("Creating abox ontology. mgr = "+getOWLOntologyManager());
			IRI ontologyIRI = IRI.create(tboxOntology.getOntologyID().getOntologyIRI()+"__abox");
			aboxOntology = getOWLOntologyManager().getOntology(ontologyIRI);
			if (aboxOntology != null) {
				LOG.warn("Clearing existing abox ontology");
				getOWLOntologyManager().removeOntology(aboxOntology);
			}
			aboxOntology = getOWLOntologyManager().createOntology(ontologyIRI);
			AddImport ai = new AddImport(aboxOntology, 
					getOWLDataFactory().getOWLImportsDeclaration(tboxOntology.getOntologyID().getOntologyIRI()));
			getOWLOntologyManager().applyChange(ai);
		}
		if (queryOntology == null) {
			// Imports: {q imports a, a imports t}
			LOG.debug("Creating query ontology");

			IRI ontologyIRI = IRI.create(tboxOntology.getOntologyID().getOntologyIRI()+"__query"); 
			queryOntology = getOWLOntologyManager().getOntology(ontologyIRI);
			if (queryOntology == null) {
				queryOntology = getOWLOntologyManager().createOntology(ontologyIRI);
			}
			AddImport ai = new AddImport(queryOntology, 
					getOWLDataFactory().getOWLImportsDeclaration(aboxOntology.getOntologyID().getOntologyIRI()));
			getOWLOntologyManager().applyChange(ai);
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug(modelId+" manager(T) = "+tboxOntology.getOWLOntologyManager());
			LOG.debug(modelId+" manager(A) = "+aboxOntology.getOWLOntologyManager());
			LOG.debug(modelId+" manager(Q) = "+queryOntology.getOWLOntologyManager());
			LOG.debug(modelId+" id(T) = "+tboxOntology.getOntologyID().getOntologyIRI());
			LOG.debug(modelId+" id(A) = "+aboxOntology.getOntologyID().getOntologyIRI());
			LOG.debug(modelId+" id(Q) = "+queryOntology.getOntologyID().getOntologyIRI());
		}
	}

	public String getModelId() {
		return modelId;
	}

	/**
	 * @return ontology manager for tbox
	 */
	public OWLOntologyManager getOWLOntologyManager() {
		return tboxOntology.getOWLOntologyManager();
	}
	
	/**
	 * @return data factory for tbox
	 */
	public OWLDataFactory getOWLDataFactory() {
		return getOWLOntologyManager().getOWLDataFactory();
	}
	
	/**
	 * Release the reasoner
	 * 
	 */
	public void disposeReasoner() {
		synchronized (reasonerMutex) {
			if (reasoner != null) {
				reasoner.dispose();
				reasoner = null;
			}
		}
	}
	
	public void disposeModuleReasoner() {
		synchronized (moduleReasonerMutex) {
			_internalDisposeModuleReasonerAndListener();
		}
	}
	
	/**
	 * Only call within a {@link #moduleReasonerMutex} synchronized block!!
	 */
	private void _internalDisposeModuleReasonerAndListener() {
		if (moduleReasoner != null) {
			moduleReasoner.dispose();
			moduleReasoner = null;
		}
		if (moduleListener != null) {
			aboxOntology.getOWLOntologyManager().removeOntologyChangeListener(moduleListener);
			moduleListener = null;
		}
	}

	public void dispose() {
		disposeReasoner();
		disposeModuleReasoner();
		final OWLOntologyManager m = getOWLOntologyManager();
		if (queryOntology != null) {
			m.removeOntology(queryOntology);
		}
		if (aboxOntology != null) {
			m.removeOntology(aboxOntology);
		}
	}
	
	/**
	 * The reasoner factory is used during initialization to
	 * generate a reasoner object using abox as ontology
	 * 
	 * @param reasonerFactory
	 */
	public void setReasonerFactory(OWLReasonerFactory reasonerFactory) {
		this.reasonerFactory = reasonerFactory;
	}

	/**
	 * @return current reasoner, operating over abox
	 */
	public OWLReasoner getReasoner() {
		synchronized (reasonerMutex) {
			if (reasoner == null) {
				// reasoner -> query -> abox -> tbox
				if (LOG.isDebugEnabled()) {
					LOG.debug("Creating reasoner on "+queryOntology+" ImportsClosure="+
						queryOntology.getImportsClosure());
				}
				reasoner = reasonerFactory.createReasoner(queryOntology);
			}
		}
		return reasoner;
	}
	/**
	 * @param reasoner
	 */
	public void setReasoner(OWLReasoner reasoner) {
		this.reasoner = reasoner;
	}
	
	public OWLReasoner getModuleReasoner() throws OWLOntologyCreationException {
		synchronized (moduleReasonerMutex) {
			if (moduleReasoner == null) {
				moduleReasoner = createModuleReasoner();
			}
			if (moduleListener == null) {
				moduleListener = createModuleChangeListener();
				aboxOntology.getOWLOntologyManager().addOntologyChangeListener(moduleListener);
			}
		}
		return moduleReasoner;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private OWLReasoner createModuleReasoner() throws OWLOntologyCreationException {
		LOG.info("Creating module reasoner for module: "+modelId);
		ModuleType mtype = ModuleType.BOT;
		OWLOntologyManager m = OWLManager.createOWLOntologyManager(aboxOntology.getOWLOntologyManager().getOWLDataFactory());
		SyntacticLocalityModuleExtractor sme = new SyntacticLocalityModuleExtractor(m, aboxOntology, mtype);
		Set<OWLEntity> seeds = (Set) aboxOntology.getIndividualsInSignature();
		OWLOntology module = sme.extractAsOntology(seeds, IRI.generateDocumentIRI());
		OWLReasoner reasoner = reasonerFactory.createReasoner(module);
		LOG.info("Done creating module reasoner module: "+modelId);
		return reasoner;
	}
	
	private OWLOntologyChangeListener createModuleChangeListener() {
		return new OWLOntologyChangeListener() {
			
			@Override
			public void ontologiesChanged(List<? extends OWLOntologyChange> changes)
					throws OWLException {
				if (moduleReasoner != null) { // warning this only works because of the volatile keyword
					for (OWLOntologyChange change : changes) {
						boolean dispose = false;
						if (aboxOntology.equals(change.getOntology())) {
							dispose = change.isAxiomChange() || change.isImportChange();
						}
						if (dispose) {
							synchronized (moduleReasonerMutex) {
								_internalDisposeModuleReasonerAndListener();
								LOG.info("Disposing module reasoner due to ontology change for model: "+modelId);
							}
							break;
						}
					}
				}
			}
		};
	}
	
	/**
	 * The tbox ontology should contain class axioms used to generate minimal models in the
	 * abox ontology.
	 * 
	 * May be the same as abox, in which case generated abox axioms go in the same ontology 
	 * 
	 * @return tbox
	 */
	public OWLOntology getTboxOntology() {
		return tboxOntology;
	}
	/**
	 * @param tboxOntology
	 */
	public void setTboxOntology(OWLOntology tboxOntology) {
		this.tboxOntology = tboxOntology;
	}
	/**
	 * Note: ABox ontology should import TBox ontology
	 * @return abox
	 */
	public OWLOntology getAboxOntology() {
		return aboxOntology;
	}

	/**
	 * Note: ABox ontology should import TBox ontology
	 * @param aboxOntology
	 */
	public void setAboxOntology(OWLOntology aboxOntology) {
		this.aboxOntology = aboxOntology;
	}
	/**
	 * You should not need to use this directly - exposed for debugging
	 * 
	 * @return auxiliary ontology to support queries
	 */
	public OWLOntology getQueryOntology() {
		return queryOntology;
	}
	
	public boolean isQueryClass(OWLClass c) {
		if (queryClassMap != null && queryClassMap.containsKey(c)) {
			return true;
		}
		return false;
	}
	
	public Set<OWLClass> getQueryClasses() {
		if (queryClassMap != null) {
			return queryClassMap.keySet();
		}
		return Collections.emptySet();
	}
	
	public void addAxiom(OWLAxiom ax) {
		addAxiom(ax, aboxOntology);
	}
	public void addAxiom(OWLAxiom ax, OWLOntology ont) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Adding: "+ax+" to "+ont);
		}
		getOWLOntologyManager().addAxiom(ont, ax);
	}
	public void addAxioms(Set<? extends OWLAxiom> axs) {
		addAxioms(axs, aboxOntology);
	}
	public void addAxioms(Set<? extends OWLAxiom> axs, OWLOntology ont) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Adding: "+axs+" to "+ont);
		}
		getOWLOntologyManager().addAxioms(ont, axs);
	}
	
	public void applyChanges(List<OWLOntologyChange> changeIRI) {
		getOWLOntologyManager().applyChanges(changeIRI);
	}
	
	public synchronized Map<OWLClass,OWLClassExpression> getQueryClassMap(boolean precomputePropertyClassCombinations) {
		if (queryClassMap == null) {
			generateQueryOntology(precomputePropertyClassCombinations);
		}
		return queryClassMap;
	}
	
	/**
	 * <b>Motivation</b>: OWL reasoners do not return superclass expressions
	 * If we want to find all class expressions that may hold for a class
	 * then we must pre-coordinate
	 * all possible expressions within the subset of OWL we care about.
	 * <br/>
	 * This class generates all satisfiable class expressions of the form
	 * r some c (for the cross-product of R x C), as well as all
	 * class expressions that have been used (which may include nested expressions)
	 * 
	 * The results are stored in queryClassMap
	 * 
	 * @param precomputePropertyClassCombinations 
	 */
	private void generateQueryOntology(boolean precomputePropertyClassCombinations) {
		queryClassMap = new HashMap<OWLClass,OWLClassExpression>(); 

		getReasoner().flush();

		if (precomputePropertyClassCombinations) {
			LOG.debug("Precomputing all OP x Class combos");
			// cross-product of P x C
			// TODO - reflexivity and local reflexivity?
			for (OWLObjectProperty p : tboxOntology.getObjectPropertiesInSignature(true)) {
				LOG.debug(" materializing P some C for P=:"+p);
				for (OWLClass c : tboxOntology.getClassesInSignature(true)) {
					OWLObjectSomeValuesFrom r = getOWLDataFactory().getOWLObjectSomeValuesFrom(p, c);
					//LOG.debug(" QMAP:"+r);
					addClassExpressionToQueryMap(r);
				}
			}
		}

		// all expressions used in ontology
		for (OWLOntology ont : tboxOntology.getImportsClosure()) {
			LOG.debug("Finding all nested anonymous expressions");
			for (OWLAxiom ax : ont.getAxioms()) {
				// TODO - check if this is the nest closure. ie (r some (r2 some (r3 some ...))) 
				for (OWLClassExpression x : ax.getNestedClassExpressions()) {
					if (x.isAnonymous()) {
						//LOG.debug(" QMAP+:"+x);
						addClassExpressionToQueryMap(x);
					}
				}
			}
		}
		if (LOG.isDebugEnabled()) {
			for (OWLOntology ont : collectedAxioms.keySet()) {
				LOG.debug("TOTAL axioms in QMAP: "+collectedAxioms.get(ont).size());
			}
		}
		LOG.debug("Adding collected axioms");
		addCollectedAxioms();
		LOG.debug("Flushing reasoner...");
		reasoner.flush();
		LOG.debug("Flushed reasoner");
	}
	
	/**
	 * Note that this collects axioms but does not change the ontology. Call {@link #addCollectedAxioms()} to add these
	 * 
	 * @param x
	 */
	private void addClassExpressionToQueryMap(OWLClassExpression x) {
		if (!(x instanceof OWLObjectSomeValuesFrom)) {
			// in future we may support a wider variety of expressions - e.g. cardinality
			return;
		}
		// this makes things too slow
		//if (!reasoner.isSatisfiable(x)) {
		//	LOG.debug("Not adding unsatisfiable query expression:" +x);
		//	return;
		//}
		IRI nxIRI = getSkolemIRI(x.getSignature());
		OWLClass nx = getOWLDataFactory().getOWLClass(nxIRI);
		OWLAxiom ax = getOWLDataFactory().getOWLEquivalentClassesAxiom(nx, x);
		collectAxiom(ax, queryOntology);
		queryClassMap.put(nx, x);
	}

	private IRI getSkolemIRI(Set<OWLEntity> objs) {
		// TODO Auto-generated method stub
		IRI iri;
		StringBuffer sb = new StringBuffer();
		for (OWLEntity obj : objs) {
			sb.append("/"+getFragmentID(obj));
		}
		iri = IRI.create("http://x.org"+sb.toString());
		return iri;
	}
	
	private String getFragmentID(OWLObject obj) {
		if (obj instanceof OWLNamedObject) {
			return ((OWLNamedObject) obj).getIRI().toString().replaceAll(".*/", "");
		}
		return UUID.randomUUID().toString();
	}

	
	/**
	 * Collects an axiom to be added to ont at some later time.
	 * Cal {@link #addCollectedAxioms()} to add these
	 * 
	 * @param ax
	 * @param ont
	 */
	private void collectAxiom(OWLAxiom ax, OWLOntology ont) {
		//LOG.debug("Collecting: "+ax+" to "+ont);
		if (!collectedAxioms.containsKey(ont))
			collectedAxioms.put(ont, new HashSet<OWLAxiom>());
		collectedAxioms.get(ont).add(ax);
	}

	/**
	 * Adds all collected axioms to their specified destination 
	 */
	private void addCollectedAxioms() {
		for (OWLOntology ont : collectedAxioms.keySet())
			addCollectedAxioms(ont);
	}

	/**
	 * Adds all collected axioms to ont
	 * @param ont
	 */
	private void addCollectedAxioms(OWLOntology ont) {
		if (collectedAxioms.containsKey(ont)) {
			getOWLOntologyManager().addAxioms(ont, collectedAxioms.get(ont));
			collectedAxioms.remove(ont);
		}

	}
}
