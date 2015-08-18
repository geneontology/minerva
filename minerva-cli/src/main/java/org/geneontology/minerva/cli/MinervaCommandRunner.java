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
import org.coode.owlapi.manchesterowlsyntax.ManchesterOWLSyntaxOntologyFormat;
import org.geneontology.minerva.GafToLegoIndividualTranslator;
import org.geneontology.minerva.GafToLegoTranslator;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.generate.LegoModelGenerator;
import org.geneontology.minerva.legacy.LegoAllIndividualToGeneAnnotationTranslator;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.geneontology.minerva.util.MinimalModelGenerator;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.io.OWLFunctionalSyntaxOntologyFormat;
import org.semanticweb.owlapi.io.RDFXMLOntologyFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationValueVisitorEx;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyFormat;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import owltools.cli.JsCommandRunner;
import owltools.cli.Opts;
import owltools.cli.tools.CLIMethod;
import owltools.gaf.BioentityDocument;
import owltools.gaf.GafDocument;
import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.SimpleEcoMapper;
import owltools.gaf.io.GpadWriter;
import owltools.graph.OWLGraphWrapper;
import owltools.io.OWLPrettyPrinter;
import owltools.vocab.OBOUpperVocabulary;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

public class MinervaCommandRunner extends JsCommandRunner {

	private volatile MinimalModelGenerator mmg = null;
	
	
	private synchronized MinimalModelGenerator getMinimalModelGenerator(String modelId, boolean isCreateNewAbox) throws OWLOntologyCreationException {

		if (mmg == null) {
			IRI modelIRI = IRI.create("foo://bar/"+modelId);
			OWLReasonerFactory rf = new ElkReasonerFactory();
			if (isCreateNewAbox) {
				ModelContainer model = new ModelContainer(modelIRI, g.getSourceOntology(), rf);
				mmg = new MinimalModelGenerator(model);
			}
			else {
				ModelContainer model = new ModelContainer(modelIRI, g.getSourceOntology(), g.getSourceOntology(), rf);
				mmg = new MinimalModelGenerator(model);
			}
		}
		return mmg;
	}
	
	@CLIMethod("--generate-minimal-model")
	public void generateMinimalModel(Opts opts) throws Exception {
		opts.info("[--no-collapse] [--no-reduce] [--reuse-tblox] [-x] [-a] [-s] CLASS", "Generates default/proto individuals for a class");
		boolean isCollapse = true;
		boolean isReduce = true;
		boolean isExtractModule = false;
		boolean isPrecomputePropertyClassCombinations = true;
		boolean isCreateNewAbox = true;
		boolean isAllIndividuals = false;
		boolean isStrict = true;
		Set<OWLObjectProperty> normProps = new HashSet<OWLObjectProperty>();
		Set<OWLClass> preservedClassSet = null;
		OWLClass c;
		while (opts.hasOpts()) {
			if (opts.nextEq("--no-collapse")) {
				opts.info("", "if set, do not heuristically collapse individuals");
				isCollapse = false;
			}
			else if (opts.nextEq("--no-reduce")) {
				opts.info("", "if set, do not perform transitive reduction");
				isReduce = false;
			}
			else if (opts.nextEq("--reuse-tbox")) {
				opts.info("", "if set, place new individuals in the ontology");
				isCreateNewAbox = false;
			}
			else if (opts.nextEq("-a|--all-individuals")) {
				isAllIndividuals = true;
			}
			else if (opts.nextEq("-s|--is-strict")) {
				isStrict = true;
			}
			else if (opts.nextEq("-x|--extract-module")) {
				isExtractModule = true;
			}
			else if (opts.nextEq("-q|--quick")) {
				isPrecomputePropertyClassCombinations = false;
			}
			else if (opts.nextEq("-p|--property")) {
				normProps.add(this.resolveObjectProperty(opts.nextOpt()));
			}
			else if (opts.nextEq("-l|--plist")) {
				normProps.addAll(this.resolveObjectPropertyList(opts));
			}
			else if (opts.nextEq("--lego")) {
				preservedClassSet = new HashSet<OWLClass>();
				preservedClassSet.add(g.getDataFactory().getOWLClass(OBOUpperVocabulary.GO_biological_process.getIRI()));
				preservedClassSet.add(g.getDataFactory().getOWLClass(OBOUpperVocabulary.GO_molecular_function.getIRI()));
			}

			else {
				break;
			}
		}
		mmg = getMinimalModelGenerator("1", isCreateNewAbox);
		if (isStrict) {
			mmg.isStrict = true;
		}
		mmg.setPrecomputePropertyClassCombinations(isPrecomputePropertyClassCombinations);

		if (isAllIndividuals) {
			mmg.generateAllNecessaryIndividuals(isCollapse, isReduce);
		}
		else {
			c = this.resolveClass(opts.nextOpt());
			mmg.generateNecessaryIndividuals(c, isCollapse, isReduce);
		}
		for (OWLObjectProperty p : normProps) {
			mmg.normalizeDirections(p);
		}
		if (preservedClassSet != null && preservedClassSet.size() > 0) {
			mmg.anonymizeIndividualsNotIn(preservedClassSet);
		}
		if (isExtractModule) {
			mmg.extractModule();
		}
		g.setSourceOntology(mmg.getModel().getAboxOntology());
	}
	
