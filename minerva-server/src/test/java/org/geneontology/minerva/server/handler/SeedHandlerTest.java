package org.geneontology.minerva.server.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.Collections;

import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse;
import org.geneontology.minerva.server.handler.M3SeedHandler.SeedRequest;
import org.geneontology.minerva.server.handler.M3SeedHandler.SeedRequestArgument;
import org.geneontology.minerva.server.handler.M3SeedHandler.SeedResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.SimpleEcoMapper;
import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class SeedHandlerTest {

	private static CurieHandler curieHandler = null;
	private static JsonOrJsonpSeedHandler handler = null;
	private static UndoAwareMolecularModelManager models = null;
	
	private static final String uid = "test-user";
	private static final String intention = "test-intention";
	private static final String packetId = "foo-packet-id";
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		init(new ParserWrapper(), "http://golr.geneontology.org/solr");
	}

	static void init(ParserWrapper pw, String golr) throws Exception {
		final OWLGraphWrapper graph = pw.parseToOWLGraph("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl");
		// curie handler
		final String modelIdcurie = "gomodel";
		final String modelIdPrefix = "http://model.geneontology.org/";
		final CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap(modelIdcurie, modelIdPrefix));
		curieHandler = new MappedCurieHandler(DefaultCurieHandler.getMappings(), localMappings);
		models = new UndoAwareMolecularModelManager(graph, curieHandler, modelIdPrefix);
		SimpleEcoMapper ecoMapper = EcoMapperFactory.createSimple();
		handler = new JsonOrJsonpSeedHandler(models, "unknown", golr, ecoMapper) {

			@Override
			protected void logGolrRequest(URI uri) {
				System.err.println(uri);
			}
			
		};
	}
	
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if (handler != null) {
			handler = null;
		}
		if (models != null) {
			models.dispose();
		}
	}
	
	@Test
	public void test1() throws Exception {
		// B cell apoptotic process
		// mouse
		SeedResponse response = seed("GO:0001783", "NCBITaxon:10090");
		assertNotNull(response.data.id);
		ModelContainer model = models.getModel(curieHandler.getIRI(response.data.id));
		assertNotNull(model);
		MolecularModelJsonRenderer renderer = new MolecularModelJsonRenderer(model, null, curieHandler);
		String json = toJson(renderer.renderModel());
		System.out.println("-----------");
		System.out.println(json);
		System.out.println("-----------");
	}
	
	private SeedResponse seed(String process, String taxon) throws Exception {
		SeedRequest request = new SeedRequest();
		request.arguments = new SeedRequestArgument();
		request.arguments.process = process;
		request.arguments.taxon = taxon;
		return seed(request);
	}
	
	private SeedResponse seed(SeedRequest request) {
		String json = MolecularModelJsonRenderer.renderToJson(new SeedRequest[]{request}, false);
		SeedResponse response = handler.fromProcessGetPrivileged(uid, intention, packetId, json);
		assertEquals(uid, response.uid);
		assertEquals(intention, response.intention);
		assertEquals(response.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response.messageType);
		return response;
	}
	
	private String toJson(Object data) {
		String json = MolecularModelJsonRenderer.renderToJson(data, true);
		return json;
	}
}
