package org.geneontology.minerva;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import com.bigdata.rdf.rio.json.BigdataSPARQLResultsJSONWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.geneontology.minerva.util.ReverseChangeGenerator;
import org.openrdf.model.*;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.vocabulary.OWL;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.query.*;
import org.openrdf.query.impl.TupleQueryResultBuilder;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.Rio;
import org.openrdf.rio.helpers.RDFHandlerBase;
import org.openrdf.rio.helpers.StatementCollector;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.ManchesterSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyAlreadyExistsException;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyDocumentAlreadyExistsException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.rio.RioMemoryTripleSource;
import org.semanticweb.owlapi.rio.RioRenderer;

import com.bigdata.journal.Options;
import com.bigdata.rdf.sail.BigdataSail;
import com.bigdata.rdf.sail.BigdataSailRepository;
import com.bigdata.rdf.sail.BigdataSailRepositoryConnection;
import com.google.common.base.Optional;

import info.aduna.iteration.Iterations;
import owltools.gaf.parser.GafObjectsBuilder;
import owltools.graph.OWLGraphWrapper;

public class BlazegraphMolecularModelManager<METADATA> extends CoreMolecularModelManager<METADATA> {

	private static Logger LOG = Logger
			.getLogger(BlazegraphMolecularModelManager.class);

	boolean isPrecomputePropertyClassCombinations = false;

	final String pathToOWLStore;
	final String pathToExportFolder;
	private final BigdataSailRepository repo;

	private final String modelIdPrefix;

	GafObjectsBuilder builder = new GafObjectsBuilder();
	OWLDocumentFormat ontologyFormat = new TurtleDocumentFormat();

	private final List<PreFileSaveHandler> preFileSaveHandlers = new ArrayList<PreFileSaveHandler>();
	private final List<PostLoadOntologyFilter> postLoadOntologyFilters = new ArrayList<PostLoadOntologyFilter>();