	@CLIMethod("--most-specific-class-expression|--msce")
	public void msce(Opts opts) throws Exception {
		opts.info("[-c CLASS] INDIVIDUAL", "Generates MSCE for an individual using MinimalModelGenerator");
		mmg = getMinimalModelGenerator("1", false);
		OWLNamedIndividual ind;
		OWLClass c = null;
		while (opts.hasOpts()) {
			if (opts.nextEq("-c|--class")) {
				opts.info("CLASS", "if set will add equivalence axioms to CLASS");
				c = this.resolveClass(opts.nextOpt());
			}
			else {
				break;
			}
		}
		ind =  (OWLNamedIndividual) this.resolveEntity(opts);
		OWLClassExpression ce = mmg.getMostSpecificClassExpression(ind);

		System.out.println(getPrettyPrinter().render(ce));
		System.out.println(ce);

		if (c != null) {
			OWLEquivalentClassesAxiom ax = g.getDataFactory().getOWLEquivalentClassesAxiom(c, ce);
			g.getManager().addAxiom(g.getSourceOntology(), ax);
		}
	}
	
	@CLIMethod("--modalize")
	public void modalize(Opts opts) throws Exception {
		opts.info("CLASS", "Take all instances of CLASS and make a generalized statement about them");
		mmg = getMinimalModelGenerator("1", false);
		OWLClass qc = null;
		OWLObjectProperty p = null;
		OWLDataFactory df = g.getDataFactory();
		while (opts.hasOpts()) {
			if (opts.nextEq("-p|--modal-property")) {
				p = resolveObjectProperty(opts.nextOpt());
				opts.info("CLASS", "if set will add equivalence axioms to CLASS");
				//c = this.resolveClass(opts.nextOpt());
			}
			else {
				break;
			}
		}
		qc = this.resolveClass(opts.nextOpt());
		Set<OWLNamedIndividual> inds = mmg.getModel().getReasoner().getInstances(qc, false).getFlattened();
		for (OWLNamedIndividual ind : inds) {
			OWLClassExpression ce = mmg.getMostSpecificClassExpression(ind);
			if (ce instanceof OWLObjectIntersectionOf) {
				for (OWLClassExpression x : ((OWLObjectIntersectionOf)ce).getOperands()) {
					if (x instanceof OWLObjectSomeValuesFrom) {
						OWLObjectSomeValuesFrom svf = ((OWLObjectSomeValuesFrom)x);

					}
				}
			}
			Set<OWLClass> types = mmg.getModel().getReasoner().getTypes(ind, true).getFlattened();

			System.out.println(getPrettyPrinter().render(ce));
			System.out.println(ce);
			for (OWLClass c : types) {
				df.getOWLSubClassOfAxiom(c, df.getOWLObjectSomeValuesFrom(p, ce));
			}
		}
	}
	
