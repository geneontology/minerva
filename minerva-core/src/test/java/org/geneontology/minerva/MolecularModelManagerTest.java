package org.geneontology.minerva;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLNamedIndividual;

import owltools.OWLToolsTestBasics;
import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class MolecularModelManagerTest extends OWLToolsTestBasics {

	// JUnit way of creating a temporary test folder
	// will be deleted after the test has run, by JUnit.
	@Rule
    public TemporaryFolder folder = new TemporaryFolder();
	
	MolecularModelManager<Void> mmm;

	static{
		Logger.getLogger("org.semanticweb.elk").setLevel(Level.ERROR);
	}
	

	private String renderJSON(String modelId) throws UnknownIdentifierException {

		ModelContainer model = mmm.getModel(modelId);
		String js = MolecularModelJsonRenderer.renderToJson(model.getAboxOntology(), null, true);
		return js;
	}

	@Test
	public void testDeleteIndividual() throws Exception {
		ParserWrapper pw = new ParserWrapper();
		OWLGraphWrapper g = pw.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));

		// GO:0038024 ! cargo receptor activity
		// GO:0042803 ! protein homodimerization activity

		mmm = new MolecularModelManager<Void>(g, "http://testmodel.geneontology.org/");

		String modelId = mmm.generateBlankModel(null);
		String i1 = mmm.createIndividual(modelId, "GO:0038024", null, null).getKey();

		String i2 = mmm.createIndividual(modelId, "GO:0042803", null, null).getKey();

		mmm.addPartOf(modelId, i1, i2, null);

		//		String js = renderJSON(modelId);
		//		System.out.println("-------------");
		//		System.out.println("INDS:" + js);
		//		
		//		System.out.println("-------------");

		mmm.deleteIndividual(modelId, i2, null);

		//		js = renderJSON(modelId);
		//		System.out.println("INDS:" + js);
		//		System.out.println("-------------");

		Set<OWLNamedIndividual> individuals = mmm.getIndividuals(modelId);
		assertEquals(1, individuals.size());
	}

	@Test
	public void testExportImport() throws Exception {
		ParserWrapper pw = new ParserWrapper();
		OWLGraphWrapper g = pw.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));

		// GO:0038024 ! cargo receptor activity
		// GO:0042803 ! protein homodimerization activity
		// GO:0008233 ! peptidase activity

		mmm = new MolecularModelManager<Void>(g, "http://testmodel.geneontology.org/");

		final String modelId = mmm.generateBlankModel(null);
		final Pair<String,OWLNamedIndividual> i1 = mmm.createIndividual(modelId, "GO:0038024", null, null);

		final Pair<String,OWLNamedIndividual> i2 = mmm.createIndividual(modelId, "GO:0042803", null, null);

		mmm.addPartOf(modelId, i1.getKey(), i2.getKey(), null);
		
		// export
		final String modelContent = mmm.exportModel(modelId);
		System.out.println("-------------------");
		System.out.println(modelContent);
		System.out.println("-------------------");
		
		// add an additional individual to model after export
		final Pair<String,OWLNamedIndividual> i3 = mmm.createIndividual(modelId, "GO:0008233", null, null);
		assertEquals(3, mmm.getIndividuals(modelId).size());

		
		// import
		final String modelId2 = mmm.importModel(modelContent);
		
		assertEquals(modelId, modelId2);
		Set<OWLNamedIndividual> loaded = mmm.getIndividuals(modelId2);
		assertEquals(2, loaded.size());
		for (OWLNamedIndividual i : loaded) {
			IRI iri = i.getIRI();
			// check that the model only contains the individuals created before the export
			assertTrue(iri.equals(i1.getRight().getIRI()) || iri.equals(i2.getRight().getIRI()));
			assertFalse(iri.equals(i3.getRight().getIRI()));
		}
	}
	
	@Test
	public void testSaveModel() throws Exception {
		final File saveFolder = folder.newFolder();
		final ParserWrapper pw1 = new ParserWrapper();
		OWLGraphWrapper g = pw1.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));

		mmm = new MolecularModelManager<Void>(g, "http://testmodel.geneontology.org/");
		mmm.setPathToOWLFiles(saveFolder.getCanonicalPath());
		
		// GO:0038024 ! cargo receptor activity
		// GO:0042803 ! protein homodimerization activity
		// GO:0008233 ! peptidase activity

		final String modelId = mmm.generateBlankModel(null);
		final Pair<String,OWLNamedIndividual> i1 = mmm.createIndividual(modelId, "GO:0038024", null, null);

		final Pair<String,OWLNamedIndividual> i2 = mmm.createIndividual(modelId, "GO:0042803", null, null);

		mmm.addPartOf(modelId, i1.getKey(), i2.getKey(), null);
		
		// save
		mmm.saveModel(modelId, null, null);
		
		// add an additional individual to model after export
		final Pair<String,OWLNamedIndividual> i3 = mmm.createIndividual(modelId, "GO:0008233", null, null);
		assertEquals(3, mmm.getIndividuals(modelId).size());

		// discard mmm
		mmm.dispose();
		mmm = null;
		
		final ParserWrapper pw2 = new ParserWrapper();
		g = pw2.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));
		
		
		mmm = new MolecularModelManager<Void>(g, "http://testmodel.geneontology.org/");
		mmm.setPathToOWLFiles(saveFolder.getCanonicalPath());
		
		Set<String> availableModelIds = mmm.getAvailableModelIds();
		assertTrue(availableModelIds.contains(modelId));
		
		final ModelContainer model = mmm.getModel(modelId);
		assertNotNull(model);
		
		Collection<OWLNamedIndividual> loaded = mmm.getIndividuals(modelId);
		assertEquals(2, loaded.size());
		for (OWLNamedIndividual i : loaded) {
			IRI iri = i.getIRI();
			// check that the model only contains the individuals created before the save
			assertTrue(iri.equals(i1.getRight().getIRI()) || iri.equals(i2.getRight().getIRI()));
			assertFalse(iri.equals(i3.getRight().getIRI()));
		}
	}

	@Test
	public void testInferredType() throws Exception {
		ParserWrapper pw = new ParserWrapper();
		OWLGraphWrapper g = pw.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));

		// GO:0038024 ! cargo receptor activity
		// GO:0042803 ! protein homodimerization activity

		mmm = new MolecularModelManager<Void>(g, "http://testmodel.geneontology.org/");

		String modelId = mmm.generateBlankModel(null);
		String cc = mmm.createIndividual(modelId, "GO:0004872", null, null).getKey(); // receptor activity

		
		String mit = mmm.createIndividual(modelId, "GO:0007166", null, null).getKey(); // cell surface receptor signaling pathway

		mmm.addPartOf(modelId, mit, cc, null);

		// we expect inference to be to: GO:0038023  signaling receptor activity
		// See discussion here: https://github.com/kltm/go-mme/issues/3

		System.out.println(renderJSON(modelId));
		//List<Map<Object, Object>> gson = mmm.getIndividualObjects(modelId);
		//assertEquals(1, individuals.size());
	}

}
