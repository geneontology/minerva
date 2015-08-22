package org.geneontology.minerva.server.handler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Server;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.server.StartUpTool;
import org.geneontology.minerva.server.StartUpTool.MinervaStartUpConfig;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class LocalServerTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private static CurieHandler curieHandler = null;
	private static UndoAwareMolecularModelManager models = null;
	private static Server server = null;
	private static String urlPrefix;



	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		init(new ParserWrapper());
	}
	
	@AfterClass
	public static void afterClass() throws Exception {
		server.stop();
		server.destroy();
	}

	@Before
	public void before() throws IOException {
		models.setPathToOWLFiles(folder.newFolder().getCanonicalPath());
	}
	
	@After
	public void after() {
		models.dispose();
	}
	
	static void init(ParserWrapper pw) throws Exception {
		final OWLGraphWrapper graph = pw.parseToOWLGraph("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl");
		// curie handler
		final String modelIdcurie = "gomodel";
		final String modelIdPrefix = "http://model.geneontology.org/";
		final CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap(modelIdcurie, modelIdPrefix));
		curieHandler = new MappedCurieHandler(DefaultCurieHandler.getMappings(), localMappings);
		OWLReasonerFactory rf = new ElkReasonerFactory();
		models = new UndoAwareMolecularModelManager(graph, rf, curieHandler, modelIdPrefix);
		
		MinervaStartUpConfig conf = new MinervaStartUpConfig();
		conf.useReasoner = true;
		conf.useModuleReasoner = false;
		conf.useRequestLogging = true;
		conf.checkLiteralIds = false;
		conf.lookupService = null;
		conf.importantRelations = null;
		conf.port = 6800;
		conf.contextString = "/";
		server = StartUpTool.startUp(models, conf);
		urlPrefix = "http://localhost:"+conf.port+conf.contextString;
	}
	
	@Test
	public void testLongGet() throws Exception {
		String longGetSuffix = FileUtils.readFileToString(new File("src/test/resources/server-test/long-get.txt"));
		String urlString = urlPrefix + longGetSuffix;
		URL url = new URL(urlString);
		InputStream stream = url.openStream();
		List<String> lines = IOUtils.readLines(stream);
		for (String string : lines) {
			System.out.println(string);
		}
	}
}