	@CLIMethod("--gaf2lego")
	@Deprecated
	public void gaf2Lego(Opts opts) throws Exception {
		String output = null;
		boolean minimize = false;
		OWLOntologyFormat format = new RDFXMLOntologyFormat();
		while (opts.hasOpts()) {
			if (opts.nextEq("-m|--minimize")) {
				opts.info("", "If set, combine into one model");
				minimize = true;
			}
			else if (opts.nextEq("-o|--output")) {
				output = opts.nextOpt();
			}
			else if (opts.nextEq("--format")) {
				String formatString = opts.nextOpt();
				if ("manchester".equalsIgnoreCase(formatString)) {
					format = new ManchesterOWLSyntaxOntologyFormat();
				}
			}
			else {
				break;
			}
		}
		if (g != null && gafdoc != null && output != null) {
			GafToLegoTranslator translator = new GafToLegoTranslator(g, null);
			OWLOntology lego;
			if (minimize) {
				lego = translator.minimizedTranslate(gafdoc);
			}
			else {
				lego = translator.translate(gafdoc);
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
		OWLOntologyFormat format = new RDFXMLOntologyFormat();
		while (opts.hasOpts()) {
			if (opts.nextEq("-o|--output")) {
				output = opts.nextOpt();
			}
			else if (opts.nextEq("--format")) {
				String formatString = opts.nextOpt();
				if ("manchester".equalsIgnoreCase(formatString)) {
					format = new ManchesterOWLSyntaxOntologyFormat();
				}
				else if ("functional".equalsIgnoreCase(formatString)) {
					format = new OWLFunctionalSyntaxOntologyFormat();
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
	
	@CLIMethod("--generate-molecular-model")
	@Deprecated
	public void generateMolecularModel(Opts opts) throws Exception {
		opts.info("[--owl FILE] [-s SEED-GENE-LIST] [-a] [-r] -p PROCESS", "Generates an activity network (aka lego) from existing GAF and ontology");
		OWLClass processCls = null;
		File owlOutputFile = null;
		boolean isReplaceSourceOntology = false;
		boolean isPrecomputePropertyClassCombinations = false;
		boolean isExtractModule = false;
		List<String> seedGenes = new ArrayList<String>();
		while (opts.hasOpts()) {
			if (opts.nextEq("-p")) {
				processCls = this.resolveClass(opts.nextOpt());
			}
			else if (opts.nextEq("-r|--replace")) {
				isReplaceSourceOntology = true;
			}
			else if (opts.nextEq("-q|--quick")) {
				isPrecomputePropertyClassCombinations = false;
			}
			else if (opts.nextEq("-x|--extract-module")) {
				isExtractModule = true;
			}
			else if (opts.nextEq("-a|--all-relation-class-pairs")) {
				isPrecomputePropertyClassCombinations = true;
			}
			else if (opts.nextEq("-s|--seed")) {
				seedGenes = opts.nextList();
			}
			else if (opts.nextEq("--owl")) {
				owlOutputFile = opts.nextFile();
			}
			else {
				break;
			}
		}
		ModelContainer model = new ModelContainer(IRI.create("foo://bar/1"), g.getSourceOntology(), new ElkReasonerFactory());
		LegoModelGenerator ni = new LegoModelGenerator(model);
		ni.setPrecomputePropertyClassCombinations(isPrecomputePropertyClassCombinations);
		ni.initialize(gafdoc, g);

		String p = g.getIdentifier(processCls);
		seedGenes.addAll(ni.getGenes(processCls));

		ni.buildNetwork(p, seedGenes);


		OWLOntology ont = model.getAboxOntology();
		if (isExtractModule) {
			ni.extractModule();
		}
		if (owlOutputFile != null) {
			FileOutputStream os = new FileOutputStream(owlOutputFile);
			g.getManager().saveOntology(ont, os);
		}
		if (isReplaceSourceOntology) {
			g.setSourceOntology(model.getAboxOntology());
		}
	}
	
	@SuppressWarnings("unused")
	@CLIMethod("--fetch-candidate-process")
	@Deprecated
	public void fetchCandidateProcess(Opts opts) throws Exception {
		Double pvalThresh = 0.05;
		Double pvalCorrectedThresh = 0.05;
		Integer popSize = null;
		boolean isDirect = false;
		boolean isReflexive = false;
		while (opts.hasOpts()) {
			if (opts.nextEq("-p")) {
				pvalCorrectedThresh = Double.valueOf(opts.nextOpt());
			}
			else if (opts.nextEq("--pval-uncorrected")) {
				pvalThresh = Double.valueOf(opts.nextOpt());
			}
			else if (opts.nextEq("--pop-size")) {
				popSize = Integer.valueOf(opts.nextOpt());
			}
			else if (opts.nextEq("-d")) {
				isDirect = true;
			}
			else if (opts.nextEq("-r")) {
				isReflexive = true;
			}
			else {
				break;
			}
		}
		OWLClass disease = this.resolveClass(opts.nextOpt());

		OWLPrettyPrinter owlpp = new OWLPrettyPrinter(g);
		ModelContainer model = new ModelContainer(IRI.create("foo://bar/1"), g.getSourceOntology(), new ElkReasonerFactory());
		LegoModelGenerator ni = new LegoModelGenerator(model);
		ni.setPrecomputePropertyClassCombinations(false);

		ni.initialize(gafdoc, g);
		OWLClass nothing = g.getDataFactory().getOWLNothing();
		Map<OWLClass, Double> smap = ni.fetchScoredCandidateProcesses(disease, popSize);
		int MAX = 500;
		int n=0;
		for (Map.Entry<OWLClass, Double> e : smap.entrySet()) {
			n++;
			if (n > MAX) {
				break;
			}
			Double score = e.getValue();
			OWLClass c = e .getKey();
			System.out.println("PROC\t"+owlpp.render(c)+"\t"+score);
		}
	}

	@CLIMethod("--go-multi-enrichment")
	@Deprecated
	public void goMultiEnrichment(Opts opts) throws Exception {
		opts.info("P1 P2", "Generates an activity network (aka lego) from existing GAF and ontology");
		Double pvalThresh = 0.05;
		Double pvalCorrectedThresh = 0.05;
		Integer popSize = null;
		boolean isDirect = false;
		boolean isReflexive = false;
		while (opts.hasOpts()) {
			if (opts.nextEq("-p")) {
				pvalCorrectedThresh = Double.valueOf(opts.nextOpt());
			}
			else if (opts.nextEq("--pval-uncorrected")) {
				pvalThresh = Double.valueOf(opts.nextOpt());
			}
			else if (opts.nextEq("--pop-size")) {
				popSize = Integer.valueOf(opts.nextOpt());
			}
			else if (opts.nextEq("-d")) {
				isDirect = true;
			}
			else if (opts.nextEq("-r")) {
				isReflexive = true;
			}
			else {
				break;
			}
		}
		OWLClass rc1 = this.resolveClass(opts.nextOpt());
		OWLClass rc2 = this.resolveClass(opts.nextOpt());
		ModelContainer model = new ModelContainer(IRI.create("foo://bar/1"), g.getSourceOntology(), new ElkReasonerFactory());
		LegoModelGenerator ni = new LegoModelGenerator(model);

		ni.initialize(gafdoc, g);
		OWLPrettyPrinter owlpp = new OWLPrettyPrinter(g);
		OWLClass nothing = g.getDataFactory().getOWLNothing();
		Set<OWLClass> sampleSet = model.getReasoner().getSubClasses(rc2, false).getFlattened();
		sampleSet.remove(nothing);
		if (isDirect) {
			sampleSet = Collections.singleton(rc2);
		}
		if (isReflexive) {
			sampleSet.add(rc2);
		}

		// calc correction factor
		int numHypotheses = 0;
		for (OWLClass c1 : model.getReasoner().getSubClasses(rc1, false).getFlattened()) {
			if (c1.equals(nothing))
				continue;
			if (ni.getGenes(c1).size() < 2) {
				continue;
			}
			for (OWLClass c2 : sampleSet) {
				if (ni.getGenes(c2).size() < 2) {
					continue;
				}
				numHypotheses++;
			}
		}


		for (OWLClass c1 : model.getReasoner().getSubClasses(rc1, false).getFlattened()) {
			if (c1.equals(nothing))
				continue;
			System.out.println("Sample: "+c1);
			for (OWLClass c2 : sampleSet) {
				if (c2.equals(nothing))
					continue;
				Double pval = ni.calculatePairwiseEnrichment(c1, c2, popSize);
				if (pval == null || pval > pvalThresh)
					continue;
				Double pvalCorrected = pval * numHypotheses;
				if (pvalCorrected == null || pvalCorrected > pvalCorrectedThresh)
					continue;
				System.out.println("enrich\t"+owlpp.render(c1)+"\t"+owlpp.render(c2)+"\t"+pval+"\t"+pvalCorrected);
			}
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

		SimpleEcoMapper mapper = EcoMapperFactory.createSimple();
		LegoAllIndividualToGeneAnnotationTranslator translator = new LegoAllIndividualToGeneAnnotationTranslator(g, curieHandler, reasoner, mapper);
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
				translator.translate(model, annotations, entities, addtitionalRefs);	
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
		IRI ontologyIRI = model.getOntologyID().getOntologyIRI();
		if (ontologyIRI != null) {
			modelCurie = curieHandler.getCuri(ontologyIRI);
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
