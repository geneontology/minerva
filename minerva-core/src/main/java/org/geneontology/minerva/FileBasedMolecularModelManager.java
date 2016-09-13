package org.geneontology.minerva;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.geneontology.minerva.util.ReverseChangeGenerator;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.ManchesterSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
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

import com.google.common.base.Optional;

import owltools.gaf.parser.GafObjectsBuilder;
import owltools.graph.OWLGraphWrapper;

/**
 * Layer for retrieving and storing models as OWL files.
 * 
 * @param <METADATA> 
 * @see CoreMolecularModelManager
 */
public class FileBasedMolecularModelManager<METADATA> extends CoreMolecularModelManager<METADATA> {
	
	private static Logger LOG = Logger.getLogger(FileBasedMolecularModelManager.class);

	boolean isPrecomputePropertyClassCombinations = false;
	
	String pathToOWLFiles = "owl-models";
	
	private final String modelIdPrefix;
	
	GafObjectsBuilder builder = new GafObjectsBuilder();
	
	OWLDocumentFormat ontologyFormat = new TurtleDocumentFormat();

	private final List<PreFileSaveHandler> preFileSaveHandlers = new ArrayList<PreFileSaveHandler>();
	private final List<PostLoadOntologyFilter> postLoadOntologyFilters = new ArrayList<PostLoadOntologyFilter>();
	
	/**
	 * @param graph
	 * @param modelIdPrefix
	 * @throws OWLOntologyCreationException
	 */
	public FileBasedMolecularModelManager(OWLGraphWrapper graph,
			String modelIdPrefix) throws OWLOntologyCreationException {
		super(graph);
		this.modelIdPrefix = modelIdPrefix;
	}

	/**
	 * Note this may move to an implementation-specific subclass in future
	 * 
	 * @return path to owl on server
	 */
	public String getPathToOWLFiles() {
		return pathToOWLFiles;
	}
	/**
	 * @param pathToOWLFiles
	 */
	public void setPathToOWLFiles(String pathToOWLFiles) {
		this.pathToOWLFiles = pathToOWLFiles;
	}
	