	/**
	 * @param graph
	 * @param modelIdPrefix
	 * @param pathToJournal Path to Blazegraph journal file to use. 
	 * Only one instance of Blazegraph can use this file at a time.
	 * @throws OWLOntologyCreationException
	 */
	public BlazegraphMolecularModelManager(OWLGraphWrapper graph, String modelIdPrefix, String pathToJournal, String pathToExportFolder)
			throws OWLOntologyCreationException {
		super(graph);
		this.modelIdPrefix = modelIdPrefix;
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

	private BigdataSailRepository initializeRepository(String pathToJournal) {
		try {
			Properties properties = new Properties();
			properties.load(this.getClass().getResourceAsStream("blazegraph.properties"));
			properties.setProperty(Options.FILE, pathToJournal);
			BigdataSail sail = new BigdataSail(properties);
			BigdataSailRepository repository = new BigdataSailRepository(sail);
			repository.initialize();
			return repository;
		} catch (RepositoryException e) {
			LOG.fatal("Could not create Blazegraph sail", e);
			return null;		
		} catch (IOException e) {
			LOG.fatal("Could not create Blazegraph sail", e);
			return null;
		}
	}

	private void createImports(OWLOntology ont, OWLOntologyID tboxId,
			METADATA metadata) throws OWLOntologyCreationException {
		OWLOntologyManager m = ont.getOWLOntologyManager();
		OWLDataFactory f = m.getOWLDataFactory();

		// import T-Box
		Optional<IRI> ontologyIRI = tboxId.getOntologyIRI();
		if (ontologyIRI.isPresent()) {
			OWLImportsDeclaration tBoxImportDeclaration = f
					.getOWLImportsDeclaration(ontologyIRI.get());
			m.applyChange(new AddImport(ont, tBoxImportDeclaration));
		}

		// import additional ontologies via IRI
		for (IRI importIRI : additionalImports) {
			OWLImportsDeclaration importDeclaration = f
					.getOWLImportsDeclaration(importIRI);
			// check that the import ontology is available
			OWLOntology importOntology = m.getOntology(importIRI);
			if (importOntology == null) {
				// only try to load it, if it isn't already loaded
				try {
					m.loadOntology(importIRI);
				} catch (OWLOntologyDocumentAlreadyExistsException e) {
					// ignore
				} catch (OWLOntologyAlreadyExistsException e) {
					// ignore
				}
			}
			m.applyChange(new AddImport(ont, importDeclaration));
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
		final OWLOntologyManager m = graph.getManager();
		final OWLOntology tbox = graph.getSourceOntology();
		OWLOntology abox = null;
		ModelContainer model = null;
		try {
			abox = m.createOntology(modelId);

			// add imports to T-Box and additional ontologies via IRI
			createImports(abox, tbox.getOntologyID(), metadata);

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
	 * Save all models to disk. The optional annotations may be used to set
	 * saved_by and other meta data.
	 * 
	 * @param annotations
	 * @param metadata
	 * 
	 * @throws OWLOntologyStorageException
	 * @throws OWLOntologyCreationException
	 * @throws IOException
	 * @throws RepositoryException 
	 * @throws UnknownIdentifierException 
	 */
	public void saveAllModels(Set<OWLAnnotation> annotations, METADATA metadata)
			throws OWLOntologyStorageException, OWLOntologyCreationException,
			IOException, RepositoryException, UnknownIdentifierException {
		for (Entry<IRI, ModelContainer> entry : modelMap.entrySet()) {
			saveModel(entry.getValue(), annotations, metadata);
		}
	}

	/**
	 * Save a model to the database.
	 * 
	 * @param m
	 * @param annotations
	 * @param metadata
	 *
	 * @throws OWLOntologyStorageException
	 * @throws OWLOntologyCreationException
	 * @throws IOException
	 * @throws RepositoryException 
	 * @throws UnknownIdentifierException 
	 */
	public void saveModel(ModelContainer m,
			Set<OWLAnnotation> annotations, METADATA metadata)
					throws OWLOntologyStorageException, OWLOntologyCreationException,
					IOException, RepositoryException, UnknownIdentifierException {
		IRI modelId = m.getModelId();
		final OWLOntology ont = m.getAboxOntology();
		final OWLOntologyManager manager = ont.getOWLOntologyManager();
		List<OWLOntologyChange> changes = preSaveFileHandler(ont);
		synchronized(ont) {
			try {
				this.writeModelToDatabase(ont, modelId);
				// reset modified flag for abox after successful save
				m.setAboxModified(false);
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
		// Only one thread at a time can use the unisolated connection.
		synchronized(repo) {
			final BigdataSailRepositoryConnection connection = repo.getUnisolatedConnection();
			try {
				connection.begin();
				try {
					URI graph = new URIImpl(modelId.toString());
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

	public static interface PreFileSaveHandler {

		public List<OWLOntologyChange> handle(OWLOntology model) throws UnknownIdentifierException;

	}

	public void addPreFileSaveHandler(PreFileSaveHandler handler) {
		if (handler != null) {
			preFileSaveHandlers.add(handler);
		}
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
		try {
			BigdataSailRepositoryConnection connection = repo.getReadOnlyConnection();
			try {
				RepositoryResult<Resource> graphs = connection.getContextIDs();
				Set<IRI> modelIds = new HashSet<>();
				while (graphs.hasNext()) {
					modelIds.add(IRI.create(graphs.next().stringValue()));
				}
				graphs.close();
				return Collections.unmodifiableSet(modelIds);
			} finally {
				connection.close();
			}	
		} catch (RepositoryException e) {
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
			BigdataSailRepositoryConnection connection = repo.getReadOnlyConnection();
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
					if ((model instanceof URI) && (predicate instanceof URI)) {
						IRI modelId = IRI.create(((URI)model).toString());
						OWLAnnotationProperty property = factory
								.getOWLAnnotationProperty(IRI.create(((URI)predicate).toString()));
						OWLAnnotation annotation = factory.getOWLAnnotation(property, factory.getOWLLiteral(value));
						Set<OWLAnnotation> modelAnnotations = annotations.getOrDefault(modelId, new HashSet<>());
						modelAnnotations.add(annotation);
						annotations.put(modelId, modelAnnotations);
					}
				}
			} catch (MalformedQueryException e) {
				throw new IOException(e);
			} catch (QueryEvaluationException e) {
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

    public QueryResult executeSPARQLQuery(String queryText, int timeout) throws MalformedQueryException, QueryEvaluationException, RepositoryException {
        BigdataSailRepositoryConnection connection = repo.getReadOnlyConnection();
        try {
            Query query = connection.prepareQuery(QueryLanguage.SPARQL, queryText);
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
	protected void loadModel(IRI modelId, boolean isOverride) throws OWLOntologyCreationException {
		LOG.info("Load model: " + modelId + " from database");
		if (modelMap.containsKey(modelId)) {
			if (!isOverride) {
				throw new OWLOntologyCreationException("Model already exists: " + modelId);
			}
			unlinkModel(modelId);
		}
		try {
			BigdataSailRepositoryConnection connection = repo.getReadOnlyConnection();
			try {
				RepositoryResult<Resource> graphs = connection.getContextIDs();
				if (!Iterations.asSet(graphs).contains(new URIImpl(modelId.toString()))) {
					throw new OWLOntologyCreationException("No such model in datastore: " + modelId);
				}
				graphs.close();
				RepositoryResult<Statement> statements = 
						connection.getStatements(null, null, null, false, new URIImpl(modelId.toString()));
				OWLOntology abox = loadOntologyDocumentSource(new RioMemoryTripleSource(statements), false);
				statements.close();
				abox = postLoadFileFilter(abox);
				ModelContainer model = addModel(modelId, abox);
				updateImports(model);
			} finally {
				connection.close();
			}	
		} catch (RepositoryException e) {
			throw new OWLOntologyCreationException(e);
		}
	}

	@Override
	protected OWLOntology loadModelABox(IRI modelId) throws OWLOntologyCreationException {
		LOG.info("Load model abox: " + modelId + " from database");
		try {
			BigdataSailRepositoryConnection connection = repo.getReadOnlyConnection();
			try {
				//TODO repeated code with loadModel
				RepositoryResult<Resource> graphs = connection.getContextIDs();
				if (!Iterations.asSet(graphs).contains(new URIImpl(modelId.toString()))) {
					throw new OWLOntologyCreationException("No such model in datastore: " + modelId);
				}
				graphs.close();
				RepositoryResult<Statement> statements = 
						connection.getStatements(null, null, null, false, new URIImpl(modelId.toString()));
				OWLOntology abox = loadOntologyDocumentSource(new RioMemoryTripleSource(statements), true);
				statements.close();
				abox = postLoadFileFilter(abox);
				return abox;
			} finally {
				connection.close();
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

	/**
	 * Imports ontology RDF directly to database. No OWL checks are performed. 
	 * @param file
	 * @throws OWLOntologyCreationException 
	 * @throws IOException 
	 * @throws RepositoryException 
	 */
	public void importModelToDatabase(File file, boolean skipMarkedDelete) throws OWLOntologyCreationException, RepositoryException, IOException, RDFParseException, RDFHandlerException {
		synchronized(repo) {
			final BigdataSailRepositoryConnection connection = repo.getUnisolatedConnection();
			try {
				connection.begin();
				try {
					final boolean delete;
					if (skipMarkedDelete) {
						delete = scanForIsDelete(file);	
					} else {
						delete = false;
					}
					if (!delete) {
						java.util.Optional<URI> ontIRIOpt = scanForOntologyIRI(file).map(id -> new URIImpl(id));
						if (ontIRIOpt.isPresent()) {
							URI graph = ontIRIOpt.get();
							connection.clear(graph);
							//FIXME Turtle format is hard-coded here
							connection.add(file, "", RDFFormat.TURTLE, graph);
							connection.commit();
						} else {
							throw new OWLOntologyCreationException("Detected anonymous ontology; must have IRI");
						}
					}
				} catch (Exception e) {
					connection.rollback();
					throw e;
				}
			} finally {
				connection.close();
			}
		}
	}

	/**
	 * Tries to efficiently find the ontology IRI triple without loading the whole file.
	 * @throws IOException 
	 * @throws RDFHandlerException 
	 * @throws RDFParseException 
	 */
	private java.util.Optional<String> scanForOntologyIRI(File file) throws RDFParseException, RDFHandlerException, IOException {
		RDFHandlerBase handler = new RDFHandlerBase() {
			public void handleStatement(Statement statement) { 
				if (statement.getObject().stringValue().equals("http://www.w3.org/2002/07/owl#Ontology") &&
						statement.getPredicate().stringValue().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")) throw new FoundTripleException(statement);
			}
		};
		InputStream inputStream = new FileInputStream(file);
		try {
			//FIXME Turtle format is hard-coded here
			RDFParser parser = Rio.createParser(RDFFormat.TURTLE);
			parser.setRDFHandler(handler);
			parser.parse(inputStream, "");
			// If an ontology IRI triple is found, it will be thrown out
			// in an exception. Otherwise, return empty.
			return java.util.Optional.empty();
		} catch (FoundTripleException fte) {
			Statement statement = fte.getStatement();
			if (statement.getSubject() instanceof BNode ) {
				LOG.warn("Blank node subject for ontology triple: " + statement);
				return java.util.Optional.empty();
			} else {
				return java.util.Optional.of(statement.getSubject().stringValue());
			}
		} finally {
			inputStream.close();
		}
	}
	
	private boolean scanForIsDelete(File file) throws RDFParseException, RDFHandlerException, IOException {
		RDFHandlerBase handler = new RDFHandlerBase() {
			
			public void handleStatement(Statement statement) { 
				if (statement.getPredicate().stringValue().equals(AnnotationShorthand.modelstate.getAnnotationProperty().toString()) && 
					statement.getObject().stringValue().equals("delete")) throw new FoundTripleException(statement);
			}
		};
		InputStream inputStream = new FileInputStream(file);
		try {
			//FIXME Turtle format is hard-coded here
			RDFParser parser = Rio.createParser(RDFFormat.TURTLE);
			parser.setRDFHandler(handler);
			parser.parse(inputStream, "");
			// If an ontology IRI triple is found, it will be thrown out
			// in an exception. Otherwise, return false.
			return false;
		} catch (FoundTripleException fte) {
			return true;
		} finally {
			inputStream.close();
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
	 * @param annotations
	 * @param metadata
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
	 * @param m 
	 * @param annotations 
	 * @param metadata
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
				throw new IOException("For modelId: '"+modelId+"', the resulting path is not a file: " + targetFile.getAbsolutePath());
			}
			if (targetFile.canWrite() == false) {
				throw new IOException("For modelId: '"+modelId+"', Cannot write to the file: " + targetFile.getAbsolutePath());
			}
		}
		else {
			File targetFolder = targetFile.getParentFile();
			FileUtils.forceMkdir(targetFolder);
		}
		File tempFile = null;
		try {
			// create tempFile
			String prefix = modelId.toString(); // TODO escape
			tempFile = File.createTempFile(prefix, ".ttl");
			try {
				BigdataSailRepositoryConnection connection = repo.getReadOnlyConnection();
				try {
					OutputStream out = new FileOutputStream(tempFile);
					// Workaround for order dependence of RDF reading by OWL API
					// Need to output ontology triple first until this bug is fixed:
					// https://github.com/owlcs/owlapi/issues/574
					ValueFactory factory = connection.getValueFactory();
					Statement ontologyDeclaration = factory.createStatement(factory.createURI(modelId.toString()), RDF.TYPE, OWL.ONTOLOGY);
					Rio.write(Collections.singleton(ontologyDeclaration), out, RDFFormat.TURTLE);
					// end workaround
					RDFWriter writer = Rio.createWriter(RDFFormat.TURTLE, out);
					connection.export(writer, new URIImpl(modelId.toString()));
					// copy temp file to the finalFile
					FileUtils.copyFile(tempFile, targetFile);
				}  finally {
					connection.close();
				}	
			} catch (RepositoryException e) {
				throw new IOException(e);
			} catch (RDFHandlerException e) {
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
			repo.shutDown();
		} catch (RepositoryException e) {
			LOG.error("Failed to shutdown Blazegraph sail.", e);
		}
	}

}
