package org.geneontology.minerva.legacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import owltools.gaf.Bioentity;
import owltools.gaf.BioentityDocument;
import owltools.gaf.GafDocument;
import owltools.gaf.GeneAnnotation;
import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.SimpleEcoMapper;
import owltools.io.ParserWrapper;

public class LegoToGeneAnnotationTranslatorTest {
	
	public static ParserWrapper pw;
	public static CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
	public static OWLReasonerFactory rf = new ElkReasonerFactory();
	public static SimpleEcoMapper mapper;

	@BeforeClass
	public static void beforeClass() throws Exception {
		pw = new ParserWrapper();
		mapper = EcoMapperFactory.createSimple();
	}

	@Test
	public void testZfinExample() throws Exception {
		OWLOntology model = loadModel("gomodel-1.owl");
		Pair<GafDocument,BioentityDocument> pair = translate(model, "gomodel-1");
		GafDocument gafDocument = pair.getLeft();
		List<GeneAnnotation> allAnnotations = gafDocument.getGeneAnnotations();
		assertFalse(allAnnotations.isEmpty());
		
		// check that all annotations have evidence info
		List<GeneAnnotation> noEvidence = new ArrayList<>();
		List<GeneAnnotation> noShortEvidence = new ArrayList<>();
		Set<String> unmappedEco = new HashSet<>();
		
		for(GeneAnnotation ann : allAnnotations) {
			
			String ecoEvidenceCls = ann.getEcoEvidenceCls();
			String shortEvidence = ann.getShortEvidence();
			if (ecoEvidenceCls == null) {
				noEvidence.add(ann);
			}
			else if (shortEvidence == null) {
				unmappedEco.add(ecoEvidenceCls);
				noShortEvidence.add(ann);
			}
		}
		
		BioentityDocument bioentityDocument = pair.getRight();
		
		// expect two entities
		List<Bioentity> bioentities = bioentityDocument.getBioentities();
		assertEquals(2, bioentities.size());
		
		// check that all entities have a taxon id
		List<Bioentity> noTaxon = new ArrayList<>();
		
		for (Bioentity entity : bioentities) {
			if (entity.getNcbiTaxonId() == null) {
				noTaxon.add(entity);
			}
		}
		assertTrue(noTaxon.isEmpty());
		
		assertTrue(noEvidence.isEmpty());
		assertEquals(2, unmappedEco.size());
		assertTrue(unmappedEco.contains("ECO:0000302"));
		assertTrue(unmappedEco.contains("ECO:0000011"));
		assertEquals(2, noShortEvidence.size());
	}
	
	@Test
	public void testWithField() throws Exception {
		OWLOntology model = loadModel("gomodel-2.owl");
		Pair<GafDocument,BioentityDocument> pair = translate(model, "gomodel-2");
		
		BioentityDocument bioentityDocument = pair.getRight();
		
		// expect two entities
		List<Bioentity> bioentities = bioentityDocument.getBioentities();
		assertEquals(2, bioentities.size());
		
		// expect three annotations, one providing with infos
		List<GeneAnnotation> allAnnotations = pair.getLeft().getGeneAnnotations();
		List<GeneAnnotation> withAnnotations = new ArrayList<GeneAnnotation>();
		assertEquals(3, allAnnotations.size());
		for(GeneAnnotation ann : allAnnotations) {
			Collection<String> withInfos = ann.getWithInfos();
			if (withInfos != null && !withInfos.isEmpty()) {
				withAnnotations.add(ann);
			}
		}
		assertEquals(1, withAnnotations.size());
	}
	
	private OWLOntology loadModel(String name) throws Exception {
		OWLOntology model = pw.parseOWL(IRI.create(new File("src/test/resources/"+name).getCanonicalFile()));
		return model;
	}
	
	private Pair<GafDocument,BioentityDocument> translate(OWLOntology model, String id) {
		LegoToGeneAnnotationTranslator t = new LegoToGeneAnnotationTranslator(model, curieHandler, mapper);
		return t.translate(id, model, null);
	}

}
