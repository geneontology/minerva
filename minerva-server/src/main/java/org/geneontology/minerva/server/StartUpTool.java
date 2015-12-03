package org.geneontology.minerva.server;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.geneontology.minerva.ModelReaderHelper;
import org.geneontology.minerva.ModelWriterHelper;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.lookup.CachingExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.GolrExternalLookupService;
import org.geneontology.minerva.server.handler.JsonOrJsonpBatchHandler;
import org.geneontology.minerva.server.handler.JsonOrJsonpSeedHandler;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLSubObjectPropertyOfAxiom;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import owltools.cli.Opts;
import owltools.graph.OWLGraphWrapper;
import owltools.io.CatalogXmlIRIMapper;
import owltools.io.ParserWrapper;


public class StartUpTool {
	
	private static final Logger LOGGER = Logger.getLogger(StartUpTool.class);

	public static class MinervaStartUpConfig {
		// data configuration
		public String ontology = null;
		public String catalog = null;
		public String modelFolder = null;
		public String modelIdPrefix = "http://model.geneontology.org/";
		public String modelIdcurie = "gomodel";
		
		public String defaultModelState = "development";
		
		public String golrUrl = null;
		public String golrSeedUrl = null;
		public int golrCacheSize = 100000;
		public long golrCacheDuration = 24l;
		public TimeUnit golrCacheDurationUnit = TimeUnit.HOURS;
		public ExternalLookupService lookupService = null;
		public boolean checkLiteralIds = true;

		// reasoner settings
		public boolean useReasoner = true;
		
		/*
		 * If set to TRUE, this will use a different approach for the reasoner: It will create
		 * a module from the abox using the individuals as seeds and only create a reasoner for
		 * this new ontology.
		 * 
		 * This reduced set of axioms should consume less memory for each reasoner. 
		 * The drawback is the additional CPU time (sequential) to generate the relevant 
		 * subset. During tests this tripled the runtime of the test cases. 
		 */
		public boolean useModuleReasoner = false;
		public OWLReasonerFactory rf = new ElkReasonerFactory();
		
		public CurieHandler curieHandler;

		// The subset of highly relevant relations is configured using super property
		// all direct children (asserted) are considered important
		public String importantRelationParent = null;
		public Set<OWLObjectProperty> importantRelations = null;

		// server configuration
		public int port = 6800; 
		public String contextPrefix = null; // root context by default
		public String contextString = null;
		
		// increase default size to deal with large HTTP GET requests
		public int requestHeaderSize = 64*1024;
		public int requestBufferSize = 128*1024;
		
		public boolean useRequestLogging = false;
		
		public boolean useGolrUrlLogging = false;
	}
	
