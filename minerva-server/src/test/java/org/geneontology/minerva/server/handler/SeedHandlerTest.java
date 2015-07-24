package org.geneontology.minerva.server.handler;

import static org.junit.Assert.*;

import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.UndoAwareMolecularModelManager.UndoMetadata;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.server.handler.JsonOrJsonpSeedHandler;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse;
import org.geneontology.minerva.server.handler.M3SeedHandler.SeedRequest;
import org.geneontology.minerva.server.handler.M3SeedHandler.SeedResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;

import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class SeedHandlerTest {

	private final static CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
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
		
		models = new UndoAwareMolecularModelManager(graph, new ElkReasonerFactory(), curieHandler,
				"http://model.geneontology.org/", "gomodel:");
		handler = new JsonOrJsonpSeedHandler(models, golr, null);
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
		
		String json = toJson(response.data);
		System.out.println("-----------");
		System.out.println(json);
		System.out.println("-----------");
	}
	
	private SeedResponse seed(String process, String taxon) throws Exception {
		SeedRequest request = new SeedRequest();
		request.modelId = generateBlankModel();
		request.process = process;
		request.taxon = taxon;
		return seed(request);
	}
	
	private SeedResponse seed(SeedRequest request) {
		SeedResponse response = handler.fromProcessGetPrivileged(uid, intention, packetId, request);
		assertEquals(uid, response.uid);
		assertEquals(intention, response.intention);
		assertEquals(response.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response.messageType);
		return response;
	}
	
	private String generateBlankModel() throws Exception {
		UndoMetadata metadata = new UndoMetadata(uid);
		ModelContainer model = models.generateBlankModel(metadata);
		return model.getModelId();
	}
	
	private String toJson(Object data) {
		String json = MolecularModelJsonRenderer.renderToJson(data, true);
		return json;
	}
}
