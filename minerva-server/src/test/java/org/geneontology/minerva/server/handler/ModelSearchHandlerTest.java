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
import java.util.Collections;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.lookup.CachingExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.GolrExternalLookupService;
import org.geneontology.minerva.lookup.MonarchExternalLookupService;
import org.geneontology.minerva.server.GsonMessageBodyHandler;
import org.geneontology.minerva.server.LoggingApplicationEventListener;
import org.geneontology.minerva.server.RequireJsonpFilter;
import org.geneontology.minerva.server.inferences.InferenceProviderCreator;
import org.geneontology.minerva.server.validation.MinervaShexValidator;
import org.geneontology.minerva.server.validation.ValidationTest;
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
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;

/**
 * @author benjamingood
 *
 */
public class ModelSearchHandlerTest {
	private static final Logger LOGGER = Logger.getLogger(ValidationTest.class);
	static Server server;
	static final String ontologyIRI = "http://purl.obolibrary.org/obo/go/extensions/go-lego.owl";
	static final String catalog = "src/test/resources/ontology/catalog-for-validation.xml";
	static final String modelIdcurie = "http://model.geneontology.org/";
	static final String modelIdPrefix = "gomodel";
	static final String shexFileUrl = "https://raw.githubusercontent.com/geneontology/go-shapes/master/shapes/go-cam-shapes.shex";
	static final String goshapemapFileUrl = "https://raw.githubusercontent.com/geneontology/go-shapes/master/shapes/go-cam-shapes.shapeMap";
	static final String golr_url = "http://noctua-golr.berkeleybop.org/"; 
	static ExternalLookupService externalLookupService;
	static OWLOntology tbox_ontology;
	static CurieHandler curieHandler;	
	
	@ClassRule
	public static TemporaryFolder tmp = new TemporaryFolder();
	
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		LOGGER.info("Set up molecular model manager - loading files into a journal");
		// set curie handler
		String modelIdPrefix = "http://model.geneontology.org/";
		String modelIdcurie = "gomodel";
		CurieHandler curieHandler = new MappedCurieHandler();
		String valid_model_folder = "src/test/resources/models/should_pass/";
		String inputDB = makeBlazegraphJournal(valid_model_folder);	
		//leave tbox empty for now
		OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();
		tbox_ontology = OWLManager.createOWLOntologyManager().createOntology(IRI.create("http://example.org/dummy"));
		UndoAwareMolecularModelManager models = new UndoAwareMolecularModelManager(tbox_ontology, curieHandler, modelIdPrefix, inputDB, null);
		
		
		LOGGER.info("Setup Jetty config.");
		// Configuration: Use an already existing handler instance
		// Configuration: Use custom JSON renderer (GSON)
		ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.register(GsonMessageBodyHandler.class);
		resourceConfig.register(RequireJsonpFilter.class);

		ModelSearchHandler searchHandler = new ModelSearchHandler(models, 10000);
		resourceConfig = resourceConfig.registerInstances(searchHandler);

		// setup jetty server port, buffers and context path
		server = new Server();
		// create connector with port and custom buffer sizes

		HttpConfiguration http_config = new HttpConfiguration();  
		int requestHeaderSize = 64*1024;
		int requestBufferSize = 128*1024;
		int port = 6800;
		String contextString = "/";
		http_config.setRequestHeaderSize(requestHeaderSize);
		ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(http_config));
		connector.setPort(port);
		server.addConnector(connector);

		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath(contextString);
		server.setHandler(context);
		ServletHolder h = new ServletHolder(new ServletContainer(resourceConfig));
		context.addServlet(h, "/*");

		// start jetty server
		LOGGER.info("Start server on port: "+port+" context: "+contextString);
		server.start();
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		server.stop();
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

	/**
	 * Test method for {@link org.geneontology.minerva.server.handler.ModelSearchHandler#searchGet(java.util.Set, java.util.Set, java.util.Set, java.lang.String, java.util.Set, java.util.Set, java.util.Set, java.lang.String, int, int, java.lang.String)}.
	 * @throws URISyntaxException 
	 * @throws IOException 
	 */
	@Test
	public final void testSearchGet() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("gp", "http://identifiers.org/uniprot/P15822-3");
		//?gp=http://identifiers.org/uniprot/P15822-3
		//?goterm=http://purl.obolibrary.org/obo/GO_0003677
		URI searchuri = builder.build();
		String result = getJsonStringFromUri(searchuri);
		
		System.out.println("Search URI "+searchuri);
		System.out.println("Search result "+result);
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
			BlazegraphMolecularModelManager<Void> m3 = new BlazegraphMolecularModelManager<>(dummy, curieHandler, modelIdPrefix, inputDB, null);
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
	
}
