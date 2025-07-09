package org.geneontology.minerva;

import com.google.common.base.Optional;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.ChangeApplied;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ModelContainer {

    private static Logger LOG = Logger.getLogger(ModelContainer.class);

    private final IRI modelId;
    private OWLOntology aboxOntology = null;
    private boolean aboxModified = false;
    private OWLOntology tboxOntology = null;
    //private OWLReasoner tboxReasoner = null;

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
     * <p>
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
     *
     * @throws OWLOntologyCreationException
     */
    private void init() throws OWLOntologyCreationException {
        // abox -> tbox
        if (aboxOntology == null) {
            LOG.debug("Creating abox ontology. mgr = " + getOWLOntologyManager());
            Optional<IRI> tBoxIRI = tboxOntology.getOntologyID().getOntologyIRI();
            if (tBoxIRI.isPresent()) {
                IRI ontologyIRI = IRI.create(tBoxIRI.get() + "__abox");
                aboxOntology = getOWLOntologyManager().getOntology(ontologyIRI);
                if (aboxOntology != null) {
                    LOG.warn("Clearing existing abox ontology");
                    getOWLOntologyManager().removeOntology(aboxOntology);
                }
                aboxOntology = getOWLOntologyManager().createOntology(ontologyIRI);
                AddImport ai = new AddImport(aboxOntology,
                        getOWLDataFactory().getOWLImportsDeclaration(tBoxIRI.get()));
                getOWLOntologyManager().applyChange(ai);
            } else {
                aboxOntology = getOWLOntologyManager().createOntology();
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(modelId + " manager(T) = " + tboxOntology.getOWLOntologyManager());
            LOG.debug(modelId + " manager(A) = " + aboxOntology.getOWLOntologyManager());
            LOG.debug(modelId + " id(T) = " + tboxOntology.getOntologyID());
            LOG.debug(modelId + " id(A) = " + aboxOntology.getOntologyID());
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

        for (ModelChangeListener listener : listeners) {
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

    /**
     * @param changes the changes to apply
     * @return the changes that had an effect when applied
     */
    public List<OWLOntologyChange> applyChanges(List<? extends OWLOntologyChange> changes) {
        // Some changes will add an axiom already in the ontology; these are no-ops and should
        // not be added to the Undo stack
        List<OWLOntologyChange> effectfulChanges = new ArrayList<>();
        for (OWLOntologyChange change : changes) {
            ChangeApplied applied = getOWLOntologyManager().applyChange(change);
            if (applied == ChangeApplied.SUCCESSFULLY) effectfulChanges.add(change);
        }
        List<OWLOntologyChange> relevantChanges = new ArrayList<>();
        for (OWLOntologyChange change : effectfulChanges) {
            if (aboxOntology.equals(change.getOntology())) {
                aboxModified = true;
                relevantChanges.add(change);
            }
        }
        if (!relevantChanges.isEmpty()) {
            for (ModelChangeListener listener : listeners) {
                listener.handleChange(relevantChanges);
            }
        }
        return new ArrayList<>(effectfulChanges);
    }

    public boolean isModified() {
        return aboxModified;
    }

    void setAboxModified(boolean modified) {
        aboxModified = modified;
    }
}
