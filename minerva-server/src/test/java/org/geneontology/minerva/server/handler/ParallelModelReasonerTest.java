package org.geneontology.minerva.server.handler;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.server.handler.M3BatchHandler.Entity;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3Argument;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3Request;
import org.geneontology.minerva.server.handler.M3BatchHandler.Operation;
import org.geneontology.minerva.server.inferences.CachingInferenceProviderCreatorImpl;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class ParallelModelReasonerTest {

	private static CurieHandler curieHandler = null;
	private static JsonOrJsonpBatchHandler handler = null;
	private static UndoAwareMolecularModelManager models = null;
	private static CountingCachingInferenceProvider ipc;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		init(new ParserWrapper());
	}
	
	static void init(ParserWrapper pw) throws OWLOntologyCreationException, IOException {
		final OWLGraphWrapper graph = pw.parseToOWLGraph("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl");
		
		// curie handler
		final String modelIdcurie = "gomodel";
		final String modelIdPrefix = "http://model.geneontology.org/";
		final CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap(modelIdcurie, modelIdPrefix));
		curieHandler = new MappedCurieHandler(DefaultCurieHandler.getMappings(), localMappings);
		
		models = new UndoAwareMolecularModelManager(graph, curieHandler, modelIdPrefix);
		ipc = new CountingCachingInferenceProvider(false);
		handler = new JsonOrJsonpBatchHandler(models, "development", ipc ,
				Collections.<OWLObjectProperty>emptySet(), (ExternalLookupService) null);
		models.setPathToOWLFiles("src/test/resources/reasoner-test");
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
	
	@Before
	public void before() {
		ipc.clear();
		models.dispose();
	}
	
	@Test
	public void testMostlyReadReasoner() throws Exception {
		List<DelayedRequestThread> threads = new ArrayList<>();
		threads.add(new ModifyingDelayedRequestThread(0));
		for (int i = 0; i < 10; i++) {
			threads.add(new DelayedRequestThread(i*100));
		}
		for (DelayedRequestThread thread : threads) {
			thread.start();
		}
		for (DelayedRequestThread thread : threads) {
			thread.join();
			validateResponse(thread.response);
		}
		System.out.println("Hit: "+ipc.hit+" Miss: "+ipc.miss);
		assertTrue(ipc.hit.get() >= 9); // most should be hits
		assertTrue(ipc.miss.get() >= 1); // at least one miss
	}
	
	@Ignore("Fails currently on the build server")
	@Test
	public void testMostlyModifyReasoner() throws Exception {
		List<DelayedRequestThread> threads = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			threads.add(new ModifyingDelayedRequestThread(i*100));
		}
		threads.add(new DelayedRequestThread(250));
		threads.add(new DelayedRequestThread(1050));
		
		for (DelayedRequestThread thread : threads) {
			thread.start();
		}
		for (DelayedRequestThread thread : threads) {
			thread.join();
			validateResponse(thread.response);
		}
		System.out.println("Hit: "+ipc.hit+" Miss: "+ipc.miss);
		assertTrue(ipc.miss.get() >= 10); // ten changes, at least ten cache miss
	}
	
	private static final class CountingCachingInferenceProvider extends CachingInferenceProviderCreatorImpl {
		
		final AtomicLong hit = new AtomicLong(0L);
		final AtomicLong miss = new AtomicLong(0L);
		
		private CountingCachingInferenceProvider(boolean useSLME) {
			super(new ElkReasonerFactory(), 1, useSLME, "Counting Caching ELK");
		}

		@Override
		protected void addHit() {
			hit.incrementAndGet();
		}

		@Override
		protected void addMiss() {
			miss.incrementAndGet();
		}
		
		protected void clear() {
			super.clear();
			hit.set(0);
			miss.set(0);
		}
	}

	private class ModifyingDelayedRequestThread extends DelayedRequestThread {
		private ModifyingDelayedRequestThread(int delay) {
			super(delay);
		}

		@Override
		protected List<M3Request> createBatch() {
			return createAddBatch();
		}
	}

	private class DelayedRequestThread extends Thread {

		private final long millis;
		private M3BatchResponse response;
		
		public DelayedRequestThread(int delay) {
			millis = delay;
		}

		@Override
		public void run() {
			// delay
			try {
				Thread.sleep(millis);
			} catch (InterruptedException e) {
				// ignore
			}
			// work
			List<M3Request> batch = createBatch();
			
			response = executeBatch(batch);
			
		}
		
		protected List<M3Request> createBatch() {
			return createReadBatch();
		}
	}
	
	private List<M3Request> createReadBatch() {
		List<M3Request> batch = new ArrayList<>();
		M3Request r;
		
		final String modelId = "http://model.geneontology.org/5525a0fc00000001";
		
		// get model
		r = new M3Request();
		r.entity = Entity.model;
		r.operation = Operation.get;
		r.arguments = new M3Argument();
		r.arguments.modelId = modelId;
		batch.add(r);
		
		return batch;
	}
	
	private List<M3Request> createAddBatch() {
		List<M3Request> batch = new ArrayList<>();
		M3Request r;
		
		final String modelId = "http://model.geneontology.org/5525a0fc00000001";
		
		// get model
		r = new M3Request();
		r.entity = Entity.individual;
		r.operation = Operation.add;
		r.arguments = new M3Argument();
		r.arguments.modelId = modelId;
		BatchTestTools.setExpressionClass(r.arguments, "GO:0003674");
		batch.add(r);
		
		return batch;
	}
	
	private void validateResponse(M3BatchResponse response) {
		assertEquals("test-user", response.uid);
		assertEquals("test-intention", response.intention);
		assertEquals(response.message, M3BatchResponse.MESSAGE_TYPE_SUCCESS, response.messageType);
	}
	
	private M3BatchResponse executeBatch(List<M3Request> batch) {
		M3BatchResponse response = handler.m3Batch("test-user", "test-intention", "foo-packet-id",
				batch.toArray(new M3Request[batch.size()]), true, true);
		return response;
	}
}