	public static void main(String[] args) throws Exception {
		Opts opts = new Opts(args);
		MinervaStartUpConfig conf = new MinervaStartUpConfig();
		
		
		while (opts.hasArgs()) {
			if (opts.nextEq("-g|--graph")) {
				conf.ontology = opts.nextOpt();
			}
			else if (opts.nextEq("-c|--catalog")) {
				conf.catalog = opts.nextOpt();
			}
			else if (opts.nextEq("-f|--model-folder")) {
				conf.modelFolder = opts.nextOpt();
			}
			else if (opts.nextEq("--model-id-prefix")) {
				conf.modelIdPrefix = opts.nextOpt();
			}
			else if (opts.nextEq("--model-id-curie")) {
				conf.modelIdcurie = opts.nextOpt();
			}
			else if (opts.nextEq("-p|--protein-folder")) {
				System.err.println("specific protein ontologies are no longer supported");
				System.exit(-1);
			}
			else if (opts.nextEq("--gaf-folder")) {
				System.err.println("--gaf-folder is not longer supported");
				System.exit(-1);
			}
			else if (opts.nextEq("--context-prefix")) {
				conf.contextPrefix = opts.nextOpt();
			}
			else if (opts.nextEq("--port")) {
				conf.port = Integer.parseInt(opts.nextOpt());
			}
			else if (opts.nextEq("-i|--import|--additional-import")) {
				System.err.println("-i|--import|--additional-import is no longer supported, all imports are expected to be in the source ontology '-g|--graph'");
				System.exit(-1);
			}
			else if (opts.nextEq("--obsolete-import")) {
				System.err.println("--obsolete-import is no longer supported");
				System.exit(-1);
			}
			else if (opts.nextEq("--set-relevant-relations")) {
				System.err.println("--set-relevant-relations is no longer supported, use '--set-important-relation-parent' instead");
				System.exit(-1);
			}
			else if (opts.nextEq("--add-relevant-relations")) {
				System.err.println("--add-relevant-relations is no longer supported, use '--set-important-relation-parent' instead");
				System.exit(-1);
			}
			else if (opts.nextEq("--add-relevant-relation")) {
				System.err.println("--add-relevant-relation is no longer supported, use '--set-important-relation-parent' instead");
				System.exit(-1);
			}
			else if (opts.nextEq("--set-important-relation-parent")) {
				conf.importantRelationParent = opts.nextOpt();
			}
			else if (opts.nextEq("--skip-class-id-validation")) {
				conf.checkLiteralIds = false;
			}
			else if (opts.nextEq("--golr-cache-size")) {
				String sizeString = opts.nextOpt();
				conf.golrCacheSize = Integer.parseInt(sizeString);
			}
			else if (opts.nextEq("--golr-labels")) {
				conf.golrUrl = opts.nextOpt();
			}
			else if (opts.nextEq("--golr-seed")) {
				conf.golrSeedUrl = opts.nextOpt();
			}
			else if (opts.nextEq("--no-reasoning|--no-reasoner")) {
				conf.useReasoner = false;
			}
			else if (opts.nextEq("--slme-hermit")) {
				conf.rf = new org.semanticweb.HermiT.Reasoner.ReasonerFactory();
				conf.useModuleReasoner = true;
			}
			else if (opts.nextEq("--slme-elk")) {
				conf.rf = new ElkReasonerFactory();
				conf.useModuleReasoner = true;
			}
			else if (opts.nextEq("--elk")) {
				conf.rf = new ElkReasonerFactory();
				conf.useModuleReasoner = false;
			}
			else if (opts.nextEq("--use-request-logging|--request-logging")) {
				conf.useRequestLogging = true;
			}
			else if (opts.nextEq("--use-golr-url-logging|--golr-url-logging")) {
				conf.useGolrUrlLogging = true;
			}
			else {
				break;
			}
		}
		if (conf.ontology == null) {
			System.err.println("No ontology graph available");
			System.exit(-1);
		}
		if (conf.modelFolder == null) {
			System.err.println("No model folder available");
			System.exit(-1);
		} 
		conf.contextString = "/";
		if (conf.contextPrefix != null) {
			conf.contextString = "/"+conf.contextPrefix;
		}
		
		// set curie handler
		CurieMappings defaultMappings = DefaultCurieHandler.getMappings();
		CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap(conf.modelIdcurie, conf.modelIdPrefix));
		
		// TODO make the modules configurable
		conf.curieHandler = new MappedCurieHandler(defaultMappings, localMappings);

		// wrap the Golr service with a cache
		if (conf.golrUrl != null) {
			conf.lookupService = new GolrExternalLookupService(conf.golrUrl, conf.curieHandler, conf.useGolrUrlLogging);
			LOGGER.info("Setting up Golr cache with size: "+conf.golrCacheSize+" duration: "+
					conf.golrCacheDuration+" "+conf.golrCacheDurationUnit+
					" use url logging: "+conf.useGolrUrlLogging);
			conf.lookupService = new CachingExternalLookupService(conf.lookupService, conf.golrCacheSize, conf.golrCacheDuration, conf.golrCacheDurationUnit);
		}
		
