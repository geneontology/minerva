package org.geneontology.minerva;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.rdf4j.common.transaction.IsolationLevels;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.query.parser.QueryPrologLexer;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.*;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.eclipse.rdf4j.sail.NotifyingSail;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.nativerdf.NativeStore;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.geneontology.minerva.util.ReverseChangeGenerator;
import org.geneontology.minerva.util.SailMutationCounter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.*;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.rio.RioMemoryTripleSource;
import org.semanticweb.owlapi.rio.RioRenderer;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.geneontology.minerva.BlazegraphOntologyManager.in_taxon;

public class BlazegraphMolecularModelManager<METADATA> extends CoreMolecularModelManager<METADATA> {

    private static Logger LOG = Logger
            .getLogger(BlazegraphMolecularModelManager.class);

    boolean isPrecomputePropertyClassCombinations = false;

    final String pathToOWLStore;
    final String pathToExportFolder;
    private final SailRepository repo;
    private final CurieHandler curieHandler;

    private final String modelIdPrefix;

    OWLDocumentFormat ontologyFormat = new TurtleDocumentFormat();

    private final List<PreFileSaveHandler> preFileSaveHandlers = new ArrayList<PreFileSaveHandler>();
    private final List<PostLoadOntologyFilter> postLoadOntologyFilters = new ArrayList<PostLoadOntologyFilter>();


    /**
     * @param tbox
     * @param modelIdPrefix
     * @param pathToJournal Path to Blazegraph journal file to use.
     *                      Only one instance of Blazegraph can use this file at a time.
     * @throws OWLOntologyCreationException
     * @throws IOException
     */
    public BlazegraphMolecularModelManager(OWLOntology tbox, CurieHandler curieHandler, String modelIdPrefix, @Nonnull String pathToJournal, String pathToExportFolder, String pathToOntologyJournal, boolean downloadOntologyJournal)
            throws OWLOntologyCreationException, IOException {
        super(tbox, pathToOntologyJournal, downloadOntologyJournal);
        if (curieHandler == null) {
            LOG.error("curie handler required for blazegraph model manager startup ");
            System.exit(-1);
        } else if (curieHandler.getMappings() == null) {
            LOG.error("curie handler WITH MAPPINGS required for blazegraph model manager startup ");
            System.exit(-1);
        }
        this.modelIdPrefix = modelIdPrefix;
        this.curieHandler = curieHandler;
        this.pathToOWLStore = pathToJournal;
        this.pathToExportFolder = pathToExportFolder;
        this.repo = initializeRepository(this.pathToOWLStore);
    }

    /**
     * Note this may move to an implementation-specific subclass in future
     *
     * @return path to owl on server
     */
    public String getPathToOWLStore() {
        return pathToOWLStore;
    }

    /**
     * @return the curieHandler
     */
    public CurieHandler getCuriHandler() {
        return curieHandler;
    }

    private SailRepository initializeRepository(String pathToJournal) {
        String indexes = "spoc,posc,cosp"; //FIXME review for appropriate indexes
        try {
            SailRepository repository = new SailRepository(new NativeStore(new File(pathToJournal), indexes));
            return repository;
        } catch (RepositoryException e) {
            LOG.fatal("Could not create Blazegraph sail", e);
            return null;
        }
    }

    /**
     * Generates a blank model
     *
     * @param metadata
     * @return modelId
     * @throws OWLOntologyCreationException
     */
    public ModelContainer generateBlankModel(METADATA metadata)
            throws OWLOntologyCreationException {
        // Create an arbitrary unique ID and add it to the system.
        IRI modelId = generateId(modelIdPrefix);
        if (modelMap.containsKey(modelId)) {
            throw new OWLOntologyCreationException(
                    "A model already exists for this db: " + modelId);
        }
        LOG.info("Generating blank model for new modelId: " + modelId);

        // create empty ontology, use model id as ontology IRI
        final OWLOntologyManager m = tbox.getOWLOntologyManager();
        OWLOntology abox = null;
        ModelContainer model = null;
        try {
            abox = m.createOntology(modelId);
            // generate model
            model = new ModelContainer(modelId, tbox, abox);
        } catch (OWLOntologyCreationException exception) {
            if (abox != null) {
                m.removeOntology(abox);
            }
            throw exception;
        }
        // add to internal map
        modelMap.put(modelId, model);
        return model;
    }

    /**
     * Save all models to disk.
     *
     * @throws OWLOntologyStorageException
     * @throws OWLOntologyCreationException
     * @throws IOException
     * @throws RepositoryException
     * @throws UnknownIdentifierException
     */
    public void saveAllModels()
            throws OWLOntologyStorageException, OWLOntologyCreationException,
            IOException, RepositoryException, UnknownIdentifierException {
        for (Entry<IRI, ModelContainer> entry : modelMap.entrySet()) {
            saveModel(entry.getValue());
        }
    }

