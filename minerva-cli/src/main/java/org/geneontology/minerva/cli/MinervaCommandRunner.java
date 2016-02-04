package org.geneontology.minerva.cli;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.geneontology.minerva.GafToLegoIndividualTranslator;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.legacy.LegoToGeneAnnotationTranslator;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.ManchesterSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationValueVisitorEx;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import com.google.common.base.Optional;

import owltools.cli.JsCommandRunner;
import owltools.cli.Opts;
import owltools.cli.tools.CLIMethod;
import owltools.gaf.BioentityDocument;
import owltools.gaf.GafDocument;
import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.SimpleEcoMapper;
import owltools.gaf.io.GpadWriter;
import owltools.graph.OWLGraphWrapper;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

public class MinervaCommandRunner extends JsCommandRunner {

	/**
	 * Translate the GeneAnnotations into a lego all individual OWL representation.
	 * 
	 * Will merge the source ontology into the graph by default
	 * 
	 * @param opts
	 * @throws Exception
	 */
	@CLIMethod("--gaf-lego-indivduals")
	public void gaf2LegoIndivduals(Opts opts) throws Exception {
		boolean addLineNumber = false;
		boolean merge = true;
		boolean minimize = false;
		String output = null;
		CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
		OWLDocumentFormat format = new RDFXMLDocumentFormat();
		while (opts.hasOpts()) {
			if (opts.nextEq("-o|--output")) {
				output = opts.nextOpt();
			}
			else if (opts.nextEq("--format")) {
				String formatString = opts.nextOpt();
				if ("manchester".equalsIgnoreCase(formatString)) {
					format = new ManchesterSyntaxDocumentFormat();
				}
				else if ("functional".equalsIgnoreCase(formatString)) {
					format = new FunctionalSyntaxDocumentFormat();
				}
			}
			else if (opts.nextEq("--add-line-number")) {
				addLineNumber = true;
			}
			else if (opts.nextEq("--skip-merge")) {
				merge = false;
			}
			else if (opts.nextEq("-m|--minimize")) {
				minimize = true;
			}
			else {
				break;
			}
		}
		if (g != null && gafdoc != null && output != null) {
			GafToLegoIndividualTranslator tr = new GafToLegoIndividualTranslator(g, curieHandler, addLineNumber);
			OWLOntology lego = tr.translate(gafdoc);
			
			if (merge) {
				new OWLGraphWrapper(lego).mergeImportClosure(true);	
			}
			if (minimize) {
				final OWLOntologyManager m = lego.getOWLOntologyManager();
				
				SyntacticLocalityModuleExtractor sme = new SyntacticLocalityModuleExtractor(m, lego, ModuleType.BOT);
				Set<OWLEntity> sig = new HashSet<OWLEntity>(lego.getIndividualsInSignature());
				Set<OWLAxiom> moduleAxioms = sme.extract(sig);
				
				OWLOntology module = m.createOntology(IRI.generateDocumentIRI());
				m.addAxioms(module, moduleAxioms);
				lego = module;
			}
			
			OWLOntologyManager manager = lego.getOWLOntologyManager();
			OutputStream outputStream = null;
			try {
				outputStream = new FileOutputStream(output);
				manager.saveOntology(lego, format, outputStream);
			}
			finally {
				IOUtils.closeQuietly(outputStream);
			}
		}
		else {
			if (output == null) {
				System.err.println("No output file was specified.");
			}
			if (g == null) {
				System.err.println("No graph available for gaf-run-check.");
			}
			if (gafdoc == null) {
				System.err.println("No loaded gaf available for gaf-run-check.");
			}
			exit(-1);
			return;
		}
	}
	
	@CLIMethod("--lego-to-gpad")
	public void legoToAnnotations(Opts opts) throws Exception {
		String modelIdPrefix = "http://model.geneontology.org/";
		String modelIdcurie = "gomodel";
		String inputFolder = null;
		String outputFolder = null;
		List<String> defaultRefs = null;
		boolean addLegoModelId = true;
		while (opts.hasOpts()) {
			if (opts.nextEq("-i|--input")) {
				inputFolder = opts.nextOpt();
			}
			else if (opts.nextEq("-o|--output")) {
				outputFolder = opts.nextOpt();
			}
			else if (opts.nextEq("--add-default-ref")) {
				if (defaultRefs == null) {
					defaultRefs = new ArrayList<String>();
				}
				defaultRefs.add(opts.nextOpt());
			}
			else if (opts.nextEq("--remove-lego-model-ids")) {
				addLegoModelId = false;
			}
			else if (opts.nextEq("--model-id-prefix")) {
				modelIdPrefix = opts.nextOpt();
			}
			else if (opts.nextEq("--model-id-curie")) {
				modelIdcurie = opts.nextOpt();
			}
			else {
				break;
			}
		}
		// create curie handler
		CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap(modelIdcurie, modelIdPrefix));
		CurieHandler curieHandler = new MappedCurieHandler(DefaultCurieHandler.getMappings(), localMappings);
		
