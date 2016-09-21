package org.geneontology.minerva;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.geneontology.minerva.util.ReverseChangeGenerator;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.rio.helpers.StatementCollector;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.ManchesterSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyAlreadyExistsException;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyDocumentAlreadyExistsException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.rio.RioMemoryTripleSource;
import org.semanticweb.owlapi.rio.RioRenderer;

import owltools.gaf.parser.GafObjectsBuilder;
import owltools.graph.OWLGraphWrapper;

import com.bigdata.journal.Options;
import com.bigdata.rdf.sail.BigdataSail;
import com.bigdata.rdf.sail.BigdataSailRepository;
import com.bigdata.rdf.sail.BigdataSailRepositoryConnection;
import com.google.common.base.Optional;

public class BlazegraphMolecularModelManager<METADATA> extends CoreMolecularModelManager<METADATA> {

	private static Logger LOG = Logger
			.getLogger(BlazegraphMolecularModelManager.class);

	boolean isPrecomputePropertyClassCombinations = false;
	
	final String pathToOWLStore;
	private final BigdataSailRepository repo;

	private final String modelIdPrefix;

	GafObjectsBuilder builder = new GafObjectsBuilder();
	// WARNING: Do *NOT* switch to functional syntax until the OWL-API has fixed
	// a bug.
	OWLDocumentFormat ontologyFormat = new ManchesterSyntaxDocumentFormat();

	private final List<PreFileSaveHandler> preFileSaveHandlers = new ArrayList<PreFileSaveHandler>();
	private final List<PostLoadOntologyFilter> postLoadOntologyFilters = new ArrayList<PostLoadOntologyFilter>();

	/**
	 * @param graph
	 * @param modelIdPrefix
	 * @throws OWLOntologyCreationException
	 */
	public BlazegraphMolecularModelManager(OWLGraphWrapper graph, String modelIdPrefix, String pathToJournal)
			throws OWLOntologyCreationException {
		super(graph);
		this.modelIdPrefix = modelIdPrefix;
		this.pathToOWLStore = pathToJournal;
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
	 */
	public void saveAllModels(Set<OWLAnnotation> annotations, METADATA metadata)
			throws OWLOntologyStorageException, OWLOntologyCreationException,
			IOException, RepositoryException {
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
	 */
	public synchronized void saveModel(ModelContainer m,
			Set<OWLAnnotation> annotations, METADATA metadata)
			throws OWLOntologyStorageException, OWLOntologyCreationException,
			IOException, RepositoryException {
		IRI modelId = m.getModelId();
		final OWLOntology ont = m.getAboxOntology();
		final OWLOntologyManager manager = ont.getOWLOntologyManager();
		final BigdataSailRepositoryConnection connection = repo.getUnisolatedConnection();
		try {
			List<OWLOntologyChange> changes = preSaveFileHandler(ont);
			connection.begin();
			try {
				URI graph = new URIImpl(modelId.toString());
				connection.clear(graph);
				StatementCollector collector = new StatementCollector();
				RioRenderer renderer = new RioRenderer(ont, collector, null);
				renderer.render();
				connection.add(collector.getStatements(), graph);
				connection.commit();
				// reset modified flag for abox after successful save
				m.setAboxModified(false);
			} catch (Exception e) {
				connection.rollback();
				throw e;
			} finally {
				if (changes != null) {
					List<OWLOntologyChange> invertedChanges = ReverseChangeGenerator
							.invertChanges(changes);
					if (invertedChanges != null && !invertedChanges.isEmpty()) {
						manager.applyChanges(invertedChanges);
					}
				}
			}
		} finally {
			connection.close();
		}
	}

	private List<OWLOntologyChange> preSaveFileHandler(OWLOntology model) {
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

		public List<OWLOntologyChange> handle(OWLOntology model);
		
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

	@Override
	protected void loadModel(IRI modelId, boolean isOverride)
			throws OWLOntologyCreationException {
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
	protected OWLOntology loadModelABox(IRI modelId)
			throws OWLOntologyCreationException {
		LOG.info("Load model abox: " + modelId + " from database");
		try {
			BigdataSailRepositoryConnection connection = repo.getReadOnlyConnection();
			try {
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
	
	public void dispose() {
		super.dispose();
		try {
			repo.shutDown();
		} catch (RepositoryException e) {
			LOG.error("Failed to shutdown Blazegraph sail.", e);
		}
	}

}
