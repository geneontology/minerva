package org.geneontology.minerva.legacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.geneontology.minerva.lookup.TableLookupService;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.SimpleIRIMapper;

import owltools.gaf.Bioentity;
import owltools.gaf.GafDocument;
import owltools.gaf.GeneAnnotation;
import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.SimpleEcoMapper;
import owltools.io.CatalogXmlIRIMapper;
import owltools.io.ParserWrapper;

public class LegoToGeneAnnotationTranslatorTest {
	
	public static ParserWrapper pw;
	public static CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
	public static OWLReasonerFactory rf = new ElkReasonerFactory();
	public static SimpleEcoMapper mapper;

	@BeforeClass
	public static void beforeClass() throws Exception {
		pw = new ParserWrapper();
		
		pw.addIRIMapper(new CatalogXmlIRIMapper(new File("src/test/resources/catalog-v001.xml")));

		mapper = EcoMapperFactory.createSimple();
	}

	@Test
	public void testZfinExample() throws Exception {
		OWLOntology model = loadModel("gomodel-1.owl");
		List<LookupEntry> entities = new ArrayList<>();
		entities.add(new LookupEntry(IRI.create("http://identifiers.org/zfin/ZDB-GENE-991124-7"), "tbx5a", "gene", "NCBITaxon:7955"));
		// is actually a gene, but made a protein for this test case
		entities.add(new LookupEntry(IRI.create("http://identifiers.org/zfin/ZDB-GENE-040426-2843"), "kctd10", "protein", "NCBITaxon:7955"));
		GafDocument gafDocument = translate(model, "gomodel-1", entities);
		List<GeneAnnotation> allAnnotations = gafDocument.getGeneAnnotations();
		assertFalse(allAnnotations.isEmpty());
		
		// check that all annotations have evidence info
		List<GeneAnnotation> noEvidence = new ArrayList<>();
		List<GeneAnnotation> noShortEvidence = new ArrayList<>();
		List<GeneAnnotation> invalidDates = new ArrayList<>();
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
			String date = ann.getLastUpdateDate();
			if (date == null || date.matches("^\\d{8}$") == false) {
				invalidDates.add(ann);
			}
		}
		assertTrue(invalidDates.isEmpty());
		
		// expect two entities
		Collection<Bioentity> bioentities = gafDocument.getBioentities();
		assertEquals(2, bioentities.size());
		
		// check that all entities have a taxon id
		List<Bioentity> noTaxon = new ArrayList<>();
		List<Bioentity> noSymbol = new ArrayList<>();
		
		for (Bioentity entity : bioentities) {
			System.out.println("ENTITY: " + entity);
			if (entity.getNcbiTaxonId() == null) {
				noTaxon.add(entity);
			}
			if (entity.getSymbol() == null) {
				noSymbol.add(entity);
			}
			if ("ZFIN:ZDB-GENE-040426-2843".equals(entity.getId())) {
				assertEquals("protein", entity.getTypeCls());
			}
			else {
				assertEquals("gene", entity.getTypeCls());
			}
		}
		assertTrue(noTaxon.isEmpty());
		
		assertTrue(noEvidence.isEmpty());
		assertEquals(2, unmappedEco.size());
		assertTrue(unmappedEco.contains("ECO:0000302"));
		assertTrue(unmappedEco.contains("ECO:0000011"));
		assertEquals(2, noShortEvidence.size());
		
		assertTrue(noSymbol.isEmpty());
	}
	
	@Test
	public void testWithField() throws Exception {
		OWLOntology model = loadModel("gomodel-2.owl");
		List<LookupEntry> entities = new ArrayList<>();
		entities.add(new LookupEntry(IRI.create("http://flybase.org/reports/FBgn0263395"), "hppy", "gene", "NCBITaxon:7227"));
		entities.add(new LookupEntry(IRI.create("http://v2.pseudomonas.com/getAnnotation.do?locusID=PA1528"), "zipA", "gene", "NCBITaxon:208964"));
		entities.add(new LookupEntry(IRI.create("http://www.aspergillusgenome.org/cgi-bin/locus.pl?dbid=ASPL0000098579"), "zipA", "gene", "NCBITaxon:162425"));
		GafDocument gafdoc = translate(model, "gomodel-2", entities);
		
		// expect two entities
		Collection<Bioentity> bioentities = gafdoc.getBioentities();
		assertEquals(2, bioentities.size());
		
		List<Bioentity> noSymbol = new ArrayList<>();
		
		for (Bioentity entity : bioentities) {
			if (entity.getSymbol() == null) {
				noSymbol.add(entity);
			}
		}
		assertTrue(noSymbol.isEmpty());
		
		// expect three annotations, one providing with infos
		List<GeneAnnotation> allAnnotations = gafdoc.getGeneAnnotations();
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
	
	@Test
	public void testExtendedEvidence1() throws Exception {
		OWLOntology model = loadModel("a1a2a3a401");
		List<LookupEntry> entities = new ArrayList<>();
		GafDocument gafdoc = translate(model, "a1a2a3a401", entities);
		List<GeneAnnotation> allAnnotations = gafdoc.getGeneAnnotations();
		assertEquals(1, allAnnotations.size());
		GeneAnnotation ann = allAnnotations.get(0);
		List<String> referenceIds = ann.getReferenceIds();
		assertTrue(referenceIds.contains("PMID:19797081"));
	}

	@Test
	public void testExtendedEvidence2() throws Exception {
		OWLOntology model = loadModel("a1a2a3a402");
		List<LookupEntry> entities = new ArrayList<>();
		GafDocument gafdoc = translate(model, "a1a2a3a402", entities);
		List<GeneAnnotation> allAnnotations = gafdoc.getGeneAnnotations();
		assertEquals(1, allAnnotations.size());
		GeneAnnotation ann = allAnnotations.get(0);
		List<String> referenceIds = ann.getReferenceIds();
		assertTrue(referenceIds.contains("PMID:19797081"));
	}
	
	private OWLOntology loadModel(String name) throws Exception {
		OWLOntology model = pw.parseOWL(IRI.create(new File("src/test/resources/"+name).getCanonicalFile()));
		return model;
	}
	
	private GafDocument translate(OWLOntology model, String id, List<LookupEntry> entities) throws UnknownIdentifierException {
		LegoToGeneAnnotationTranslator t = new LegoToGeneAnnotationTranslator(model, curieHandler, mapper);
		ExternalLookupService lookup = null;
		if (entities != null) {
			lookup = new TableLookupService(entities);	
		}
		return t.translate(id, model, lookup, null);
	}

}