		ExternalLookupService lookup= null;

		SimpleEcoMapper mapper = EcoMapperFactory.createSimple();
		LegoToGeneAnnotationTranslator translator = new LegoToGeneAnnotationTranslator(g.getSourceOntology(), curieHandler, mapper);
		Set<String> modelStates = new HashSet<>();
		Map<String, GafDocument> typedAnnotations = new HashMap<>();
		Map<String, BioentityDocument> typedEntities = new HashMap<>();

		File inputFile = new File(inputFolder).getCanonicalFile();
		OWLOntologyManager m = g.getManager();
		if (inputFile.isDirectory()) {
			File[] files = inputFile.listFiles(new FilenameFilter() {

				@Override
				public boolean accept(File dir, String name) {
					return StringUtils.isAlphanumeric(name);
				}
			});
			for (File file : files) {
				OWLOntology model = m.loadOntology(IRI.create(file));
				
				// get curie
				String modelCurie = getModelCurie(model, curieHandler, null);
				
				List<String> addtitionalRefs = handleRefs(defaultRefs, addLegoModelId, modelCurie);
				
				// get state
				final String modelState = getModelState(model, "unknown");
				modelStates.add(modelState);
				
				// get appropriate annotation containers
				GafDocument annotations = typedAnnotations.get(modelState);
				if (annotations == null) {
					annotations = new GafDocument(null, null);
					typedAnnotations.put(modelState, annotations);
				}
				BioentityDocument entities = typedEntities.get(modelState);
				if (entities == null) {
					entities = new BioentityDocument(null);
				}
				
				// translate
				translator.translate(model, lookup, annotations, entities, addtitionalRefs);	
			}
		}

		for(String modelState : modelStates) {
			// write GPAD to avoid bioentity data issues
			GafDocument annotations = typedAnnotations.get(modelState);
			if (annotations != null) {
				PrintWriter fileWriter = null;
				File outputFile = new File(outputFolder, modelState+".gpad");
				try {
					outputFile.getParentFile().mkdirs();
					fileWriter = new PrintWriter(outputFile);
					GpadWriter writer = new GpadWriter(fileWriter, 1.2d);
					writer.write(annotations);
				}
				finally {
					IOUtils.closeQuietly(fileWriter);	
				}
			}
		}
	}
	
	private static String getModelState(OWLOntology model, String defaultValue) {
		String modelState = defaultValue;
		Set<OWLAnnotation> modelAnnotations = model.getAnnotations();
		for (OWLAnnotation modelAnnotation : modelAnnotations) {
			IRI propIRI = modelAnnotation.getProperty().getIRI();
			if (AnnotationShorthand.modelstate.getAnnotationProperty().equals(propIRI)) {
				String value = modelAnnotation.getValue().accept(new OWLAnnotationValueVisitorEx<String>() {

					@Override
					public String visit(IRI iri) {
						return null;
					}

					@Override
					public String visit(OWLAnonymousIndividual individual) {
						return null;
					}

					@Override
					public String visit(OWLLiteral literal) {
						return literal.getLiteral();
					}
				});
				if (value != null) {
					modelState = value;
				}
			}
		}
		return modelState;
	}
	
	private static String getModelCurie(OWLOntology model, CurieHandler curieHandler, String defaultValue) {
		// get model curie from ontology IRI
		String modelCurie = defaultValue;
		Optional<IRI> ontologyIRI = model.getOntologyID().getOntologyIRI();
		if (ontologyIRI.isPresent()) {
			modelCurie = curieHandler.getCuri(ontologyIRI.get());
		}
		return modelCurie;
	}
	
	List<String> handleRefs(List<String> defaultRefs, boolean addLegoModelId, String modelCurie) {
		List<String> addtitionalRefs;
		if (addLegoModelId && modelCurie != null) {
			if (defaultRefs == null) {
				addtitionalRefs = Collections.singletonList(modelCurie);
			}
			else {
				addtitionalRefs = new ArrayList<String>(defaultRefs.size() + 1);
				addtitionalRefs.addAll(defaultRefs);
				addtitionalRefs.add(modelCurie);
			}
		}
		else {
			addtitionalRefs = defaultRefs;
		}
		return addtitionalRefs;
	}
}