		startUp(conf);
	}
	
	/**
	 * Try to resolve the given string into an {@link OWLObjectProperty}.
	 * 
	 * @param rel
	 * @param g
	 * @return property or null
	 */
	public static OWLObjectProperty getRelation(String rel, OWLGraphWrapper g) {
		if (rel == null || rel.isEmpty()) {
			return null;
		}
		if (rel.startsWith("http://")) {
			IRI iri = IRI.create(rel);
			return g.getDataFactory().getOWLObjectProperty(iri);
		}
		// try to find property
		OWLObjectProperty p = g.getOWLObjectPropertyByIdentifier(rel);
		if (p == null) {
			// could not find by id, search by label
			OWLObject owlObject = g.getOWLObjectByLabel(rel);
			if (owlObject instanceof OWLObjectProperty) {
				p = (OWLObjectProperty) owlObject;
			}
		}
		return p;
	}
	
	/**
	 * Find all asserted direct sub properties of the parent property.
	 * 
	 * @param parent
	 * @param g
	 * @return set
	 */
	public static Set<OWLObjectProperty> getAssertedSubProperties(OWLObjectProperty parent, OWLGraphWrapper g) {
		Set<OWLObjectProperty> properties = new HashSet<OWLObjectProperty>();
		for(OWLOntology ont : g.getAllOntologies()) {
			Set<OWLSubObjectPropertyOfAxiom> axioms = ont.getObjectSubPropertyAxiomsForSuperProperty(parent);
			for (OWLSubObjectPropertyOfAxiom axiom : axioms) {
				OWLObjectPropertyExpression subProperty = axiom.getSubProperty();
				if (subProperty instanceof OWLObjectProperty) {
					properties.add(subProperty.asOWLObjectProperty());
				}
			}
		}
		return properties;
	}

	public static void startUp(final MinervaStartUpConfig conf) 
			throws Exception {
		// load ontology
		LOGGER.info("Start loading ontology: "+conf.ontology);
		ParserWrapper pw = new ParserWrapper();
		// if available, set catalog
		if (conf.catalog != null) {
			LOGGER.info("Adding catalog xml: "+conf.catalog);
			pw.addIRIMapper(new CatalogXmlIRIMapper(conf.catalog));
		}
		OWLGraphWrapper graph = pw.parseToOWLGraph(conf.ontology);
		
		// try to get important relations
		if (conf.importantRelationParent != null) {
			// try to find parent property
			OWLObjectProperty parentProperty = getRelation(conf.importantRelationParent, graph);
			if (parentProperty != null) {
				// find all asserted direct sub properties of the parent property
				conf.importantRelations = getAssertedSubProperties(parentProperty, graph);
				if (conf.importantRelations.isEmpty()) {
					LOGGER.warn("Could not find any asserted sub properties for parent: "+conf.importantRelationParent);
				}
			}
			else {
				LOGGER.warn("Could not find a property for rel: "+conf.importantRelationParent);
			}
		}

		// create model manager
		LOGGER.info("Start initializing Minerva");
		UndoAwareMolecularModelManager models = new UndoAwareMolecularModelManager(graph, conf.rf,
				conf.curieHandler, conf.modelIdPrefix);
		// set pre and post file handlers
		models.addPostLoadOntologyFilter(ModelReaderHelper.INSTANCE);
		models.addPreFileSaveHandler(new ModelWriterHelper(conf.curieHandler, conf.lookupService));
		
		// set folder to  models
		LOGGER.info("Model path: "+conf.modelFolder);
		models.setPathToOWLFiles(conf.modelFolder);
		
		// start server
		Server server = startUp(models, conf);
		server.join();
	}
	
	public static Server startUp(UndoAwareMolecularModelManager models, MinervaStartUpConfig conf)
			throws Exception {
		LOGGER.info("Setup Jetty config.");
		// Configuration: Use an already existing handler instance
		// Configuration: Use custom JSON renderer (GSON)
		ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.register(GsonMessageBodyHandler.class);
		resourceConfig.register(RequireJsonpFilter.class);
		if (conf.useRequestLogging) {
			resourceConfig.register(LoggingApplicationEventListener.class);
		}
		//resourceConfig.register(AuthorizationRequestFilter.class);
		
		LOGGER.info("BatchHandler config useReasoner: "+conf.useReasoner);
		LOGGER.info("BatchHandler config useModuleReasoner: "+conf.useModuleReasoner);
		LOGGER.info("BatchHandler config importantRelations: "+conf.importantRelations);
		LOGGER.info("BatchHandler config lookupService: "+conf.lookupService);
		LOGGER.info("BatchHandler config checkLiteralIds: "+conf.checkLiteralIds);
		LOGGER.info("BatchHandler config useRequestLogging: "+conf.useRequestLogging);
		if (conf.golrSeedUrl == null) {
			// default fall back to normal golr URL
			conf.golrSeedUrl = conf.golrUrl;
		}
		LOGGER.info("SeedHandler config golrUrl: "+conf.golrSeedUrl);
		
		JsonOrJsonpBatchHandler batchHandler = new JsonOrJsonpBatchHandler(models, conf.defaultModelState,
				conf.useReasoner, conf.useModuleReasoner, conf.importantRelations, conf.lookupService);
		batchHandler.CHECK_LITERAL_IDENTIFIERS = conf.checkLiteralIds;
		JsonOrJsonpSeedHandler seedHandler = new JsonOrJsonpSeedHandler(models, conf.defaultModelState, conf.golrSeedUrl);
		resourceConfig = resourceConfig.registerInstances(batchHandler, seedHandler);

		// setup jetty server port, buffers and context path
		Server server = new Server();
		// create connector with port and custom buffer sizes
		SelectChannelConnector connector = new SelectChannelConnector();
		connector.setPort(conf.port);
		connector.setRequestHeaderSize(conf.requestHeaderSize);
		connector.setRequestBufferSize(conf.requestBufferSize);
		server.addConnector(connector);

		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath(conf.contextString);
		server.setHandler(context);
		ServletHolder h = new ServletHolder(new ServletContainer(resourceConfig));
		context.addServlet(h, "/*");

		// start jetty server
		LOGGER.info("Start server on port: "+conf.port+" context: "+conf.contextString);
		server.start();
		return server;
	}
}
