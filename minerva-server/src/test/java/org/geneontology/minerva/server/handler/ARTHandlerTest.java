/**
 * 
 */
package org.geneontology.minerva.server.handler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.server.GsonMessageBodyHandler;
import org.geneontology.minerva.server.RequireJsonpFilter;
import org.geneontology.minerva.server.StartUpTool;
import org.geneontology.minerva.server.StartUpTool.MinervaStartUpConfig;
import org.geneontology.minerva.server.handler.M3BatchHandler.Entity;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3Argument;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3Request;
import org.geneontology.minerva.server.handler.M3BatchHandler.Operation;
import org.geneontology.minerva.server.handler.ModelARTHandler.ModelARTResult;
import org.geneontology.minerva.server.inferences.InferenceProviderCreator;
import org.geneontology.minerva.server.validation.MinervaShexValidator;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import com.google.gson.Gson;

import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.SimpleEcoMapper;

/**
 * @author tremaynemushayahama
 *
 */
public class ARTHandlerTest {
	private static final Logger LOGGER = Logger.getLogger(ARTHandlerTest.class);
	static Server server;
	static final String ontologyIRI = "http://purl.obolibrary.org/obo/go/extensions/go-lego.owl";
	static final String modelIdcurie = "http://model.geneontology.org/";
	static final String modelIdPrefix = "gomodel";
	static final String go_lego_journal_file = "/tmp/test-go-lego-blazegraph.jnl";
	static final String valid_model_folder = "src/test/resources/models/art-simple/";
	static final String model_save =         "src/test/resources/models/tmp/";	
	static final String shexFileUrl = "https://raw.githubusercontent.com/geneontology/go-shapes/master/shapes/go-cam-shapes.shex";
	static final String goshapemapFileUrl = "https://raw.githubusercontent.com/geneontology/go-shapes/master/shapes/go-cam-shapes.shapeMap";
		
	static OWLOntology tbox_ontology;
	static CurieHandler curieHandler;	
	static UndoAwareMolecularModelManager models;
	private static JsonOrJsonpBatchHandler handler;

	@ClassRule
	public static TemporaryFolder tmp = new TemporaryFolder();

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {			
				
		LOGGER.info("Setup shex.");
		URL shex_schema_url = new URL(shexFileUrl);
		File shex_schema_file = new File("src/test/resources/validate.shex"); 
		org.apache.commons.io.FileUtils.copyURLToFile(shex_schema_url, shex_schema_file);	
		URL shex_map_url = new URL(goshapemapFileUrl);
		File shex_map_file = new File("src/test/resources/validate.shapemap");
		org.apache.commons.io.FileUtils.copyURLToFile(shex_map_url, shex_map_file);
		
		LOGGER.info("Set up molecular model manager - loading files into a journal");
		// set curie handler	
		final CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap(modelIdcurie, modelIdPrefix));
		curieHandler = new MappedCurieHandler(DefaultCurieHandler.loadDefaultMappings(), localMappings);		
		String inputDB = makeBlazegraphJournal(valid_model_folder);			
		OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();
		tbox_ontology = ontman.createOntology(IRI.create("http://example.org/dummy"));//empty tbox
		models = new UndoAwareMolecularModelManager(tbox_ontology, curieHandler, modelIdPrefix, inputDB, model_save, go_lego_journal_file);
		models.addTaxonMetadata();

		LOGGER.info("Setup Jetty config.");
		ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.register(GsonMessageBodyHandler.class);
		resourceConfig.register(RequireJsonpFilter.class);
				
		MinervaShexValidator shex = new MinervaShexValidator(shex_schema_file, shex_map_file, curieHandler, models.getGolego_repo());
		shex.setActive(true);
		
		//setup the config for the startup tool. 
		MinervaStartUpConfig conf = new MinervaStartUpConfig();
		conf.reasonerOpt = "arachne";
		conf.shex = shex;
		conf.port = 6800;
		conf.contextString = "/";			
		
		InferenceProviderCreator ipc = StartUpTool.createInferenceProviderCreator(conf.reasonerOpt, models, conf.shex); 

		ModelARTHandler artHandler = new ModelARTHandler(models, ipc);
		//set up a handler for testing with M3BatchRequest service
		handler = new JsonOrJsonpBatchHandler(models, "development", ipc,
						Collections.<OWLObjectProperty>emptySet(), (ExternalLookupService) null);
		
		
		resourceConfig = resourceConfig.registerInstances(artHandler);

		// setup jetty server port, buffers and context path
		server = new Server();
		// create connector with port and custom buffer sizes

