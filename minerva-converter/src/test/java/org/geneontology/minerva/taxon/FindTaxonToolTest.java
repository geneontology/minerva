package org.geneontology.minerva.taxon;

import static org.junit.Assert.*;

import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;

import owltools.io.ParserWrapper;
import owltools.io.CatalogXmlIRIMapper;

public class FindTaxonToolTest {
	
	private static OWLOntology NEO = null;
	private static CurieHandler curieHandler = null;
	
	@BeforeClass
	public static void beforeClass() throws Exception {
		ParserWrapper pw = new ParserWrapper();

		// if available, set catalog
        String envCatalog = System.getenv().get("GENEONTOLOGY_CATALOG");
        if (envCatalog != null) {
        	pw.addIRIMapper(new CatalogXmlIRIMapper(envCatalog));
        }

		NEO = pw.parse("http://purl.obolibrary.org/obo/go/noctua/neo.owl");
		curieHandler  = DefaultCurieHandler.getDefaultHandler();
	}

	@Test
	public void test1() throws Exception {
		OWLDataFactory df = NEO.getOWLOntologyManager().getOWLDataFactory();
		FindTaxonTool tool = new FindTaxonTool(curieHandler , df);
		OWLClass zfin1 = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/ZFIN_ZDB-GENE-991124-7"));
		String taxon1 = tool.getEntityTaxon(zfin1 , NEO);
		assertNotNull(taxon1);
	}
	
	@Test
	public void test2() throws Exception {
		OWLDataFactory df = NEO.getOWLOntologyManager().getOWLDataFactory();
		FindTaxonTool tool = new FindTaxonTool(curieHandler, df);
		String taxon1 = tool.getEntityTaxon("ZFIN:ZDB-GENE-991124-7" , NEO);
		assertNotNull(taxon1);
	}

}
