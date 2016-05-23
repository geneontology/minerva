package org.geneontology.minerva;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import owltools.OWLToolsTestBasics;
import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;
import owltools.io.CatalogXmlIRIMapper;

public class MolecularModelManagerTest extends OWLToolsTestBasics {

	// JUnit way of creating a temporary test folder
	// will be deleted after the test has run, by JUnit.
	@Rule
    public TemporaryFolder folder = new TemporaryFolder();
	private final CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
	
	static{
		Logger.getLogger("org.semanticweb.elk").setLevel(Level.ERROR);
	}
	
	private MolecularModelManager<Void> createM3(OWLGraphWrapper g) throws OWLOntologyCreationException {
		return new MolecularModelManager<Void>(g, curieHandler, "http://testmodel.geneontology.org/");
	}

	@Test
	public void testDeleteIndividual() throws Exception {
		ParserWrapper pw = new ParserWrapper();

		// if available, set catalog
        String envCatalog = System.getenv().get("GENEONTOLOGY_CATALOG");
        if (envCatalog != null) {
        	pw.addIRIMapper(new CatalogXmlIRIMapper(envCatalog));
        }

		OWLGraphWrapper g = pw.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));

		// GO:0038024 ! cargo receptor activity
		// GO:0042803 ! protein homodimerization activity

		MolecularModelManager<Void> mmm = createM3(g);

		ModelContainer model = mmm.generateBlankModel(null);
		OWLNamedIndividual i1 = mmm.createIndividual(model.getModelId(), "GO:0038024", null, null);

		OWLNamedIndividual i2 = mmm.createIndividual(model.getModelId(), "GO:0042803", null, null);

		addPartOf(model, i1, i2, mmm);

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

		// if available, set catalog
        String envCatalog = System.getenv().get("GENEONTOLOGY_CATALOG");
        if (envCatalog != null) {
        	pw.addIRIMapper(new CatalogXmlIRIMapper(envCatalog));
        }

		OWLGraphWrapper g = pw.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));

		// GO:0038024 ! cargo receptor activity
		// GO:0042803 ! protein homodimerization activity
		// GO:0008233 ! peptidase activity

		MolecularModelManager<Void> mmm = createM3(g);

		final ModelContainer model = mmm.generateBlankModel(null);
		final OWLNamedIndividual i1 = mmm.createIndividual(model.getModelId(), "GO:0038024", null, null);

		final OWLNamedIndividual i2 = mmm.createIndividual(model.getModelId(), "GO:0042803", null, null);

		addPartOf(model, i1, i2, mmm);
		
		// export
		final String modelContent = mmm.exportModel(model);
		final IRI modelId1 = model.getModelId();
		
		// add an additional individual to model after export
		final OWLNamedIndividual i3 = mmm.createIndividual(model.getModelId(), "GO:0008233", null, null);
		assertEquals(3, mmm.getIndividuals(model.getModelId()).size());

		
		// import
		final ModelContainer model2 = mmm.importModel(modelContent);
		
		final String modelContent2 = mmm.exportModel(model2);
		assertEquals(modelContent, modelContent2);
		
		assertEquals(modelId1, model2.getModelId());
		Set<OWLNamedIndividual> loaded = mmm.getIndividuals(model2.getModelId());
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

		// if available, set catalog
        String envCatalog = System.getenv().get("GENEONTOLOGY_CATALOG");
        if (envCatalog != null) {
        	pw1.addIRIMapper(new CatalogXmlIRIMapper(envCatalog));
        }

		OWLGraphWrapper g = pw1.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));

		MolecularModelManager<Void> mmm = createM3(g);
		mmm.setPathToOWLFiles(saveFolder.getCanonicalPath());
		
		// GO:0038024 ! cargo receptor activity
		// GO:0042803 ! protein homodimerization activity
		// GO:0008233 ! peptidase activity

		final ModelContainer model = mmm.generateBlankModel(null);
		final OWLNamedIndividual i1 = mmm.createIndividual(model.getModelId(), "GO:0038024", null, null);

		final OWLNamedIndividual i2 = mmm.createIndividual(model.getModelId(), "GO:0042803", null, null);

		addPartOf(model, i1, i2, mmm);
		
		// save
		mmm.saveModel(model, null, null);
		
		// add an additional individual to model after export
		final OWLNamedIndividual i3 = mmm.createIndividual(model.getModelId(), "GO:0008233", null, null);
		assertEquals(3, mmm.getIndividuals(model.getModelId()).size());

		// discard mmm
		mmm.dispose();
		mmm = null;
		
		final ParserWrapper pw2 = new ParserWrapper();

		// if available, set catalog
        if (envCatalog != null) {
        	pw2.addIRIMapper(new CatalogXmlIRIMapper(envCatalog));
        }

		g = pw2.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));
		
		
		mmm = createM3(g);
		mmm.setPathToOWLFiles(saveFolder.getCanonicalPath());
		
		Set<IRI> availableModelIds = mmm.getAvailableModelIds();
		assertTrue(availableModelIds.contains(model.getModelId()));
		
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

		// if available, set catalog
        String envCatalog = System.getenv().get("GENEONTOLOGY_CATALOG");
        if (envCatalog != null) {
        	pw.addIRIMapper(new CatalogXmlIRIMapper(envCatalog));
        }

		OWLGraphWrapper g = pw.parseToOWLGraph(getResourceIRIString("go-mgi-signaling-test.obo"));

		// GO:0038024 ! cargo receptor activity
		// GO:0042803 ! protein homodimerization activity

		MolecularModelManager<Void> mmm = createM3(g);

		ModelContainer model = mmm.generateBlankModel(null);
		OWLNamedIndividual cc = mmm.createIndividual(model.getModelId(), "GO:0004872", null, null); // receptor activity

		
		OWLNamedIndividual mit = mmm.createIndividual(model.getModelId(), "GO:0007166", null, null); // cell surface receptor signaling pathway

		addPartOf(model, mit, cc, mmm);

		// we expect inference to be to: GO:0038023  signaling receptor activity
		// See discussion here: https://github.com/kltm/go-mme/issues/3

		//List<Map<Object, Object>> gson = mmm.getIndividualObjects(modelId);
		//assertEquals(1, individuals.size());
	}
	
	private void addPartOf(ModelContainer model, OWLNamedIndividual i1, OWLNamedIndividual i2, 
			MolecularModelManager<Void> m3) {
		IRI partOfIRI = curieHandler.getIRI("BFO:0000050");
		final OWLObjectProperty partOf = model.getOWLDataFactory().getOWLObjectProperty(partOfIRI);
		m3.addFact(model, partOf, i1, i2, Collections.<OWLAnnotation>emptySet(), null);
	}

}