		HttpConfiguration http_config = new HttpConfiguration();  
		
		http_config.setRequestHeaderSize(conf.requestHeaderSize);
		ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(http_config));
		connector.setPort(conf.port);
		server.addConnector(connector);
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath(conf.contextString);
		server.setHandler(context);
		ServletHolder h = new ServletHolder(new ServletContainer(resourceConfig));
		context.addServlet(h, "/*");

		// start jetty server
		LOGGER.info("Start server on port: "+conf.port+" context: "+conf.contextString);
		server.start();		
		
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		models.dispose();
		server.stop();
		if (handler != null) {
			handler = null;
		}
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}
	
	@Test
	public final void testStoredModel() throws URISyntaxException, IOException, OWLOntologyStorageException, OWLOntologyCreationException, RepositoryException, UnknownIdentifierException {
		//get a hold of a test model
		String mid = "5fbeae9c00000008";
		final String modelId = "http://model.geneontology.org/"+mid;
		
		//save it to the database using the m3 api
		M3Request r = BatchTestTools.addIndividual(modelId, "GO:0003674");
		List<M3Request> batch = Collections.singletonList(r);
		M3BatchResponse response = handler.m3Batch("test-user", Collections.emptySet(), "test-intention", "foo-packet-id",
				batch.toArray(new M3Request[batch.size()]), false, true);
				
		r = new M3Request();
		r.entity = Entity.model;
		r.operation = Operation.storeModel;
		r.arguments = new M3Argument();
		r.arguments.modelId = modelId;
		batch = Collections.singletonList(r);
		response = handler.m3Batch("test-user", Collections.emptySet(), "test-intention", "foo-packet-id",
		batch.toArray(new M3Request[batch.size()]), false, true);
			
		
		//run a art query, show that the model found has not been modified
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/stored");
		builder.addParameter("id", "gomodel:"+mid);
		URI arturi = builder.build();
		String json_result = getJsonStringFromUri(arturi);
		Gson g = new Gson();
		ModelARTResult result = g.fromJson(json_result, ModelARTResult.class);
		
		//test if stored ontology = active ontology
				
		// check that response indicates modified
		LOGGER.info("Model Stored Model "+json_result);
		LOGGER.info("Model Active Model "+result.getActiveModel().toString());
		LOGGER.info("Model Stored Model "+result.getStoredModel().toString());
		//assertTrue(result.getActiveModel().equals(result.getStoredModel())); 

		
		// check that response now indicates not modified
		assertFalse(response.data.modifiedFlag);		
				
		//don't need to undo changes as the database is rebuilt each time from files and never flushed to file here.
	}


	private static String makeBlazegraphJournal(String input_folder) throws IOException, OWLOntologyCreationException, RepositoryException, RDFParseException, RDFHandlerException {
		String inputDB = tmp.newFile().getAbsolutePath(); 
		File i = new File(input_folder);
		if(i.exists()) {
			//remove anything that existed earlier
			File bgdb = new File(inputDB);
			if(bgdb.exists()) {
				bgdb.delete();
			}
			//load everything into a bg journal
			OWLOntology dummy = OWLManager.createOWLOntologyManager().createOntology(IRI.create("http://example.org/dummy"));
			BlazegraphMolecularModelManager<Void> m3 = new BlazegraphMolecularModelManager<>(dummy, curieHandler, modelIdPrefix, inputDB, null, go_lego_journal_file);
			if(i.isDirectory()) {
				FileUtils.listFiles(i, null, true).parallelStream().parallel().forEach(file-> {
					if(file.getName().endsWith(".ttl")||file.getName().endsWith("owl")) {
						LOGGER.info("Loading " + file);
						try {
							String modeluri = m3.importModelToDatabase(file, true);
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
			LOGGER.info("loaded files into blazegraph journal: "+input_folder);
			m3.dispose();
		}
		return inputDB;
	}

	private static String getJsonStringFromUri(URI uri) throws IOException {
		final URL url = uri.toURL();
		final HttpURLConnection connection;
		InputStream response = null;
		// setup and open (actual connection)
		connection = (HttpURLConnection) url.openConnection();
		connection.setInstanceFollowRedirects(true); // warning does not follow redirects from http to https
		response = connection.getInputStream(); // opens the connection to the server		
		// get string response from stream
		String json = IOUtils.toString(response);

		return json;
	}


	private	static String getJsonStringFromPost(HttpPost post) throws IOException {

		CloseableHttpClient httpClient = HttpClients.createDefault();
		CloseableHttpResponse response = httpClient.execute(post);
		String json = EntityUtils.toString(response.getEntity());

		return json;
	}

}
