package org.geneontology.minerva;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.coode.owlapi.manchesterowlsyntax.ManchesterOWLSyntaxOntologyFormat;
import org.geneontology.minerva.util.ReverseChangeGenerator;
import org.semanticweb.owlapi.io.OWLFunctionalSyntaxOntologyFormat;
import org.semanticweb.owlapi.io.OWLXMLOntologyFormat;
import org.semanticweb.owlapi.io.RDFXMLOntologyFormat;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyAlreadyExistsException;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyDocumentAlreadyExistsException;
import org.semanticweb.owlapi.model.OWLOntologyFormat;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

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
	
	private final String modelIdLongFormPrefix;
	private final String modelIdShortFormPrefix;
	
	GafObjectsBuilder builder = new GafObjectsBuilder();
	// WARNING: Do *NOT* switch to functional syntax until the OWL-API has fixed a bug.
	OWLOntologyFormat ontologyFormat = new ManchesterOWLSyntaxOntologyFormat();

	/**
	 * @param graph
	 * @param rf
	 * @param modelIdLongFormPrefix
	 * @param modelIdShortFormPrefix 
	 * @throws OWLOntologyCreationException
	 */
	public FileBasedMolecularModelManager(OWLGraphWrapper graph, OWLReasonerFactory rf,
			String modelIdLongFormPrefix, String modelIdShortFormPrefix) throws OWLOntologyCreationException {
		super(graph, rf);
		this.modelIdLongFormPrefix = modelIdLongFormPrefix;
		this.modelIdShortFormPrefix = modelIdShortFormPrefix;
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
		OWLImportsDeclaration tBoxImportDeclaration = f.getOWLImportsDeclaration(tboxId.getOntologyIRI());
		m.applyChange(new AddImport(ont, tBoxImportDeclaration));
		
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
		String modelId = generateId(modelIdLongFormPrefix);
		if (modelMap.containsKey(modelId)) {
			throw new OWLOntologyCreationException("A model already exists for this db: "+modelId);
		}
		LOG.info("Generating blank model for new modelId: "+modelId);

		// create empty ontology, use model id as ontology IRI
		final OWLOntologyManager m = graph.getManager();
		IRI aBoxIRI = IRI.create(modelId); // model id is already a long form IRI
		final OWLOntology tbox = graph.getSourceOntology();
		OWLOntology abox = null;
		ModelContainer model = null;
		try {
			abox = m.createOntology(aBoxIRI);
	
			// add imports to T-Box and additional ontologies via IRI
			createImports(abox, tbox.getOntologyID(), metadata);
			
			// generate model
			model = new ModelContainer(modelId, tbox, abox, rf);
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
		for (Entry<String, ModelContainer> entry : modelMap.entrySet()) {
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
		String modelId = m.getModelId();
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
			tempFile = File.createTempFile(modelId, ".owl");
		
			// write to a temp file
			synchronized (ont) {
				saveToFile(ont, manager, tempFile, annotations, metadata);	
			}
			
			// copy temp file to the finalFile
			FileUtils.copyFile(tempFile, targetFile);
		}
		finally {
			// delete temp file
			FileUtils.deleteQuietly(tempFile);
		}
	}

	private void saveToFile(final OWLOntology ont, final OWLOntologyManager manager,
			final File outfile, final Set<OWLAnnotation> annotations, METADATA metadata)
			throws OWLOntologyStorageException {
		// check that the annotations contain relevant meta data
		final Set<OWLAxiom> metadataAxioms = new HashSet<OWLAxiom>();
		if (annotations != null) {
//			for (Pair<String,String> pair : annotations) {
				// TODO saved by
//			}
		}
		// TODO save date?
		
		List<OWLOntologyChange> changes = null;
		final IRI outfileIRI = IRI.create(outfile);
		try {
			if (metadataAxioms.isEmpty() == false) {
				changes = manager.addAxioms(ont, metadataAxioms);
			}
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
	
	/**
	 * Export the ABox for the given modelId in the default {@link OWLOntologyFormat}.
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
	 * Warning: The mapping from String to {@link OWLOntologyFormat} does not map every format!
	 * 
	 * @param model
	 * @param format
	 * @return modelContent
	 * @throws OWLOntologyStorageException
	 */
	public String exportModel(ModelContainer model, String format) throws OWLOntologyStorageException {
		OWLOntologyFormat ontologyFormat = getOWLOntologyFormat(format);
		if (ontologyFormat == null) {
			ontologyFormat = this.ontologyFormat;
		}
		return exportModel(model, ontologyFormat);
	}
	
	private OWLOntologyFormat getOWLOntologyFormat(String fmt) {
		OWLOntologyFormat ofmt = null;
		if (fmt != null) {
			fmt = fmt.toLowerCase();
			if (fmt.equals("rdfxml"))
				ofmt = new RDFXMLOntologyFormat();
			else if (fmt.equals("owl"))
				ofmt = new RDFXMLOntologyFormat();
			else if (fmt.equals("rdf"))
				ofmt = new RDFXMLOntologyFormat();
			else if (fmt.equals("owx"))
				ofmt = new OWLXMLOntologyFormat();
			else if (fmt.equals("owf"))
				ofmt = new OWLFunctionalSyntaxOntologyFormat();
			else if (fmt.equals("owm"))
				ofmt = new ManchesterOWLSyntaxOntologyFormat();
		}
		return ofmt;
	}
	
	/*
	 * look for all files in the give model folder.
	 */
	private Map<String, String> getModelIdsFromPath(String pathTo) {
		Map<String, String> allModelIds = new HashMap<String, String>();
		File modelFolder = new File(pathTo);
		File[] modelFiles = modelFolder.listFiles(new FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String name) {
				return isLocalUnique(name);
			}
		});
		for (File modelFile : modelFiles) {
			String modelFileName = modelFile.getName();
			String modelIdShort = modelIdShortFormPrefix + modelFileName;
			String modelIdLong = modelIdLongFormPrefix + modelFileName;
			allModelIds.put(modelIdLong, modelIdShort);
		}
		return allModelIds;
	}
	
	public String getLongFormModelId(String id) {
		if (id != null) {
			id = StringUtils.replaceOnce(id, modelIdShortFormPrefix, modelIdLongFormPrefix);
		}
		return id;
	}
	
	public String getShortFormModelId(String id) {
		if (id != null) {
			id = StringUtils.replaceOnce(id, modelIdLongFormPrefix, modelIdShortFormPrefix);
		}
		return id;
	}
	
	/**
	 * Retrieve a collection of all file/stored model ids found in the repo.<br>
	 * Note: Models may not be loaded at this point.
	 * 
	 * @return set of modelids.
	 * @throws IOException
	 */
	public Map<String, String> getStoredModelIds() throws IOException {
		return getModelIdsFromPath(this.pathToOWLFiles);
	}
	
	/**
	 * Retrieve all model ids currently in memory in long and short form.<br>
	 * 
	 * @return set of modelids.
	 * @throws IOException
	 */
	public Map<String, String> getCurrentModelIds() throws IOException {
		Map<String,String> allModelIds = new HashMap<String,String>();
		// add all model ids currently in memory
		for(String id : modelMap.keySet()) {
			allModelIds.put(id, StringUtils.replaceOnce(id, modelIdLongFormPrefix, modelIdShortFormPrefix));
		}
		return allModelIds;
	}

	/**
	 * Retrieve a collection of all available model ids.<br>
	 * Note: Models may not be loaded at this point.
	 * 
	 * @return set of modelids.
	 * @throws IOException
	 */
	public Map<String, String> getAvailableModelIds() throws IOException {
		Map<String, String> allModelIds = new HashMap<String, String>();
		allModelIds.putAll(this.getStoredModelIds());
		allModelIds.putAll(this.getCurrentModelIds());
		return allModelIds;
	}

	@Override
	protected void loadModel(String modelId, boolean isOverride) throws OWLOntologyCreationException {
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
		ModelContainer model = addModel(modelId, abox);
		updateImports(model);
	}

	@Override
	protected OWLOntology loadModelABox(String modelId) throws OWLOntologyCreationException {
		File modelFile = getOwlModelFile(modelId);
		IRI sourceIRI = IRI.create(modelFile);
		OWLOntology abox = loadOntologyIRI(sourceIRI, true);
		return abox;
	}

	private File getOwlModelFile(String modelId) {
		String fileName = StringUtils.replaceOnce(modelId, modelIdLongFormPrefix, "");
		return new File(pathToOWLFiles, fileName).getAbsoluteFile();
	}
}
