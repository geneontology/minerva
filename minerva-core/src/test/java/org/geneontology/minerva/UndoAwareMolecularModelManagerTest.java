package org.geneontology.minerva;

import static org.junit.Assert.*;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.UndoAwareMolecularModelManager.ChangeEvent;
import org.geneontology.minerva.UndoAwareMolecularModelManager.UndoMetadata;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.json.JsonOwlIndividual;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;

import owltools.OWLToolsTestBasics;
import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class UndoAwareMolecularModelManagerTest extends OWLToolsTestBasics {

	static OWLGraphWrapper g = null;
	static CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
	static UndoAwareMolecularModelManager m3 = null;
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		ParserWrapper pw = new ParserWrapper();
		g = pw.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));
		m3 = new UndoAwareMolecularModelManager(g, new ElkReasonerFactory(), curieHandler,
				"http://testmodel.geneontology.org/");
	}
	
	@AfterClass
	public static void afterClass() throws Exception {
		if (m3 != null) {
			m3.dispose();
		}
	}
	
	@Test
	public void testUndoRedo() throws Exception {
		String userId = "test-user-id";
		ModelContainer model = m3.generateBlankModel(null);
		// GO:0001158 ! enhancer sequence-specific DNA binding
		OWLNamedIndividual bindingIdividual = m3.createIndividual(model.getModelId(), "GO:0001158", null, new UndoMetadata(userId));
		String bindingId = bindingIdividual.getIRI().toString();
		// BFO:0000066 GO:0005654 ! occurs_in nucleoplasm
		m3.addType(model.getModelId(), bindingId, "BFO:0000066", "GO:0005654", new UndoMetadata(userId));
		
		MolecularModelJsonRenderer renderer = new MolecularModelJsonRenderer(model, null, curieHandler);
		JsonOwlIndividual render1 = renderer.renderObject(bindingIdividual);
		assertEquals(2, render1.type.length);
		
		// check event count
		Pair<List<ChangeEvent>,List<ChangeEvent>> undoRedoEvents = m3.getUndoRedoEvents(model.getModelId());
		List<ChangeEvent> undoEvents = undoRedoEvents.getLeft();
		List<ChangeEvent> redoEvents = undoRedoEvents.getRight();
		assertEquals(0, redoEvents.size());
		assertEquals(2, undoEvents.size());
		
		// undo
		assertTrue(m3.undo(model, userId));
		
		JsonOwlIndividual render2 = renderer.renderObject(bindingIdividual);
		assertEquals(1, render2.type.length);
		
		// redo
		assertTrue(m3.redo(model, userId));
		JsonOwlIndividual render3 = renderer.renderObject(bindingIdividual);
		assertEquals(2, render3.type.length);
		
		// undo again
		assertTrue(m3.undo(model, userId));
		JsonOwlIndividual render4 = renderer.renderObject(bindingIdividual);
		assertEquals(1, render4.type.length);
		
		// add new type
		// GO:0001664 ! G-protein coupled receptor binding
		m3.addType(model.getModelId(), bindingId, "GO:0001664", new UndoMetadata(userId));
		
		// redo again, should fail
		assertFalse(m3.redo(model, userId));
	}

	static void printToJson(Object obj) {
		String json = MolecularModelJsonRenderer.renderToJson(obj, true);
		System.out.println("---------");
		System.out.println(json);
		System.out.println("---------");
	}
	
}