    /**
     * Save a model to the database.
     *
     * @param m
     * @throws OWLOntologyStorageException
     * @throws OWLOntologyCreationException
     * @throws IOException
     * @throws RepositoryException
     * @throws UnknownIdentifierException
     */
    public void saveModel(ModelContainer m) throws IOException, RepositoryException, UnknownIdentifierException {
        IRI modelId = m.getModelId();
        final OWLOntology ont = m.getAboxOntology();
        final OWLOntologyManager manager = ont.getOWLOntologyManager();
        synchronized (ont) {
            List<OWLOntologyChange> changes = preSaveFileHandler(ont);
            try {
                this.writeModelToDatabase(ont, modelId);
                // reset modified flag for abox after successful save
                m.setAboxModified(false);
                // dump stored model to export file
                if (this.pathToExportFolder != null) {
                    File folder = new File(this.pathToExportFolder);
                    dumpStoredModel(modelId, folder);
                }
            } finally {
                if (changes != null) {
                    List<OWLOntologyChange> invertedChanges = ReverseChangeGenerator
                            .invertChanges(changes);
                    if (invertedChanges != null && !invertedChanges.isEmpty()) {
                        manager.applyChanges(invertedChanges);
                    }
                }
            }
        }
    }

    private void writeModelToDatabase(OWLOntology model, IRI modelId) throws RepositoryException, IOException {
        final SailRepositoryConnection connection = repo.getConnection();
        try {
            connection.begin(IsolationLevels.READ_COMMITTED);
            try {
                org.eclipse.rdf4j.model.IRI graph = Values.iri(modelId.toString());
                connection.clear(graph);
                StatementCollector collector = new StatementCollector();
                RioRenderer renderer = new RioRenderer(model, collector, null);
                renderer.render();
                connection.add(collector.getStatements(), graph);
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } finally {
            connection.close();
        }
    }

    private List<OWLOntologyChange> preSaveFileHandler(OWLOntology model) throws UnknownIdentifierException {
        List<OWLOntologyChange> allChanges = null;
        for (PreFileSaveHandler handler : preFileSaveHandlers) {
            List<OWLOntologyChange> changes = handler.handle(model);
            if (changes != null && !changes.isEmpty()) {
                if (allChanges == null) {
                    allChanges = new ArrayList<OWLOntologyChange>(
                            changes.size());
                }
                allChanges.addAll(changes);
            }
        }
        return allChanges;
    }

    /**
     * A PreFileSaveHandler may make changes to a model,
     * returning the list of changes it made. Callers will
     * assume that changes have been applied.
     */
    public static interface PreFileSaveHandler {

        public List<OWLOntologyChange> handle(OWLOntology model) throws UnknownIdentifierException;

    }

    public void addPreFileSaveHandler(PreFileSaveHandler handler) {
        if (handler != null) {
            preFileSaveHandlers.add(handler);
        }
    }

    public void updateTaxonAnnotations(ModelContainer mc, METADATA metadata) {
        OWLOntology model = mc.getAboxOntology();
        OWLDataFactory factory = OWLManager.getOWLDataFactory();
        final Set<OWLAnnotation> existingTaxonAnnotations = model.getAnnotations().stream()
                .filter(ann -> ann.getProperty().equals(in_taxon))
                .collect(Collectors.toSet());
        final Set<IRI> existingTaxa = existingTaxonAnnotations.stream()
                .map(OWLAnnotation::getValue)
                .filter(OWLObject::isIRI)
                .map(v -> v.asIRI().get())
                .collect(Collectors.toSet());
        Set<IRI> taxa = getTaxaForModel(model);
        final Set<OWLAnnotation> annotationsToRemove = existingTaxonAnnotations.stream()
                .filter(ann -> !taxa.contains(ann.getValue()))
                .collect(Collectors.toSet());
        final Set<IRI> taxaToAdd = taxa.stream()
                .filter(t -> !existingTaxa.contains(t))
                .collect(Collectors.toSet());
        final Set<OWLOntologyChange> removals = annotationsToRemove.stream()
                .map(ann -> new RemoveOntologyAnnotation(model, ann))
                .collect(Collectors.toSet());
        final Set<OWLOntologyChange> additions = taxaToAdd.stream()
                .map(t -> new AddOntologyAnnotation(model, factory.getOWLAnnotation(in_taxon, t)))
                .collect(Collectors.toSet());
        final List<OWLOntologyChange> changes = new ArrayList<>();
        changes.addAll(removals);
        changes.addAll(additions);
        this.applyChanges(mc, changes, metadata);
    }

    /**
     * Export the ABox for the given modelId in the default
     * {@link OWLDocumentFormat}.
     *
     * @param model
     * @return modelContent
     * @throws OWLOntologyStorageException
     */
    public String exportModel(ModelContainer model)
            throws OWLOntologyStorageException {
        return exportModel(model, ontologyFormat);
    }

    /**
     * Export the ABox for the given modelId in the given ontology format.<br>
     * Warning: The mapping from String to {@link OWLDocumentFormat} does not
     * map every format!
     *
     * @param model
     * @param format
     * @return modelContent
     * @throws OWLOntologyStorageException
     */
    public String exportModel(ModelContainer model, String format)
            throws OWLOntologyStorageException {
        OWLDocumentFormat ontologyFormat = getOWLOntologyFormat(format);
        if (ontologyFormat == null) {
            ontologyFormat = this.ontologyFormat;
        }

        return exportModel(model, ontologyFormat);
    }

    private OWLDocumentFormat getOWLOntologyFormat(String fmt) {
        OWLDocumentFormat ofmt = null;
        if (fmt != null) {
            fmt = fmt.toLowerCase();
            if (fmt.equals("rdfxml"))
                ofmt = new RDFXMLDocumentFormat();
            else if (fmt.equals("owl"))
                ofmt = new RDFXMLDocumentFormat();
            else if (fmt.equals("rdf"))
                ofmt = new RDFXMLDocumentFormat();
            else if (fmt.equals("owx"))
                ofmt = new OWLXMLDocumentFormat();
            else if (fmt.equals("owf"))
                ofmt = new FunctionalSyntaxDocumentFormat();
            else if (fmt.equals("owm"))
                ofmt = new ManchesterSyntaxDocumentFormat();
        }
        return ofmt;
    }

    /**
     * Retrieve a collection of all file/stored model ids found in the repo.<br>
     * Note: Models may not be loaded at this point.
     *
     * @return set of modelids.
     * @throws IOException
     */
    public Set<IRI> getStoredModelIds() throws IOException {
        try (SailRepositoryConnection connection = repo.getConnection()) {
            RepositoryResult<Resource> graphs = connection.getContextIDs();
            try {
                Set<IRI> modelIds = new HashSet<>();
                while (graphs.hasNext()) {
                    modelIds.add(IRI.create(graphs.next().stringValue()));
                }
                return Collections.unmodifiableSet(modelIds);
            } finally {
                ((AutoCloseable) graphs).close();
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Retrieve all model ids currently in memory in long and short form.<br>
     *
     * @return set of modelids.
     * @throws IOException
     */
    public Set<IRI> getCurrentModelIds() throws IOException {
        return new HashSet<IRI>(modelMap.keySet());
    }

    /**
     * Retrieve a collection of all available model ids.<br>
     * Note: Models may not be loaded at this point.
     *
     * @return set of modelids.
     * @throws IOException
     */
    public Set<IRI> getAvailableModelIds() throws IOException {
        Set<IRI> allModelIds = new HashSet<>();
        allModelIds.addAll(this.getStoredModelIds());
        allModelIds.addAll(this.getCurrentModelIds());
        return allModelIds;
    }

    public Map<IRI, Set<OWLAnnotation>> getAllModelAnnotations() throws IOException {
        Map<IRI, Set<OWLAnnotation>> annotations = new HashMap<>();
        // First get annotations from all the stored ontologies
        try {
            SailRepositoryConnection connection = repo.getConnection();
            try {
                String query = "PREFIX owl: <http://www.w3.org/2002/07/owl#> " +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                        "SELECT ?model ?p ?o " +
                        "WHERE { " +
                        "?model a owl:Ontology . " +
                        "?model ?p ?o . " +
                        "FILTER(?p NOT IN (owl:imports, rdf:type, <http://geneontology.org/lego/json-model>)) " +
                        "} ";
                TupleQuery tupleQuery = connection.prepareTupleQuery(QueryLanguage.SPARQL, query);
                TupleQueryResult result = tupleQuery.evaluate();
                OWLDataFactory factory = OWLManager.getOWLDataFactory();
                while (result.hasNext()) {
                    BindingSet binding = result.next();
                    Value model = binding.getValue("model");
                    Value predicate = binding.getValue("p");
                    String value = binding.getValue("o").stringValue();
                    if ((model instanceof org.eclipse.rdf4j.model.IRI) && (predicate instanceof org.eclipse.rdf4j.model.IRI)) {
                        IRI modelId = IRI.create(((org.eclipse.rdf4j.model.IRI) model).toString());
                        OWLAnnotationProperty property = factory
                                .getOWLAnnotationProperty(IRI.create(((org.eclipse.rdf4j.model.IRI) predicate).toString()));
                        OWLAnnotation annotation = factory.getOWLAnnotation(property, factory.getOWLLiteral(value));
                        Set<OWLAnnotation> modelAnnotations = annotations.getOrDefault(modelId, new HashSet<>());
                        modelAnnotations.add(annotation);
                        annotations.put(modelId, modelAnnotations);
                    }
                }
            } catch (MalformedQueryException | QueryEvaluationException e) {
                throw new IOException(e);
            } finally {
                connection.close();
            }
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
        // Next get annotations from ontologies that may not be stored, replacing any stored annotations
        modelMap.values().stream().filter(mc -> mc.isModified()).forEach(mc -> {
            annotations.put(mc.getModelId(), mc.getAboxOntology().getAnnotations());
        });
        return annotations;
    }

    public QueryResult executeSPARQLQuery(String queryText, int timeout) throws
            MalformedQueryException, QueryEvaluationException, RepositoryException {
        SailRepositoryConnection connection = repo.getConnection();
        try {
            List<QueryPrologLexer.Token> tokens = QueryPrologLexer.lex(queryText);
            Set<String> declaredPrefixes = tokens.stream().filter(token -> token.getType().equals(QueryPrologLexer.TokenType.PREFIX)).map(token -> token.getStringValue()).collect(Collectors.toSet());
            StringBuffer queryWithDefaultPrefixes = new StringBuffer();
            for (Entry<String, String> entry : getCuriHandler().getMappings().entrySet()) {
                if (!declaredPrefixes.contains(entry.getKey())) {
                    queryWithDefaultPrefixes.append("PREFIX " + entry.getKey() + ": <" + entry.getValue() + ">");
                    queryWithDefaultPrefixes.append("\n");
                }
            }
            queryWithDefaultPrefixes.append(queryText);
            Query query = connection.prepareQuery(QueryLanguage.SPARQL, queryWithDefaultPrefixes.toString());
            query.setMaxQueryTime(timeout);
            if (query instanceof TupleQuery) {
                TupleQuery tupleQuery = (TupleQuery) query;
                return tupleQuery.evaluate();
            } else if (query instanceof GraphQuery) {
                GraphQuery graphQuery = (GraphQuery) query;
                return graphQuery.evaluate();
            } else if (query instanceof BooleanQuery) {
                throw new UnsupportedOperationException("Unsupported query type."); //FIXME
            } else {
                throw new UnsupportedOperationException("Unsupported query type.");
            }
        } finally {
            connection.close();
        }
    }

    public QueryResult executeSPARQLQueryWithoutPrefixManipulation(String queryText, int timeout) throws
            MalformedQueryException, QueryEvaluationException, RepositoryException {
        SailRepositoryConnection connection = repo.getConnection();
        try {
            Query query = connection.prepareQuery(QueryLanguage.SPARQL, queryText.toString());
            query.setMaxQueryTime(timeout);
            if (query instanceof TupleQuery) {
                TupleQuery tupleQuery = (TupleQuery) query;
                return tupleQuery.evaluate();
            } else if (query instanceof GraphQuery) {
                GraphQuery graphQuery = (GraphQuery) query;
                return graphQuery.evaluate();
            } else if (query instanceof BooleanQuery) {
                throw new UnsupportedOperationException("Unsupported query type."); //FIXME
            } else {
                throw new UnsupportedOperationException("Unsupported query type.");
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public void loadModel(IRI modelId, boolean isOverride) throws OWLOntologyCreationException {
        if (modelMap.containsKey(modelId)) {
            if (!isOverride) {
                throw new OWLOntologyCreationException("Model already exists: " + modelId);
            }
            unlinkModel(modelId);
        }
        try {
            try (SailRepositoryConnection connection = repo.getConnection()) {
                RepositoryResult<Resource> graphs = connection.getContextIDs();
                if (graphs.stream().noneMatch(g -> g.equals(Values.iri(modelId.toString())))) {
                    throw new OWLOntologyCreationException("No such model in datastore: " + modelId);
                }
                ((AutoCloseable) graphs).close();
                RepositoryResult<Statement> statements =
                        connection.getStatements(null, null, null, false, Values.iri(modelId.toString()));
                //setting minimal = false will load the abox with the tbox ontology manager, allowing for OWL understanding of tbox content
                boolean minimal = false;
                OWLOntology abox = loadOntologyDocumentSource(new RioMemoryTripleSource(statements.iterator()), minimal);
                ((AutoCloseable) statements).close();
                abox = postLoadFileFilter(abox);
                ModelContainer model = addModel(modelId, abox);
            } catch (Exception e) {
                throw new OWLOntologyCreationException(e);
            }
        } catch (RepositoryException e) {
            throw new OWLOntologyCreationException(e);
        }
    }

    @Override
    public OWLOntology loadModelABox(IRI modelId) throws OWLOntologyCreationException {
        return loadModelABox(modelId, null);
    }

    @Override
    public OWLOntology loadModelABox(IRI modelId, OWLOntologyManager manager) throws OWLOntologyCreationException {
        LOG.info("Load model abox: " + modelId + " from database");
        try {
            try (SailRepositoryConnection connection = repo.getConnection()) {
                //TODO repeated code with loadModel
                RepositoryResult<Resource> graphs = connection.getContextIDs();
                if (graphs.stream().noneMatch(g -> g.equals(Values.iri(modelId.toString())))) {
                    throw new OWLOntologyCreationException("No such model in datastore: " + modelId);
                }
                ((AutoCloseable) graphs).close();
                RepositoryResult<Statement> statements =
                        connection.getStatements(null, null, null, false, Values.iri(modelId.toString()));
                //setting minimal to true will give an OWL abox with triples that won't be connected to the tbox, hence e.g. object properties might not be recognized.
                boolean minimal = true;
                OWLOntology abox;
                if (manager == null) {
                    abox = loadOntologyDocumentSource(new RioMemoryTripleSource(statements.iterator()), minimal);
                } else {
                    abox = loadOntologyDocumentSource(new RioMemoryTripleSource(statements.iterator()), minimal, manager);
                }
                ((AutoCloseable) statements).close();
                abox = postLoadFileFilter(abox);
                return abox;
            } catch (Exception e) {
                throw new OWLOntologyCreationException(e);
            }
        } catch (RepositoryException e) {
            throw new OWLOntologyCreationException(e);
        }
    }

    private OWLOntology postLoadFileFilter(OWLOntology model) {
        for (PostLoadOntologyFilter filter : postLoadOntologyFilters) {
            model = filter.filter(model);
        }
        return model;
    }

    public static interface PostLoadOntologyFilter {

        OWLOntology filter(OWLOntology model);
    }

    public void addPostLoadOntologyFilter(PostLoadOntologyFilter filter) {
        if (filter != null) {
            postLoadOntologyFilters.add(filter);
        }
    }

    public void importBulkModelsToDatabase(File folder, boolean skipMarkedDelete) throws IOException {
        int totalFiles = 0;
        int storedFiles = 0;
        LOG.info("Loading gocams from " + folder);
        if (folder.exists()) {
            if (folder.isDirectory()) {
                totalFiles = folder.listFiles().length;
                try (SailRepositoryConnection connection = repo.getConnection()) {
                    connection.begin(IsolationLevels.NONE);
                    for (File file : FileUtils.listFiles(folder, null, true)) {
                        if (file.getName().endsWith("ttl")) {
                            if (skipMarkedDelete && scanForIsDelete(file)) {
                                LOG.info("Skipping file marked for delete: " + file);
                            } else {
                                java.util.Optional<org.eclipse.rdf4j.model.IRI> ontIRIOpt = scanForOntologyIRI(file).map(id -> Values.iri(id));
                                java.util.Optional<org.eclipse.rdf4j.model.IRI> importOpt = scanForImport(file).map(id -> Values.iri(id));
                                if (importOpt.isPresent()) {
                                    LOG.warn("Skipping file containing owl:imports statement: " + file);
                                } else if (!ontIRIOpt.isPresent()) {
                                    LOG.warn("Skipping file with unknown extension: " + file);
                                } else {
                                    org.eclipse.rdf4j.model.IRI graph = ontIRIOpt.get();
                                    if (file.getName().endsWith(".ttl")) {
                                        connection.add(file, "", RDFFormat.TURTLE, graph);
                                        storedFiles++;
                                    } else if (file.getName().endsWith(".owl")) {
                                        connection.add(file, "", RDFFormat.RDFXML, graph);
                                        storedFiles++;
                                    }
                                }
                            }
                        }
                    }
                    connection.commit();
                }
            }
            LOG.info("Done loading gocams, loaded: " + storedFiles + " out of: " + totalFiles + " files");
        }

    }

    /**
     * Imports ontology RDF directly to database. Will remove any import statements in the ontology (because GO-CAMs should not have any as of now)
     *
     * @param file
     * @throws OWLOntologyCreationException
     * @throws IOException
     * @throws RepositoryException
     */
    public String importModelToDatabase(File file, boolean skipMarkedDelete) throws
            OWLOntologyCreationException, RepositoryException, IOException, RDFParseException, RDFHandlerException {
        final boolean delete;
        if (skipMarkedDelete) {
            delete = scanForIsDelete(file);
        } else {
            delete = false;
        }
        String modeliri = null;
        if (!delete) {
            java.util.Optional<org.eclipse.rdf4j.model.IRI> ontIRIOpt = scanForOntologyIRI(file).map(id -> Values.iri(id));
            if (ontIRIOpt.isPresent()) {
                java.util.Optional<org.eclipse.rdf4j.model.IRI> importOpt = scanForImport(file).map(id -> Values.iri(id));
                if (importOpt.isPresent()) {
                    modeliri = ontIRIOpt.get().stringValue();
                    //need to remove the imports before loading.
                    //if the imports are large, this gets slow
                    //consider 1) loading the model as below 2) running a SPARQL update to get rid of the imports
                    OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();
                    OWLOntology cam = ontman.loadOntologyFromOntologyDocument(file);
                    Set<OWLImportsDeclaration> imports = cam.getImportsDeclarations();
                    for (OWLImportsDeclaration impdec : imports) {
                        RemoveImport rm = new RemoveImport(cam, impdec);
                        ontman.applyChange(rm);
                    }
                    //write it
                    this.writeModelToDatabase(cam, IRI.create(ontIRIOpt.get().stringValue()));
                } else { //otherwise just load it all up as rdf (faster because avoids owl api)
                    // assuming bulk load?
                    final SailRepositoryConnection connection = repo.getConnection();
                    try {
                        connection.begin(IsolationLevels.READ_COMMITTED);
                        try {
                            org.eclipse.rdf4j.model.IRI graph = ontIRIOpt.get();
                            connection.clear(graph);
                            //FIXME Turtle format is hard-coded here
                            if (file.getName().endsWith(".ttl")) {
                                connection.add(file, "", RDFFormat.TURTLE, graph);
                            } else if (file.getName().endsWith(".owl")) {
                                connection.add(file, "", RDFFormat.RDFXML, graph);
                            }
                            connection.commit();
                            modeliri = graph.toString();
                        } catch (Exception e) {
                            connection.rollback();
                            throw e;
                        }
                    } finally {
                        connection.close();
                    }
                }
            } else {
                throw new OWLOntologyCreationException("Detected anonymous ontology; must have IRI");
            }
        } else {
            System.err.println("skipping " + file.getName());
        }
        return modeliri;

    }

    /**
     * checks an OWLRDF (ttl) file for owl import statements
     *
     * @param file
     * @return
     * @throws RDFParseException
     * @throws RDFHandlerException
     * @throws IOException
     */
    private java.util.Optional<String> scanForImport(File file) throws
            RDFParseException, RDFHandlerException, IOException {
        AbstractRDFHandler handler = new AbstractRDFHandler() {
            public void handleStatement(Statement statement) {
                if (statement.getPredicate().stringValue().equals("http://www.w3.org/2002/07/owl#imports"))
                    throw new FoundTripleException(statement);
            }
        };
        InputStream inputStream = new FileInputStream(file);
        try {
            //FIXME Turtle format is hard-coded here
            RDFParser parser = Rio.createParser(RDFFormat.RDFXML);
            if (file.getName().endsWith(".ttl")) {
                parser = Rio.createParser(RDFFormat.TURTLE);
            }
            parser.setRDFHandler(handler);
            parser.parse(inputStream, "");
            // If an import triple is found, it will be thrown out
            // in an exception. Otherwise, return empty.
            return java.util.Optional.empty();
        } catch (FoundTripleException fte) {
            Statement statement = fte.getStatement();
            return java.util.Optional.of(statement.getObject().stringValue());
        } finally {
            inputStream.close();
        }
    }

    /**
     * Tries to efficiently find the ontology IRI triple without loading the whole file.
     *
     * @throws IOException
     * @throws RDFHandlerException
     * @throws RDFParseException
     */
    public java.util.Optional<String> scanForOntologyIRI(File file) throws
            RDFParseException, RDFHandlerException, IOException {
        AbstractRDFHandler handler = new AbstractRDFHandler() {
            public void handleStatement(Statement statement) {
                if (statement.getObject().stringValue().equals("http://www.w3.org/2002/07/owl#Ontology") &&
                        statement.getPredicate().stringValue().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"))
                    throw new FoundTripleException(statement);
            }
        };
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            //FIXME Turtle format is hard-coded here
            RDFParser parser = Rio.createParser(RDFFormat.RDFXML);
            if (file.getName().endsWith(".ttl")) {
                parser = Rio.createParser(RDFFormat.TURTLE);
            }
            parser.setRDFHandler(handler);
            parser.parse(inputStream, "");
            // If an ontology IRI triple is found, it will be thrown out
            // in an exception. Otherwise, return empty.
            return Optional.empty();
        } catch (FoundTripleException fte) {
            Statement statement = fte.getStatement();
            if (statement.getSubject() instanceof BNode) {
                LOG.warn("Blank node subject for ontology triple: " + statement);
                return Optional.empty();
            } else {
                return Optional.of(statement.getSubject().stringValue());
            }
        }
    }

    private boolean scanForIsDelete(File file) throws RDFParseException, RDFHandlerException, IOException {
        AbstractRDFHandler handler = new AbstractRDFHandler() {
            public void handleStatement(Statement statement) {
                if (statement.getPredicate().stringValue().equals(AnnotationShorthand.modelstate.getAnnotationProperty().toString()) &&
                        statement.getObject().stringValue().equals("delete"))
                    throw new FoundTripleException(statement);
            }
        };
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            //FIXME Turtle format is hard-coded here
            RDFParser parser = Rio.createParser(RDFFormat.TURTLE);
            parser.setRDFHandler(handler);
            parser.parse(inputStream, "");
            // If an ontology IRI triple is found, it will be thrown out
            // in an exception. Otherwise, return false.
            return false;
        } catch (FoundTripleException fte) {
            return true;
        }
    }

    private static class FoundTripleException extends RuntimeException {

        private static final long serialVersionUID = 8366509854229115430L;
        private final Statement statement;

        public FoundTripleException(Statement statement) {
            this.statement = statement;
        }

        public Statement getStatement() {
            return this.statement;
        }
    }

    private static class EmptyOntologyIRIMapper implements OWLOntologyIRIMapper {

        private static final long serialVersionUID = 8432563430320023805L;

        public static IRI emptyOntologyIRI = IRI.create("http://example.org/empty");

        @Override
        public IRI getDocumentIRI(IRI ontologyIRI) {
            return emptyOntologyIRI;
        }

    }

    /**
     * Export all models to disk.
     *
     * @throws OWLOntologyStorageException
     * @throws OWLOntologyCreationException
     * @throws IOException
     */
    public void dumpAllStoredModels() throws OWLOntologyStorageException, OWLOntologyCreationException, IOException {
        File folder = new File(this.pathToExportFolder);
        for (IRI modelId : this.getStoredModelIds()) {
            dumpStoredModel(modelId, folder);
        }
    }

    /**
     * Save a model to disk.
     *
     * @throws OWLOntologyStorageException
     * @throws OWLOntologyCreationException
     * @throws IOException
     */
    public void dumpStoredModel(IRI modelId, File folder) throws IOException {
        // preliminary checks for the target file
        String fileName = StringUtils.replaceOnce(modelId.toString(), modelIdPrefix, "") + ".ttl";
        File targetFile = new File(folder, fileName).getAbsoluteFile();
        if (targetFile.exists()) {
            if (targetFile.isFile() == false) {
                throw new IOException("For modelId: '" + modelId + "', the resulting path is not a file: " + targetFile.getAbsolutePath());
            }
            if (targetFile.canWrite() == false) {
                throw new IOException("For modelId: '" + modelId + "', Cannot write to the file: " + targetFile.getAbsolutePath());
            }
        } else {
            File targetFolder = targetFile.getParentFile();
            FileUtils.forceMkdir(targetFolder);
        }
        File tempFile = null;
        try {
            // create tempFile
            String prefix = modelId.toString(); // TODO escape
            tempFile = File.createTempFile(prefix, ".ttl");
            try {
                try (SailRepositoryConnection connection = repo.getConnection(); OutputStream out = Files.newOutputStream(tempFile.toPath())) {
                    // Workaround for order dependence of RDF reading by OWL API
                    // Need to output ontology triple first until this bug is fixed:
                    // https://github.com/owlcs/owlapi/issues/574
                    ValueFactory factory = connection.getValueFactory();
                    Statement ontologyDeclaration = factory.createStatement(factory.createIRI(modelId.toString()), RDF.TYPE, OWL.ONTOLOGY);
                    Rio.write(Collections.singleton(ontologyDeclaration), out, RDFFormat.TURTLE);
                    // end workaround
                    RDFWriter writer = Rio.createWriter(RDFFormat.TURTLE, out);
                    connection.export(writer, Values.iri(modelId.toString()));
                    // copy temp file to the finalFile
                    FileUtils.copyFile(tempFile, targetFile);
                }
            } catch (RepositoryException | RDFHandlerException e) {
                throw new IOException(e);
            }
        } finally {
            // delete temp file
            FileUtils.deleteQuietly(tempFile);
        }
    }

    public void dispose() {
        super.dispose();
        try {
            repo.getSail().shutDown();
            repo.shutDown();
            if (this.getGolego_repo() != null) {
                getGolego_repo().dispose();
            }
        } catch (RepositoryException e) {
            LOG.error("Failed to shutdown Blazegraph sail.", e);
        }
    }

    public Map<IRI, Set<String>> buildTaxonModelMap() throws IOException {
        Map<String, Set<String>> model_genes = buildModelGeneMap();
        Map<IRI, Set<String>> taxon_models = new HashMap<IRI, Set<String>>();
        for (String model : model_genes.keySet()) {
            Set<String> genes = model_genes.get(model);
            Set<IRI> taxa = this.getGolego_repo().getTaxaByGenes(genes);
            for (IRI taxon : taxa) {
                Set<String> models = taxon_models.get(taxon);
                if (models == null) {
                    models = new HashSet<String>();
                }
                models.add(model);
                taxon_models.put(taxon, models);
            }
        }
        return taxon_models;
    }

    public Map<String, Set<String>> buildModelGeneMap() {
        Map<String, Set<String>> model_genes = new HashMap<String, Set<String>>();
        TupleQueryResult result;
        String sparql = "SELECT ?id (GROUP_CONCAT(DISTINCT ?type;separator=\";\") AS ?types) WHERE {\n" +
                "  GRAPH ?id {  \n" +
                "?i rdf:type ?type .\n" +
                "FILTER (?type != <http://www.w3.org/2002/07/owl#Axiom> \n" +
                "        && ?type != <http://www.w3.org/2002/07/owl#NamedIndividual> \n" +
                "        && ?type != <http://www.w3.org/2002/07/owl#Ontology> \n" +
                "        && ?type != <http://www.w3.org/2002/07/owl#Class> \n" +
                "        && ?type != <http://www.w3.org/2002/07/owl#ObjectProperty> \n" +
                "        && ?type != <http://www.w3.org/2000/01/rdf-schema#Datatype> \n" +
                "        && ?type != <http://www.w3.org/2002/07/owl#AnnotationProperty>) . \n" +
                "FILTER (!regex(str(?type), \"http://purl.obolibrary.org/obo/\" ) )    \n" +
                "    }\n" +
                "  } \n" +
                "  \n" +
                "GROUP BY ?id";
        try {
            result = (TupleQueryResult) executeSPARQLQueryWithoutPrefixManipulation(sparql, 1000);
            while (result.hasNext()) {
                BindingSet bs = result.next();
                String model = bs.getBinding("id").getValue().stringValue();
                String genes = bs.getBinding("types").getValue().stringValue();
                Set<String> g = new HashSet<String>();
                if (genes != null) {
                    String[] geness = genes.split(";");
                    for (String gene : geness) {
                        g.add(gene);
                    }
                }
                model_genes.put(model, g);
            }
        } catch (MalformedQueryException | QueryEvaluationException | RepositoryException e) {
            e.printStackTrace();
        }
        return model_genes;
    }

    @Nonnull
    public Set<IRI> getTaxaForModel(OWLOntology model) {
        Set<String> potentialGenes = model.getAxioms(AxiomType.CLASS_ASSERTION).stream()
                .map(OWLClassAssertionAxiom::getClassExpression)
                .filter(IsAnonymous::isNamed)
                .map(ce -> ce.asOWLClass().getIRI().toString())
                .filter(id -> !id.startsWith("http://purl.obolibrary.org/obo/"))
                .collect(Collectors.toSet());
        if (potentialGenes.isEmpty()) {
            return Collections.emptySet();
        } else {
            try {
                return this.getGolego_repo().getTaxaByGenes(potentialGenes);
            } catch (IOException e) {
                LOG.error("Error querying ontology Blazegraph", e);
                return Collections.emptySet();
            }
        }
    }

    public void addTaxonMetadata() throws IOException {
        Map<IRI, Set<String>> taxon_models = buildTaxonModelMap();
        LOG.info("Ready to update " + taxon_models.keySet().size() + " " + taxon_models.keySet());
        for (IRI taxon : taxon_models.keySet()) {
            LOG.info("Updating models in taxon " + taxon);
            Set<String> models = taxon_models.get(taxon);
            models.stream().parallel().forEach(model -> {
                //fine for a few thousand models, but ends up eating massive ram for many
                //addTaxonWithOWL(IRI.create(model), IRI.create(taxon));
                try {
                    addTaxonToDatabaseWithSparql(IRI.create(model), taxon);
                } catch (RepositoryException | UpdateExecutionException | MalformedQueryException |
                         InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            });
        }
    }

    //now try with sparql insert
    public int addTaxonToDatabaseWithSparql(IRI model_iri, IRI taxon_iri) throws
            RepositoryException, UpdateExecutionException, MalformedQueryException, InterruptedException {
        int changes = 0;
        String update =
                "INSERT DATA\n" +
                        "{ GRAPH <" + model_iri.toString() + "> { " +
                        "  <" + model_iri.toString() + "> <" + BlazegraphOntologyManager.in_taxon_uri + "> <" + taxon_iri.toString() + ">" +
                        "} }";

        synchronized (repo) {
            final SailRepositoryConnection conn = repo.getConnection();
            final Repository repo = conn.getRepository();
            NotifyingSail notifyingSail = null;
            if (repo instanceof SailRepository) {
                Sail sail = ((SailRepository) repo).getSail();
                if (sail instanceof NotifyingSail) {
                    notifyingSail = (NotifyingSail) sail;
                }
            }
            conn.begin();
            try {
                conn.begin();
                SailMutationCounter counter = new SailMutationCounter();
                if (notifyingSail != null) {
                    notifyingSail.addSailChangedListener(counter);
                }
                conn.prepareUpdate(QueryLanguage.SPARQL, update).execute();
                changes = counter.mutationCount();
                if (notifyingSail != null) {
                    notifyingSail.removeSailChangedListener(counter);
                }
                conn.commit();
            } finally {
                conn.close();
            }
        }
        return changes;
    }

}
