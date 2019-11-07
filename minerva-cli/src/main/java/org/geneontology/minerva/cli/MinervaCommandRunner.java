package org.geneontology.minerva.cli;

import com.bigdata.journal.Options;
import com.bigdata.rdf.sail.BigdataSail;
import com.bigdata.rdf.sail.BigdataSailRepository;
import com.bigdata.rdf.sail.BigdataSailRepositoryConnection;
import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.geneontology.minerva.MinervaOWLGraphWrapper;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.json.InferenceProvider;
import org.geneontology.minerva.json.JsonModel;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.legacy.sparql.GPADSPARQLExport;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.server.StartUpTool;
import org.geneontology.minerva.server.inferences.InferenceProviderCreator;
import org.geneontology.minerva.server.validation.MinervaShexValidator;
import org.geneontology.minerva.util.BlazegraphMutationCounter;
import org.geneontology.minerva.validation.Enricher;
import org.geneontology.minerva.validation.ShexValidationReport;
import org.geneontology.minerva.validation.ShexValidator;
import org.geneontology.minerva.validation.ValidationResultSet;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.UpdateExecutionException;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.ManchesterSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

import owltools.cli.JsCommandRunner;
import owltools.cli.Opts;
import owltools.cli.tools.CLIMethod;
import owltools.gaf.GafDocument;
import owltools.gaf.GeneAnnotation;
import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.SimpleEcoMapper;
import owltools.gaf.io.GafWriter;
import owltools.gaf.io.GpadWriter;
import scala.collection.JavaConverters;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class MinervaCommandRunner extends JsCommandRunner {

	private static final Logger LOGGER = Logger.getLogger(MinervaCommandRunner.class);

	@CLIMethod("--dump-owl-models")
	public void modelsToOWL(Opts opts) throws Exception {
		opts.info("[-j|--journal JOURNALFILE] [-f|--folder OWLFILESFOLDER] [-p|--prefix MODELIDPREFIX]",
				"dumps all LEGO models to OWL Turtle files");
		// parameters
		String journalFilePath = null;
		String outputFolder = null;
		String modelIdPrefix = "http://model.geneontology.org/";

		// parse opts
		while (opts.hasOpts()) {
			if (opts.nextEq("-j|--journal")) {
				opts.info("journal file", "Sets the Blazegraph journal file for the database");
				journalFilePath = opts.nextOpt();
			}
			else if (opts.nextEq("-f|--folder")) {
				opts.info("OWL folder", "Sets the output folder the LEGO model files");
				outputFolder = opts.nextOpt();
			}
			else if (opts.nextEq("-p|--prefix")) {
				opts.info("model ID prefix", "Sets the URI prefix for model IDs");
				modelIdPrefix = opts.nextOpt();
			}
			else {
				break;
			}
		}

		// minimal inputs
		if (journalFilePath == null) {
			System.err.println("No journal file was configured.");
			exit(-1);
			return;
		}
		if (outputFolder == null) {
			System.err.println("No output folder was configured.");
			exit(-1);
			return;
		}

		OWLOntology dummy = OWLManager.createOWLOntologyManager().createOntology(IRI.create("http://example.org/dummy"));
		CurieHandler curieHandler = new MappedCurieHandler();
		BlazegraphMolecularModelManager<Void> m3 = new BlazegraphMolecularModelManager<>(dummy, curieHandler, modelIdPrefix, journalFilePath, outputFolder);
		m3.dumpAllStoredModels();
		m3.dispose();
	}

	@CLIMethod("--import-owl-models")
	public void importOWLModels(Opts opts) throws Exception {
		opts.info("[-j|--journal JOURNALFILE] [-f|--folder OWLFILESFOLDER]",
				"import all files in folder to database");
		// parameters
		String journalFilePath = null;
		String inputFolder = null;

		// parse opts
		while (opts.hasOpts()) {
			if (opts.nextEq("-j|--journal")) {
				opts.info("journal file", "Sets the Blazegraph journal file for the database");
				journalFilePath = opts.nextOpt();
			}
			else if (opts.nextEq("-f|--folder")) {
				opts.info("OWL folder", "Sets the folder containing the LEGO model files");
				inputFolder = opts.nextOpt();
			}
			else {
				break;
			}
		}

		// minimal inputs
		if (journalFilePath == null) {
			System.err.println("No journal file was configured.");
			exit(-1);
			return;
		}
		if (inputFolder == null) {
			System.err.println("No input folder was configured.");
			exit(-1);
			return;
		}

		OWLOntology dummy = OWLManager.createOWLOntologyManager().createOntology(IRI.create("http://example.org/dummy"));
		String modelIdPrefix = "http://model.geneontology.org/"; // this will not be used for anything
		CurieHandler curieHandler = new MappedCurieHandler();
		BlazegraphMolecularModelManager<Void> m3 = new BlazegraphMolecularModelManager<>(dummy, curieHandler, modelIdPrefix, journalFilePath, null);
		for (File file : FileUtils.listFiles(new File(inputFolder), null, true)) {
			LOGGER.info("Loading " + file);
			m3.importModelToDatabase(file, true);
		}
		m3.dispose();
	}

	@CLIMethod("--sparql-update")
	public void sparqlUpdate(Opts opts) throws OWLOntologyCreationException, IOException, RepositoryException, MalformedQueryException, UpdateExecutionException {
		opts.info("[-j|--journal JOURNALFILE] [-f|--file SPARQL UPDATE FILE]",
				"apply SPARQL update to database");
		String journalFilePath = null;
		String updateFile = null;

		// parse opts
		while (opts.hasOpts()) {
			if (opts.nextEq("-j|--journal")) {
				opts.info("journal file", "Sets the Blazegraph journal file for the database");
				journalFilePath = opts.nextOpt();
			}
			else if (opts.nextEq("-f|--file")) {
				opts.info("OWL folder", "Sets the file containing a SPARQL update");
				updateFile = opts.nextOpt();
			}
			else {
				break;
			}
		}

		// minimal inputs
		if (journalFilePath == null) {
			System.err.println("No journal file was configured.");
			exit(-1);
			return;
		}
		if (updateFile == null) {
			System.err.println("No update file was configured.");
			exit(-1);
			return;
		}

		String update = FileUtils.readFileToString(new File(updateFile), StandardCharsets.UTF_8);
		Properties properties = new Properties();
		properties.load(this.getClass().getResourceAsStream("/org/geneontology/minerva/blazegraph.properties"));
		properties.setProperty(Options.FILE, journalFilePath);
		BigdataSail sail = new BigdataSail(properties);
		BigdataSailRepository repository = new BigdataSailRepository(sail);
		repository.initialize();
		BigdataSailRepositoryConnection conn = repository.getUnisolatedConnection();
		BlazegraphMutationCounter counter = new BlazegraphMutationCounter();
		conn.addChangeLog(counter);
		conn.prepareUpdate(QueryLanguage.SPARQL, update).execute();
		int changes = counter.mutationCount();
		conn.removeChangeLog(counter);
		System.out.println("\nApplied " + changes + " changes");
		conn.close();
	}

	@CLIMethod("--owl-lego-to-json")
	public void owl2LegoJson(Opts opts) throws Exception {
		opts.info("[-o JSONFILE] [-i OWLFILE] [--pretty-json] [--compact-json]",
				"converts the LEGO subset of OWL to Minerva-JSON");
		// parameters
		String input = null;
		String output = null;
		boolean usePretty = true;

		// parse opts
		while (opts.hasOpts()) {
			if (opts.nextEq("-o|--output")) {
				opts.info("output", "Sets the output file for the json");
				output = opts.nextOpt();
			}
			else if (opts.nextEq("-i|--input")) {
				opts.info("input", "Sets the input file for the model");
				input = opts.nextOpt();
			}
			else if (opts.nextEq("--pretty-json")) {
				opts.info("", "pretty print the output json");
				usePretty = true;
			}
			else if (opts.nextEq("--compact-json")) {
				opts.info("", "compact print the output json");
				usePretty = false;
			}
			else {
				break;
			}
		}

		// minimal inputs
		if (input == null) {
			System.err.println("No input model was configured.");
			exit(-1);
			return;
		}
		if (output == null) {
			System.err.println("No output file was configured.");
			exit(-1);
			return;
		}

		// configuration
		CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
		GsonBuilder gsonBuilder = new GsonBuilder();
		if (usePretty) {
			gsonBuilder.setPrettyPrinting();
		}
		Gson gson = gsonBuilder.create();

		// process each model
		if (LOGGER.isInfoEnabled()) {
			LOGGER.info("Loading model from file: "+input);
		}
		OWLOntology model = null;
		final JsonModel jsonModel;
		try {
			// load model
			model = pw.parseOWL(IRI.create(new File(input).getCanonicalFile()));
			InferenceProvider inferenceProvider = null; // TODO decide if we need reasoning
			String modelId = null;
			Optional<IRI> ontologyIRI = model.getOntologyID().getOntologyIRI();
			if (ontologyIRI.isPresent()) {
				modelId = curieHandler.getCuri(ontologyIRI.get());
			}

			// render json
			final MolecularModelJsonRenderer renderer = new MolecularModelJsonRenderer(modelId, model, inferenceProvider, curieHandler);
			jsonModel = renderer.renderModel();
		}
		finally {
			if (model != null) {
				pw.getManager().removeOntology(model);
				model = null;
			}
		}

		// save as json string
		final String json = gson.toJson(jsonModel);
		final File outputFile = new File(output).getCanonicalFile();
		try (OutputStream outputStream = new FileOutputStream(outputFile)) {
			if (LOGGER.isInfoEnabled()) {
				LOGGER.info("Saving json to file: "+outputFile);
			}
			IOUtils.write(json, outputStream);
		}
	}

	/**
	 * Output GPAD files via inference+SPARQL
	 * @param opts
	 * @throws Exception
	 */
	@CLIMethod("--lego-to-gpad-sparql")
	public void legoToAnnotationsSPARQL(Opts opts) throws Exception {
		String modelIdPrefix = "http://model.geneontology.org/";
		String modelIdcurie = "gomodel";
		String inputDB = "blazegraph.jnl";
		String gpadOutputFolder = null;
		String ontologyIRI = "http://purl.obolibrary.org/obo/go/extensions/go-lego.owl";
		while (opts.hasOpts()) {
			if (opts.nextEq("-i|--input")) {
				inputDB = opts.nextOpt();
			}
			else if (opts.nextEq("--gpad-output")) {
				gpadOutputFolder = opts.nextOpt();
			}
			else if (opts.nextEq("--model-id-prefix")) {
				modelIdPrefix = opts.nextOpt();
			}
			else if (opts.nextEq("--model-id-curie")) {
				modelIdcurie = opts.nextOpt();
			}
			else if (opts.nextEq("--ontology")) {
				ontologyIRI = opts.nextOpt();
			}
			else {
				break;
			}
		}
		OWLOntology ontology = OWLManager.createOWLOntologyManager().loadOntology(IRI.create(ontologyIRI));
		CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap(modelIdcurie, modelIdPrefix));
		CurieHandler curieHandler = new MappedCurieHandler(DefaultCurieHandler.loadDefaultMappings(), localMappings);
		BlazegraphMolecularModelManager<Void> m3 = new BlazegraphMolecularModelManager<>(ontology, curieHandler, modelIdPrefix, inputDB, null);
		final String immutableModelIdPrefix = modelIdPrefix;
		final String immutableGpadOutputFolder = gpadOutputFolder;
		m3.getAvailableModelIds().stream().parallel().forEach(modelIRI -> {
			try {
				String gpad = new GPADSPARQLExport(curieHandler, m3.getLegacyRelationShorthandIndex(), m3.getTboxShorthandIndex(), m3.getDoNotAnnotateSubset()).exportGPAD(m3.createInferredModel(modelIRI));
				String fileName = StringUtils.replaceOnce(modelIRI.toString(), immutableModelIdPrefix, "") + ".gpad";
				Writer writer = new OutputStreamWriter(new FileOutputStream(Paths.get(immutableGpadOutputFolder, fileName).toFile()), StandardCharsets.UTF_8);
				writer.write(gpad);
				writer.close();
			} catch (InconsistentOntologyException e) {
				LOGGER.error("Inconsistent ontology: " + modelIRI);
			} catch (IOException e) {
				LOGGER.error("Couldn't export GPAD for: " + modelIRI, e);
			}
		});
		m3.dispose();
	}


	static void sortAnnotations(GafDocument annotations) {
		Collections.sort(annotations.getGeneAnnotations(), new Comparator<GeneAnnotation>() {
			@Override
			public int compare(GeneAnnotation a1, GeneAnnotation a2) {
				return a1.toString().compareTo(a2.toString());
			}
		});
	}

	
	/**
	 * Output whether each model provided is logically consistent or not
	 * example parameters for invocation:
	 *   --owlcheck-go-cams -i /GitHub/GO_Shapes/test_ttl/go_cams/should_pass/ -c ./catalog-no-import.xml
	 * @param opts
	 * @throws Exception
	 */
	@CLIMethod("--owlcheck-go-cams")
	public void owlCheckGoCams(Opts opts) throws Exception {
		Logger LOG = Logger.getLogger(BlazegraphMolecularModelManager.class);
		LOG.setLevel(Level.ERROR);
		LOGGER.setLevel(Level.INFO);
		String catalog = null;
		String modelIdPrefix = "http://model.geneontology.org/";
		String modelIdcurie = "gomodel";
		String inputDB = "blazegraph.jnl";
		String explanationOutputFile = null;
		String basicOutputFile = null;
		String ontologyIRI = "http://purl.obolibrary.org/obo/go/extensions/go-lego.owl";
		String shexFileUrl = "https://raw.githubusercontent.com/geneontology/go-shapes/master/shapes/go-cam-shapes.shex";
		String goshapemapFileUrl = "https://raw.githubusercontent.com/geneontology/go-shapes/master/shapes/go-cam-shapes.shapeMap";
		String shexpath = null;
		String shapemappath = null;
		String input = null;
		boolean checkShex = false;
		Map<String, String> modelid_filename = new HashMap<String, String>();
		while (opts.hasOpts()) {
			if (opts.nextEq("-i|--input")) {
				input = opts.nextOpt();
			}
			else if (opts.nextEq("--shex")) {
				checkShex = true;
			}
			else if (opts.nextEq("--output-file")) {
				basicOutputFile = opts.nextOpt();
			}
			else if (opts.nextEq("--explanation-output-file")) {
				explanationOutputFile = opts.nextOpt();
			}
			else if (opts.nextEq("--model-id-prefix")) {
				modelIdPrefix = opts.nextOpt();
			}
			else if (opts.nextEq("--model-id-curie")) {
				modelIdcurie = opts.nextOpt();
			}
			else if (opts.nextEq("--ontology")) {
				ontologyIRI = opts.nextOpt();
			}
			else if (opts.nextEq("-c|--catalog")) {
				catalog = opts.nextOpt();
			}
			else {
				break;
			}
		}
		if(basicOutputFile==null) {
			LOGGER.error("please specific an output file with --output-file ");
			System.exit(-1);
		}
		if(input==null) {
			LOGGER.error("please provide an input file - either a directory of ttl files or a blazegraph journal");
			System.exit(-1);
		}
		if(input.endsWith(".jnl")) {
			inputDB = input;
			LOGGER.info("loaded blazegraph journal: "+input);
		}else {
			LOGGER.info("no journal found, trying as directory: "+input);
			File i = new File(input);
			if(i.exists()) {
				//remove anything that existed earlier
				File bgdb = new File(inputDB);
				if(bgdb.exists()) {
					bgdb.delete();
				}
				//load everything into a bg journal
				OWLOntology dummy = OWLManager.createOWLOntologyManager().createOntology(IRI.create("http://example.org/dummy"));
				CurieHandler curieHandler = new MappedCurieHandler();
				BlazegraphMolecularModelManager<Void> m3 = new BlazegraphMolecularModelManager<>(dummy, curieHandler, modelIdPrefix, inputDB, null);
				if(i.isDirectory()) {
					FileUtils.listFiles(i, null, true).parallelStream().parallel().forEach(file-> {
						//for (File file : 
						//m3.getAvailableModelIds().stream().parallel().forEach(modelIRI -> {
						if(file.getName().endsWith(".ttl")||file.getName().endsWith("owl")) {
							LOGGER.info("Loading " + file);
							try {
								String modeluri = m3.importModelToDatabase(file, true);
								modelid_filename.put(modeluri, file.getName());
							} catch (OWLOntologyCreationException | RepositoryException | RDFParseException
									| RDFHandlerException | IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						} 
					});
				}else {
					LOGGER.info("Loading " + i);
					m3.importModelToDatabase(i, true);
				}
				LOGGER.info("loaded files into blazegraph journal: "+input);
				m3.dispose();
			}
		}
		LOGGER.info("loading tbox ontology: "+ontologyIRI);
		OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();
		if(catalog!=null) {
			LOGGER.info("using catalog: "+catalog);
			ontman.setIRIMappers(Sets.newHashSet(new owltools.io.CatalogXmlIRIMapper(catalog)));
		}else {
			LOGGER.info("no catalog, resolving all ontology uris directly");
		}
		OWLOntology tbox_ontology = ontman.loadOntology(IRI.create(ontologyIRI));
		tbox_ontology = StartUpTool.forceMergeImports(tbox_ontology, tbox_ontology.getImports());
		LOGGER.info("ontology axioms loaded: "+tbox_ontology.getAxiomCount());
		LOGGER.info("building model manager and structural reasoner");
		CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap(modelIdcurie, modelIdPrefix));
		CurieHandler curieHandler = new MappedCurieHandler(DefaultCurieHandler.loadDefaultMappings(), localMappings);
		UndoAwareMolecularModelManager m3 = new UndoAwareMolecularModelManager(tbox_ontology, curieHandler, modelIdPrefix, inputDB, null);
		String reasonerOpt = "arachne"; 
		LOGGER.info("tbox reasoner for shex "+m3.getTbox_reasoner().getReasonerName());		
		if(shexpath==null) {
			//fall back on downloading from shapes repo
			URL shex_schema_url = new URL(shexFileUrl);
			shexpath = "./go-cam-schema.shex";
			File shex_schema_file = new File(shexpath);
			org.apache.commons.io.FileUtils.copyURLToFile(shex_schema_url, shex_schema_file);			
			System.err.println("-s .No shex schema provided, using: "+shexFileUrl);
		}
		if(shapemappath==null) {
			URL shex_map_url = new URL(goshapemapFileUrl);
			shapemappath = "./go-cam-shapes.shapeMap";
			File shex_map_file = new File(shapemappath);
			org.apache.commons.io.FileUtils.copyURLToFile(shex_map_url, shex_map_file);
			System.err.println("-m .No shape map file provided, using: "+goshapemapFileUrl);
		}
		MinervaShexValidator shex = new MinervaShexValidator(shexpath, shapemappath, curieHandler, m3.getTbox_reasoner());
		if(checkShex) {
			if(checkShex) {
				shex.setActive(true);
			}else {
				shex.setActive(false);
			}
		}
		LOGGER.info("Building OWL inference provider: "+reasonerOpt);
		InferenceProviderCreator ipc = StartUpTool.createInferenceProviderCreator(reasonerOpt, m3, shex);
		LOGGER.info("Validating models: "+reasonerOpt);

		FileWriter basic_output = new FileWriter(basicOutputFile);
		try {
			basic_output.write("filename\tmodel_id\tOWL_consistent\tshex_valid\n");
			final boolean shex_output = checkShex;
			m3.getAvailableModelIds().stream().forEach(modelIRI -> {
				try {
					String filename = modelid_filename.get(modelIRI.toString());
					boolean isConsistent = true;
					boolean isConformant = true;
					LOGGER.info("processing "+filename+"\t"+modelIRI);
					ModelContainer mc = m3.getModel(modelIRI);		
					//not reporting OWL errors ?
					InferenceProvider ip = ipc.create(mc);
					isConsistent = ip.isConsistent();
					if(shex_output) {
						ValidationResultSet validations = ip.getValidation_results();
						isConformant = validations.allConformant();	
						LOGGER.info(filename+"\t"+modelIRI+"\tOWL:"+isConsistent+"\tshex:"+isConformant);
						basic_output.write(filename+"\t"+modelIRI+"\t"+isConsistent+"\t"+isConformant+"\n");
					}else {
						LOGGER.info(filename+"\t"+modelIRI+"\tOWL:"+isConsistent+"\tshex:not checked");
						basic_output.write(filename+"\t"+modelIRI+"\t"+isConsistent+"\tnot checked\n");						
					}
				} catch (InconsistentOntologyException e) {
					LOGGER.error("Inconsistent model: " + modelIRI);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
			});
			m3.dispose();
		}finally{
			basic_output.close();
		}
	}


	/**
	 * Will test a go-cam model or directory of models against a shex schema and shape map to test conformance
	 * Example invocation: --validate-go-cams -s /Users/bgood/Documents/GitHub/GO_Shapes/shapes/go-cam-shapes.shex -m /Users/bgood/Documents/GitHub/GO_Shapes/shapes/go-cam-shapes.shapeMap -f /Users/bgood/Documents/GitHub/GO_Shapes/test_ttl/go_cams/should_pass/ -e -r /Users/bgood/Desktop/shapely_report.txt
	 * @param opts
	 * @throws Exception
	 */
	@CLIMethod("--validate-go-cams")
	public void validateGoCams(Opts opts) throws Exception {
		String url_for_tbox = "http://purl.obolibrary.org/obo/go/extensions/go-lego.owl";
		String shexFileUrl = "https://raw.githubusercontent.com/geneontology/go-shapes/master/shapes/go-cam-shapes.shex";
		String goshapemapFileUrl = "https://raw.githubusercontent.com/geneontology/go-shapes/master/shapes/go-cam-shapes.shapeMap";

		ShexValidator validator = null;
		String shexpath = null;//"../shapes/go-cam-shapes.shex";
		String shapemappath = null;
		String model_file = null;//"../test_ttl/go_cams/should_pass/typed_reactome-homosapiens-Acetylation.ttl";
		String report_file = null;
		boolean addSuperClasses = true;//this always needs to be on unless we have a major shex refactor
		boolean addSuperClassesLocal = false;
		boolean travisMode = false; 
		boolean shouldPass = true;
		String extra_endpoint = null;
		String catalog = null;
		Map<String, Model> name_model = new HashMap<String, Model>();

		while (opts.hasOpts()) {
			if(opts.nextEq("-travis")) {
				travisMode = true;
			}
			else if (opts.nextEq("-f|--file")) {
				model_file = opts.nextOpt();
				name_model = Enricher.loadRDF(model_file);
			}
			else if (opts.nextEq("-shouldfail")) {
				shouldPass = false;
			}
			else if (opts.nextEq("-s|--shexpath")) {
				shexpath = opts.nextOpt();
			}
			else if(opts.nextEq("-m|--shapemap")) { 
				shapemappath = opts.nextOpt();
			}
			else if(opts.nextEq("-r|--report")) { 
				report_file = opts.nextOpt();
			}
			else if(opts.nextEq("-localtbox")) { 
				addSuperClassesLocal = true; //this will download the beast unless there is a catalogue file
			}
			else if(opts.nextEq("-extra_endpoint")) { 
				extra_endpoint = opts.nextOpt();
			}
			else if (opts.nextEq("-c|--catalog")) {
				catalog = opts.nextOpt();
			}
			else {
				break;
			}
		}
		if(shexpath==null) {
			//fall back on downloading from shapes repo
			URL shex_schema_url = new URL(shexFileUrl);
			shexpath = "./go-cam-schema.shex";
			File shex_schema_file = new File(shexpath);
			org.apache.commons.io.FileUtils.copyURLToFile(shex_schema_url, shex_schema_file);			
			System.err.println("-s .No shex schema provided, using: "+shexFileUrl);
		}
		if(shapemappath==null) {
			URL shex_map_url = new URL(goshapemapFileUrl);
			shapemappath = "./go-cam-shapes.shapeMap";
			File shex_map_file = new File(shapemappath);
			org.apache.commons.io.FileUtils.copyURLToFile(shex_map_url, shex_map_file);
			System.err.println("-m .No shape map file provided, using: "+goshapemapFileUrl);
		}
		if(report_file==null) {
			report_file = "./shex_report.txt";
			System.err.println("-r .No report file specified, using "+report_file);
		}
		if(!addSuperClassesLocal) {
			System.out.println("Using RDF endpoint for super class expansion: "+Enricher.go_endpoint);
			System.out.println("add -localtbox to retrieve the ontology from http://purl.obolibrary.org/obo/go/extensions/go-lego.owl "
					+ "and do the inferences locally.  This is slower and uses much more memory, but at least you know what you are getting. "
					+ "You can use a catalog file by adding -c yourcatalog.xml "
					+ "- catalog files allow you to map URIs to local files to change "
					+ "what they resolve to and to reduce network traffic time.");
		}
		//requirements
		if(model_file==null) {
			System.err.println("-f .No go-cam file or directory provided to validate.");
			exit(-1);
		}else {
			FileWriter w = new FileWriter(report_file);
			int good = 0; int bad = 0;
			Enricher enrich = new Enricher(extra_endpoint, null);
			if(addSuperClassesLocal) {
				OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();	
				if(catalog!=null) {
					System.out.println("using catalog: "+catalog);
					ontman.setIRIMappers(Sets.newHashSet(new owltools.io.CatalogXmlIRIMapper(catalog)));
				}else {
					System.out.println("no catalog, resolving all import ontology uris directly, be patient...");
				}				
				OWLOntology tbox_ontology = ontman.loadOntology(IRI.create(url_for_tbox));
				tbox_ontology = StartUpTool.forceMergeImports(tbox_ontology, tbox_ontology.getImports());
				System.out.println("ontology axioms loaded: "+tbox_ontology.getAxiomCount());
				System.out.println("building model manager");

				System.out.println("done loading, building structural reasoner");
				OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
				OWLReasoner tbox_reasoner = reasonerFactory.createReasoner(tbox_ontology);
				validator = new ShexValidator(shexpath, shapemappath, tbox_reasoner);
				enrich = new Enricher(null, tbox_reasoner);
			}else {
				validator = new ShexValidator(shexpath, shapemappath, null);
			}
			for(String name : name_model.keySet()) {
				Model test_model = name_model.get(name);
				if(addSuperClasses) {
					test_model = enrich.enrichSuperClasses(test_model);
				}
				if(validator.GoQueryMap!=null){
					boolean stream_output = false;
					ShexValidationReport r = validator.runShapeMapValidation(test_model, stream_output);
					System.out.println(name+" conformant:"+r.isConformant());
					w.write(name+"\t");
					if(!r.isConformant()) {
						w.write("invalid\n");
						bad++;
						if(travisMode&&(shouldPass)) {
							System.out.println(name+" should have validated but did not "+r.getAsText());
							System.exit(-1);
						}
					}else {
						good++;
						w.write("valid\n");
						if(travisMode&&(!shouldPass)) {
							System.out.println(name+" should NOT have validated but did ");
							System.exit(-1);
						}
					}
				}
			}
			w.close();
			System.out.println("input: "+model_file+" total:"+name_model.size()+" Good:"+good+" Bad:"+bad);
		}
	}
}
