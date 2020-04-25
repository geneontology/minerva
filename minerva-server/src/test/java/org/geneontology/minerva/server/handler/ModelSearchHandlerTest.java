/**
 * 
 */
package org.geneontology.minerva.server.handler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

/**
 * @author benjamingood
 *
 */
public class ModelSearchHandlerTest {
	private static final Logger LOGGER = Logger.getLogger(ModelSearchHandlerTest.class);
	static Server server;
	static final String ontologyIRI = "http://purl.obolibrary.org/obo/go/extensions/go-lego.owl";
	static final String modelIdcurie = "http://model.geneontology.org/";
	static final String modelIdPrefix = "gomodel";
	static final String go_lego_journal_file = "/tmp/blazegraph.jnl";
	static OWLOntology tbox_ontology;
	static CurieHandler curieHandler;	
	static UndoAwareMolecularModelManager models;
	
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
		String model_save =         "src/test/resources/models/tmp/";
		String inputDB = makeBlazegraphJournal(valid_model_folder);	
		//leave tbox empty for now
		OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();
		tbox_ontology = ontman.createOntology(IRI.create("http://example.org/dummy"));
		models = new UndoAwareMolecularModelManager(tbox_ontology, curieHandler, modelIdPrefix, inputDB, model_save, go_lego_journal_file);
		models.addTaxonMetadata();
		
		LOGGER.info("Setup Jetty config.");
		// Configuration: Use an already existing handler instance
		// Configuration: Use custom JSON renderer (GSON)
		ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.register(GsonMessageBodyHandler.class);
		resourceConfig.register(RequireJsonpFilter.class);

		ModelSearchHandler searchHandler = new ModelSearchHandler(models);
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
		models.dispose();
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
	public final void testSearchGetByGene() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("gp", "http://identifiers.org/uniprot/P15822-3");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by gene URI "+searchuri);
		LOGGER.info("Search by gene result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue(result.getN()>0);
	}

	@Test
	public final void testSearchPostByGene() throws URISyntaxException, IOException {
		HttpPost post = new HttpPost(server.getURI()+"search");
		List<BasicNameValuePair> urlParameters = new ArrayList<>();
		urlParameters.add(new BasicNameValuePair("gp", "http://identifiers.org/wormbase/WBGene00001865"));		
		urlParameters.add(new BasicNameValuePair("gp", "http://identifiers.org/wormbase/WBGene00017304"));	
		urlParameters.add(new BasicNameValuePair("gp", "http://identifiers.org/wormbase/WBGene00003418"));
		post.setEntity(new UrlEncodedFormEntity(urlParameters));
		LOGGER.info("post "+post.toString());
		String json_result = getJsonStringFromPost(post);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by gene POST result "+json_result);
		LOGGER.info("POST N models found: "+result.getN());
		assertTrue(result.getN()>0);
	}
	
	@Test
	public final void testSearchGetByGO() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("term", "http://purl.obolibrary.org/obo/GO_0003677");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by GO term URI "+searchuri);
		LOGGER.info("Search by GO term result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue(result.getN()>0);
	}