	private void createImports(OWLOntology ont, OWLOntologyID tboxId, METADATA metadata) throws OWLOntologyCreationException {
		OWLOntologyManager m = ont.getOWLOntologyManager();
		OWLDataFactory f = m.getOWLDataFactory();
		
		// import T-Box
		Optional<IRI> ontologyIRI = tboxId.getOntologyIRI();
		if (ontologyIRI.isPresent()) {
			OWLImportsDeclaration tBoxImportDeclaration = f.getOWLImportsDeclaration(ontologyIRI.get());
			m.applyChange(new AddImport(ont, tBoxImportDeclaration));
		}
		
		// import additional ontologies via IRI
		for (IRI importIRI : additionalImports) {
			OWLImportsDeclaration importDeclaration = f.getOWLImportsDeclaration(importIRI);
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
	public ModelContainer generateBlankModel(METADATA metadata) throws OWLOntologyCreationException {

		// Create an arbitrary unique ID and add it to the system.
		IRI modelId = generateId(modelIdPrefix);
		if (modelMap.containsKey(modelId)) {
			throw new OWLOntologyCreationException("A model already exists for this db: "+modelId);
		}
		LOG.info("Generating blank model for new modelId: "+modelId);

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
		}
		catch (OWLOntologyCreationException exception) {
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
	 * Save all models to disk. The optional annotations may be used to set saved_by and other meta data. 
	 * 
	 * @param annotations
	 * @param metadata
	 * 
	 * @throws OWLOntologyStorageException
	 * @throws OWLOntologyCreationException
	 * @throws IOException 
	 */
	public void saveAllModels(Set<OWLAnnotation> annotations, METADATA metadata) throws OWLOntologyStorageException, OWLOntologyCreationException, IOException {
		for (Entry<IRI, ModelContainer> entry : modelMap.entrySet()) {
			saveModel(entry.getValue(), annotations, metadata);
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
	public void saveModel(ModelContainer m, Set<OWLAnnotation> annotations, METADATA metadata) throws OWLOntologyStorageException, OWLOntologyCreationException, IOException {
		IRI modelId = m.getModelId();
		final OWLOntology ont = m.getAboxOntology();
		final OWLOntologyManager manager = ont.getOWLOntologyManager();
		
		// prelimiary checks for the target file
		File targetFile = getOwlModelFile(modelId).getCanonicalFile();
		if (targetFile.exists()) {
			if (targetFile.isFile() == false) {
				throw new IOException("For modelId: '"+modelId+"', the resulting path is not a file: "+targetFile.getAbsolutePath());
			}
			if (targetFile.canWrite() == false) {
				throw new IOException("For modelId: '"+modelId+"', Cannot write to the file: "+targetFile.getAbsolutePath());
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
			tempFile = File.createTempFile(prefix, ".owl");
		
			// write to a temp file
			synchronized (ont) {
				saveToFile(ont, manager, tempFile, metadata);	
			}
			
			// copy temp file to the finalFile
			FileUtils.copyFile(tempFile, targetFile);
			
			// reset modified flag for abox after successful save
			m.setAboxModified(false);
		}
		finally {
			// delete temp file
			FileUtils.deleteQuietly(tempFile);
		}
	}

	private void saveToFile(final OWLOntology ont, final OWLOntologyManager manager,
			final File outfile, METADATA metadata)
			throws OWLOntologyStorageException {
		
		List<OWLOntologyChange> changes = preSaveFileHandler(ont);
		final IRI outfileIRI = IRI.create(outfile);
		try {
			manager.saveOntology(ont, ontologyFormat, outfileIRI);
		}
		finally {
			if (changes != null) {
				List<OWLOntologyChange> invertedChanges = ReverseChangeGenerator.invertChanges(changes);
				if (invertedChanges != null && !invertedChanges.isEmpty()) {
					manager.applyChanges(invertedChanges);
				}
			}
		}
	}
	
	private List<OWLOntologyChange> preSaveFileHandler(OWLOntology model) {
		List<OWLOntologyChange> allChanges = null;
		for(PreFileSaveHandler handler : preFileSaveHandlers) {
			List<OWLOntologyChange> changes = handler.handle(model);
			if (changes != null && !changes.isEmpty()) {
				if (allChanges == null) {
					allChanges = new ArrayList<OWLOntologyChange>(changes.size());
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
	 * Export the ABox for the given modelId in the default {@link OWLDocumentFormat}.
	 * 
	 * @param model
	 * @return modelContent
	 * @throws OWLOntologyStorageException
	 */
	public String exportModel(ModelContainer model) throws OWLOntologyStorageException {
		return exportModel(model, ontologyFormat);
	}
	
	/**
	 * Export the ABox for the given modelId in the given ontology format.<br>
	 * Warning: The mapping from String to {@link OWLDocumentFormat} does not map every format!
	 * 
	 * @param model
	 * @param format
	 * @return modelContent
	 * @throws OWLOntologyStorageException
	 */
	public String exportModel(ModelContainer model, String format) throws OWLOntologyStorageException {
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
	
	/*
	 * look for all files in the give model folder.
	 */
	private Set<IRI> getModelIdsFromPath(String pathTo) {
		Set<IRI> allModelIds = new HashSet<>();
		File modelFolder = new File(pathTo);
		File[] modelFiles = modelFolder.listFiles(new FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String name) {
				return isLocalUnique(name);
			}
		});
		for (File modelFile : modelFiles) {
			String modelFileName = modelFile.getName();
			String modelIdLong = modelIdPrefix + modelFileName;
			allModelIds.add(IRI.create(modelIdLong));
		}
		return allModelIds;
	}
	
	/**
	 * Retrieve a collection of all file/stored model ids found in the repo.<br>
	 * Note: Models may not be loaded at this point.
	 * 
	 * @return set of modelids.
	 * @throws IOException
	 */
	public Set<IRI> getStoredModelIds() throws IOException {
		return getModelIdsFromPath(this.pathToOWLFiles);
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
	protected void loadModel(IRI modelId, boolean isOverride) throws OWLOntologyCreationException {
		LOG.info("Load model: "+modelId+" from file");
		if (modelMap.containsKey(modelId)) {
			if (!isOverride) {
				throw new OWLOntologyCreationException("Model already exists: "+modelId);
			}
			unlinkModel(modelId);
		}
		File modelFile = getOwlModelFile(modelId);
		IRI sourceIRI = IRI.create(modelFile);
		OWLOntology abox = loadOntologyIRI(sourceIRI, false);
		abox = postLoadFileFilter(abox);
		ModelContainer model = addModel(modelId, abox);
		updateImports(model);
	}

	@Override
	protected OWLOntology loadModelABox(IRI modelId) throws OWLOntologyCreationException {
		File modelFile = getOwlModelFile(modelId);
		IRI sourceIRI = IRI.create(modelFile);
		OWLOntology abox = loadOntologyIRI(sourceIRI, true);
		abox = postLoadFileFilter(abox);
		return abox;
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

	private File getOwlModelFile(IRI modelId) {
		String fileName = StringUtils.replaceOnce(modelId.toString(), modelIdPrefix, "");
		return new File(pathToOWLFiles, fileName).getAbsoluteFile();
	}
}
