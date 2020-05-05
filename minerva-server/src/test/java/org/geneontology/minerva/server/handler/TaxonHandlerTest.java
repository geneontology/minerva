/**
 * 
 */
package org.geneontology.minerva.server.handler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.geneontology.minerva.server.handler.ModelSearchHandler.ModelSearchResult;
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * @author benjamingood
 *
 */
public class TaxonHandlerTest {
	private static final Logger LOGGER = Logger.getLogger(TaxonHandlerTest.class);
	static Server server;
	static final String ontologyIRI = "http://purl.obolibrary.org/obo/go/extensions/go-lego.owl";
	static final String modelIdcurie = "http://model.geneontology.org/";
	static final String modelIdPrefix = "gomodel";
	static final String go_lego_journal_file = "/tmp/test-go-lego-blazegraph.jnl";
	static OWLOntology tbox_ontology;
	static CurieHandler curieHandler;	
	static TaxonHandler taxonHandler;
	
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
		curieHandler = new MappedCurieHandler();
		String valid_model_folder = "src/test/resources/models/should_pass/";
		String inputDB = makeBlazegraphJournal(valid_model_folder);	
		//leave tbox empty for now
		OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();
		tbox_ontology = ontman.createOntology(IRI.create("http://example.org/dummy"));
		UndoAwareMolecularModelManager models = new UndoAwareMolecularModelManager(tbox_ontology, curieHandler, modelIdPrefix, inputDB, null, go_lego_journal_file);
		models.addTaxonMetadata();
		
		LOGGER.info("Setup Jetty config.");
		// Configuration: Use an already existing handler instance
		// Configuration: Use custom JSON renderer (GSON)
		ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.register(GsonMessageBodyHandler.class);
		resourceConfig.register(RequireJsonpFilter.class);

		taxonHandler = new TaxonHandler(models);
		resourceConfig = resourceConfig.registerInstances(taxonHandler);

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
		taxonHandler.getM3().dispose();
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

	
	
	@Test
	public final void testTaxa() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/taxa/");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		LOGGER.info("JSON result from test taxa\n"+json_result);
		Gson g = new Gson();
		TaxonHandler.Taxa result = g.fromJson(json_result, TaxonHandler.Taxa.class);
		assertTrue(result.taxa.size()>1);
		LOGGER.info("N taxa: "+result.taxa.size());
		for(TaxonHandler.Taxa.Taxon t : result.taxa) {
			LOGGER.info(t.id+" "+t.label);
		}
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
							m3.importModelToDatabase(file, true);
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
