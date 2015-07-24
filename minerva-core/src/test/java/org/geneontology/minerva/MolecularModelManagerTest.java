package org.geneontology.minerva;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import owltools.OWLToolsTestBasics;
import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class MolecularModelManagerTest extends OWLToolsTestBasics {

	// JUnit way of creating a temporary test folder
	// will be deleted after the test has run, by JUnit.
	@Rule
    public TemporaryFolder folder = new TemporaryFolder();
	private final OWLReasonerFactory rf = new ElkReasonerFactory();
	private final CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
	
	static{
		Logger.getLogger("org.semanticweb.elk").setLevel(Level.ERROR);
	}
	
	private MolecularModelManager<Void> createM3(OWLGraphWrapper g) throws OWLOntologyCreationException {
		return new MolecularModelManager<Void>(g, rf, curieHandler, "http://testmodel.geneontology.org/", "testmodel:");
	}

	@Test
	public void testDeleteIndividual() throws Exception {
		ParserWrapper pw = new ParserWrapper();
		OWLGraphWrapper g = pw.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));

		// GO:0038024 ! cargo receptor activity
		// GO:0042803 ! protein homodimerization activity

		MolecularModelManager<Void> mmm = createM3(g);

		ModelContainer model = mmm.generateBlankModel(null);
		OWLNamedIndividual i1 = mmm.createIndividual(model.getModelId(), "GO:0038024", null, null);

		OWLNamedIndividual i2 = mmm.createIndividual(model.getModelId(), "GO:0042803", null, null);

		addPartOf(model, i1, i2, mmm, g);

		//		String js = renderJSON(modelId);
		//		System.out.println("-------------");
		//		System.out.println("INDS:" + js);
		//		
		//		System.out.println("-------------");

		mmm.deleteIndividual(model, i2, null);

		//		js = renderJSON(modelId);
		//		System.out.println("INDS:" + js);
		//		System.out.println("-------------");

		Set<OWLNamedIndividual> individuals = mmm.getIndividuals(model.getModelId());
		assertEquals(1, individuals.size());
	}

	@Test
	public void testExportImport() throws Exception {
		ParserWrapper pw = new ParserWrapper();
		OWLGraphWrapper g = pw.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));

		// GO:0038024 ! cargo receptor activity
		// GO:0042803 ! protein homodimerization activity
		// GO:0008233 ! peptidase activity

		MolecularModelManager<Void> mmm = createM3(g);

		final ModelContainer model = mmm.generateBlankModel(null);
		final OWLNamedIndividual i1 = mmm.createIndividual(model.getModelId(), "GO:0038024", null, null);

		final OWLNamedIndividual i2 = mmm.createIndividual(model.getModelId(), "GO:0042803", null, null);

		addPartOf(model, i1, i2, mmm, g);
		
		// export
		final String modelContent = mmm.exportModel(model);
		System.out.println("-------------------");
		System.out.println(modelContent);
		System.out.println("-------------------");
		
		// add an additional individual to model after export
		final OWLNamedIndividual i3 = mmm.createIndividual(model.getModelId(), "GO:0008233", null, null);
		assertEquals(3, mmm.getIndividuals(model.getModelId()).size());

		
		// import
		final ModelContainer modelId2 = mmm.importModel(modelContent);
		
		assertEquals(model.getModelId(), modelId2.getModelId());
		Set<OWLNamedIndividual> loaded = mmm.getIndividuals(modelId2.getModelId());
		assertEquals(2, loaded.size());
		for (OWLNamedIndividual i : loaded) {
			IRI iri = i.getIRI();
			// check that the model only contains the individuals created before the export
			assertTrue(iri.equals(i1.getIRI()) || iri.equals(i2.getIRI()));
			assertFalse(iri.equals(i3.getIRI()));
		}
	}
	
	@Test
	public void testSaveModel() throws Exception {
		final File saveFolder = folder.newFolder();
		final ParserWrapper pw1 = new ParserWrapper();
		OWLGraphWrapper g = pw1.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));

		MolecularModelManager<Void> mmm = createM3(g);
		mmm.setPathToOWLFiles(saveFolder.getCanonicalPath());
		
		// GO:0038024 ! cargo receptor activity
		// GO:0042803 ! protein homodimerization activity
		// GO:0008233 ! peptidase activity

		final ModelContainer model = mmm.generateBlankModel(null);
		final OWLNamedIndividual i1 = mmm.createIndividual(model.getModelId(), "GO:0038024", null, null);

		final OWLNamedIndividual i2 = mmm.createIndividual(model.getModelId(), "GO:0042803", null, null);

		addPartOf(model, i1, i2, mmm, g);
		
		// save
		mmm.saveModel(model, null, null);
		
		// add an additional individual to model after export
		final OWLNamedIndividual i3 = mmm.createIndividual(model.getModelId(), "GO:0008233", null, null);
		assertEquals(3, mmm.getIndividuals(model.getModelId()).size());

		// discard mmm
		mmm.dispose();
		mmm = null;
		
		final ParserWrapper pw2 = new ParserWrapper();
		g = pw2.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));
		
		
		mmm = createM3(g);
		mmm.setPathToOWLFiles(saveFolder.getCanonicalPath());
		
		Map<String, String> availableModelIds = mmm.getAvailableModelIds();
		assertTrue(availableModelIds.containsKey(model.getModelId()));
		
		final ModelContainer model2 = mmm.getModel(model.getModelId());
		assertNotNull(model2);
		
		Collection<OWLNamedIndividual> loaded = mmm.getIndividuals(model2.getModelId());
		assertEquals(2, loaded.size());
		for (OWLNamedIndividual i : loaded) {
			IRI iri = i.getIRI();
			// check that the model only contains the individuals created before the save
			assertTrue(iri.equals(i1.getIRI()) || iri.equals(i2.getIRI()));
			assertFalse(iri.equals(i3.getIRI()));
		}
	}

	@Test
	public void testInferredType() throws Exception {
		ParserWrapper pw = new ParserWrapper();
		OWLGraphWrapper g = pw.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));

		// GO:0038024 ! cargo receptor activity
		// GO:0042803 ! protein homodimerization activity

		MolecularModelManager<Void> mmm = createM3(g);

		ModelContainer model = mmm.generateBlankModel(null);
		OWLNamedIndividual cc = mmm.createIndividual(model.getModelId(), "GO:0004872", null, null); // receptor activity

		
		OWLNamedIndividual mit = mmm.createIndividual(model.getModelId(), "GO:0007166", null, null); // cell surface receptor signaling pathway

		addPartOf(model, mit, cc, mmm, g);

		// we expect inference to be to: GO:0038023  signaling receptor activity
		// See discussion here: https://github.com/kltm/go-mme/issues/3

		//List<Map<Object, Object>> gson = mmm.getIndividualObjects(modelId);
		//assertEquals(1, individuals.size());
	}
	
	private static void addPartOf(ModelContainer model, OWLNamedIndividual i1, OWLNamedIndividual i2, 
			MolecularModelManager<Void> m3, OWLGraphWrapper g) {
		final OWLObjectProperty partOf = g.getOWLObjectPropertyByIdentifier("BFO:0000050");
		assertNotNull(partOf);
		m3.addFact(model, partOf, i1, i2, Collections.<OWLAnnotation>emptySet(), null);
	}

}