	@Test
	public final void testSearchGetByGOclosure() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("term", "http://purl.obolibrary.org/obo/GO_0140312");//clathrin binding - should get one model that uses child clathrin activity GO_0035615
		builder.addParameter("expand", "");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by GO term URI "+searchuri);
		LOGGER.info("Search by GO term result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue(result.getN()+" models found should find some from children of GO_0140312", result.getN()>0);
		
		builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("term", "http://purl.obolibrary.org/obo/GO_0140312");//clathrin binding - should get one model that uses child clathrin activity GO_0035615
		searchuri = builder.build();
		json_result = getJsonStringFromUri(searchuri);
		g = new Gson();
		result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by GO term URI "+searchuri);
		LOGGER.info("Search by GO term result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue(result.getN()+" without expand on, should find now models for GO_0140312", result.getN()==0);
	}
	
	@Test
	public final void testSearchGetByGOGiantclosure() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("term", "http://purl.obolibrary.org/obo/GO_0003824");
		builder.addParameter("expand", "");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by GO term URI "+searchuri);
		LOGGER.info("Search by GO term result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue(result.getN()+" models found should find many children of GO_0003824", result.getN()>0);
	}	

	@Test
	public final void testSearchGetByWormAnatomy() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("term", "http://purl.obolibrary.org/obo/WBbt_0006748"); //vulva
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by GO term URI "+searchuri);
		LOGGER.info("Search by GO term result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue("", result.getN()>0);
	}

	@Test
	public final void testSearchGetByWormAnatomyClosure() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("term", "http://purl.obolibrary.org/obo/WBbt_0008422"); //sex organ parent of vulva
		builder.addParameter("expand", "");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by GO term URI "+searchuri);
		LOGGER.info("Search by GO term result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue("", result.getN()>0);
	}	
	
	//
	
	@Test
	public final void testSearchGetByTaxon() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("taxon", "6239");//worm 6239 14 models //9606 2 zebrafish 7955 2 
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by taxon "+searchuri);
		LOGGER.info("Search by taxon result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue("No models found for taxon ", result.getN()>0);
	}
	
	@Test
	public final void testSearchGetByTaxonCurie() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("taxon", "NCBITaxon:559292");//worm 6239 14 models //9606 2 zebrafish 7955 2 
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by taxon "+searchuri);
		LOGGER.info("Search by taxon result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue("No models found for taxon ", result.getN()>0);
	}
	
	@Test
	public final void testSearchGetByTaxonURI() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("taxon", "http://purl.obolibrary.org/obo/NCBITaxon_7955");//worm 6239 14 models //9606 2 zebrafish 7955 2 
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by taxon "+searchuri);
		LOGGER.info("Search by taxon result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue("No models found for taxon ", result.getN()>0);
	}
	
	@Test
	public final void testSearchGetByTitle() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		//builder.addParameter("title", "*test*");
		builder.addParameter("title", "GO_shapes Activity unit test "); //gcy-8 . GO_shapes Activity unit test 37 (results in specification of)
		builder.addParameter("debug", "");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by title text URI "+searchuri);
		LOGGER.info("Search by title text result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue(result.getN()>0);
	}	
	
	@Test
	public final void testSearchGetByPMID() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("pmid", "PMID:1457892");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by PMID URI "+searchuri);
		LOGGER.info("Search by PMID result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue(result.getN()>0);
	}
	
	//&state=development&state=review {development, production, closed, review, delete} or operator	
	@Test
	public final void testSearchGetByState() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("state", "development");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by state URI "+searchuri);
		LOGGER.info("Search by state result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue(result.getN()>0);
	}
	
	@Test
	public final void testSearchGetByContributors() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("contributor", "http://orcid.org/0000-0002-1706-4196");
		
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by contributor URI "+searchuri);
		LOGGER.info("Search by contributor "+json_result);
		LOGGER.info("N models found: "+result.getN());
		
		builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("contributor", "http://orcid.org/0000-0003-1813-6857");		
		searchuri = builder.build();
		json_result = getJsonStringFromUri(searchuri);
		g = new Gson();
		result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by contributor URI "+searchuri);
		LOGGER.info("Search by contributor "+json_result);
		LOGGER.info("N models found: "+result.getN());
		
		builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("contributor", "http://orcid.org/0000-0002-8688-6599");		
		searchuri = builder.build();
		json_result = getJsonStringFromUri(searchuri);
		g = new Gson();
		result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by contributor URI "+searchuri);
		LOGGER.info("Search by contributor "+json_result);
		LOGGER.info("N models found: "+result.getN());
		
		builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("contributor", "http://orcid.org/0000-0002-1706-4196");
		builder.addParameter("contributor", "http://orcid.org/0000-0003-1813-6857");	
		builder.addParameter("contributor", "http://orcid.org/0000-0002-8688-6599");	
		searchuri = builder.build();
		json_result = getJsonStringFromUri(searchuri);
		g = new Gson();
		result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by multi contributor URI "+searchuri);
		LOGGER.info("Search by multi contributor "+json_result);
		LOGGER.info("N models found: "+result.getN());
		
		assertTrue(result.getN()>0);
	}
	
	@Test
	public final void testSearchGetByGroups() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("group", "http://geneontology.org"); //http://www.igs.umaryland.edu "http://www.wormbase.org"
		builder.addParameter("group", "http://www.igs.umaryland.edu");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by group URI "+searchuri);
		LOGGER.info("Search by group "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue(result.getN()>0);
	}
	
	@Test
	public final void testSearchGetByDate() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("date", "2018-08-20");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by start date URI "+searchuri);
		LOGGER.info("Search by start date "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue(result.getN()>0);
	}
	
	@Test
	public final void testSearchGetByDateRange() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("date", "2018-08-20");
		builder.addParameter("dateend", "2019-12-02");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by date range URI "+searchuri);
		LOGGER.info("Search by date range result "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue(result.getN()>0);
	}
	
	@Test
	public final void testSearchGetByExactDate() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("exactdate", "2020-02-07");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		LOGGER.info("Search by EXACT date URI "+searchuri);
		LOGGER.info("Search by EXACT date "+json_result);
		LOGGER.info("N models found: "+result.getN());
		assertTrue(result.getN()>0);
	}
	
	@Test
	public final void testSearchGetByDateAndOffset() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("date", "2018-08-20");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result1 = g.fromJson(json_result, ModelSearchResult.class);
		int n1 = result1.getN();
		builder.addParameter("offset", "1");
		searchuri = builder.build();
		json_result = getJsonStringFromUri(searchuri);
		ModelSearchResult result2 = g.fromJson(json_result, ModelSearchResult.class);
		int n2 = result2.getN();
		assertTrue(n1>n2);
	}
	
	@Test
	public final void testSearchGetByDateAndCount() throws URISyntaxException, IOException {
		//make the request
		URIBuilder builder = new URIBuilder("http://127.0.0.1:6800/search/");
		builder.addParameter("date", "2018-08-20");
		builder.addParameter("count", "");
		URI searchuri = builder.build();
		String json_result = getJsonStringFromUri(searchuri);
		Gson g = new Gson();
		ModelSearchResult result = g.fromJson(json_result, ModelSearchResult.class);
		assertTrue(result.getN()>0);
		LOGGER.info("N models found by count query: "+result.getN());
		assertTrue(result.getModels()==null);
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
