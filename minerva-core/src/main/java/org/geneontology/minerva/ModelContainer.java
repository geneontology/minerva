package org.geneontology.minerva;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.ChangeApplied;

import com.google.common.base.Optional;

public class ModelContainer {

	private static Logger LOG = Logger.getLogger(ModelContainer.class);

	private final IRI modelId;
	private OWLOntology aboxOntology = null;
	private boolean aboxModified = false;
	private OWLOntology tboxOntology = null;
	
	private final List<ModelChangeListener> listeners = new CopyOnWriteArrayList<>();

	/**
	 * The container is seeded with a tbox (i.e. ontology). An abox will be created
	 * automatically.
	 * 
	 * @param modelId 
	 * @param tbox
	 * @throws OWLOntologyCreationException
	 */
	public ModelContainer(IRI modelId, OWLOntology tbox) throws OWLOntologyCreationException {
		tboxOntology = tbox;
		this.modelId = modelId;
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
	public ModelContainer(IRI modelId, OWLOntology tbox, OWLOntology abox) throws OWLOntologyCreationException {
		tboxOntology = tbox;
		aboxOntology = abox;
		this.modelId = modelId;
		init();
	}

	/**
	 * Initialization consists of setting aboxOntology, if not set - defaults to a new ontology using tbox.
	 * @throws OWLOntologyCreationException
	 */
	private void init() throws OWLOntologyCreationException {
		// abox -> tbox
		if (aboxOntology == null) {
			LOG.debug("Creating abox ontology. mgr = "+getOWLOntologyManager());
			Optional<IRI> tBoxIRI = tboxOntology.getOntologyID().getOntologyIRI();
			if (tBoxIRI.isPresent()) {
				IRI ontologyIRI = IRI.create(tBoxIRI.get()+"__abox");
				aboxOntology = getOWLOntologyManager().getOntology(ontologyIRI);
				if (aboxOntology != null) {
					LOG.warn("Clearing existing abox ontology");
					getOWLOntologyManager().removeOntology(aboxOntology);
				}
				aboxOntology = getOWLOntologyManager().createOntology(ontologyIRI);
				AddImport ai = new AddImport(aboxOntology, 
						getOWLDataFactory().getOWLImportsDeclaration(tBoxIRI.get()));
				getOWLOntologyManager().applyChange(ai);
			}
			else {
				aboxOntology = getOWLOntologyManager().createOntology();
			}
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug(modelId+" manager(T) = "+tboxOntology.getOWLOntologyManager());
			LOG.debug(modelId+" manager(A) = "+aboxOntology.getOWLOntologyManager());
			LOG.debug(modelId+" id(T) = "+tboxOntology.getOntologyID());
			LOG.debug(modelId+" id(A) = "+aboxOntology.getOntologyID());
		}
	}

	public IRI getModelId() {
		return modelId;
	}

	public OWLOntologyManager getOWLOntologyManager() {
		return aboxOntology.getOWLOntologyManager();
	}

	public OWLDataFactory getOWLDataFactory() {
		return getOWLOntologyManager().getOWLDataFactory();
	}

	public void dispose() {
		final OWLOntologyManager m = getOWLOntologyManager();
		if (aboxOntology != null) {
			m.removeOntology(aboxOntology);
		}
		
		for(ModelChangeListener listener : listeners) {
			listener.dispose();
		}
		listeners.clear();
	}

	public OWLOntology getTboxOntology() {
		return tboxOntology;
	}

	public OWLOntology getAboxOntology() {
		return aboxOntology;
	}

	public static interface ModelChangeListener {
		
		public void handleChange(List<OWLOntologyChange> changes);
		
		public void dispose();
	}
	
	public void registerListener(ModelChangeListener listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}
	
	public void unRegisterListener(ModelChangeListener listener) {
		if (listener != null) {
			listeners.remove(listener);
		}
	}
	
	public List<OWLOntologyChange> applyChanges(List<? extends OWLOntologyChange> changes) {
		ChangeApplied applied = getOWLOntologyManager().applyChanges(changes);
		if (applied == ChangeApplied.SUCCESSFULLY) {
			List<OWLOntologyChange> relevantChanges = new ArrayList<>();
			for (OWLOntologyChange change : changes) {
				if (aboxOntology.equals(change.getOntology())) {
					aboxModified = true;
					relevantChanges.add(change);
				}
			}
			if (relevantChanges.isEmpty() == false) {
				for(ModelChangeListener listener : listeners) {
					listener.handleChange(relevantChanges);
				}
			}
		}
		return new ArrayList<OWLOntologyChange>(changes);
	}

	public boolean isModified() {
		return aboxModified;
	}

	void setAboxModified(boolean modified) {
		aboxModified = modified;
	}
}
