/**
 * 
 */
package org.geneontology.minerva;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author benjamingood
 *
 */
public class BlazegraphOntologyManagerTest {
	//if the file isn't there, it will try to download it from 
	//BlazegraphOntologyManager.http://skyhook.berkeleybop.org/issue-35-neo-test/products/blazegraph/blazegraph-go-lego.jnl.gz
	//can override the download by providing the file at the specified location
	static final String ontology_journal_file = "/tmp/test-go-lego-blazegraph.jnl";
	static BlazegraphOntologyManager onto_repo;
	
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		onto_repo = new BlazegraphOntologyManager(ontology_journal_file);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		onto_repo.dispose();
	}

	
	/**
	 * Test method for {@link org.geneontology.minerva.BlazegraphOntologyManager#getAllTaxaWithGenes()}.
	 * @throws IOException 
	 */
	@Test	
	public void testGetLabels() throws IOException {
		String thing = "http://purl.obolibrary.org/obo/NCBITaxon_44689";
		String label = onto_repo.getLabel(thing);
		assertTrue(label!=null);
		System.out.println(thing+"  "+label);
		thing = "http://identifiers.org/zfin/ZDB-GENE-010410-3";
		label = onto_repo.getLabel(thing);
		assertTrue(label!=null);
		System.out.println(thing+"  "+label);		
		thing = "http://purl.obolibrary.org/obo/ECO_0000314";
		label = onto_repo.getLabel(thing);
		assertTrue(label!=null);
		System.out.println(thing+"  "+label);
		thing = "http://purl.obolibrary.org/obo/GO_0110165";
		label = onto_repo.getLabel(thing);
		assertTrue(label!=null);
		System.out.println(thing+"  "+label);
		thing = "http://purl.obolibrary.org/obo/GO_0060090";
		label = onto_repo.getLabel(thing);
		assertTrue(label!=null);
		System.out.println(thing+"  "+label);
	}
	
	/**
	 * Test method for {@link org.geneontology.minerva.BlazegraphOntologyManager#getAllTaxaWithGenes()}.
	 * @throws IOException 
	 */
	@Test	
	public void testGetAllTaxaWithGenes() throws IOException {
		Set<String> taxa = onto_repo.getAllTaxaWithGenes();
		assertTrue("taxa has more than a hundred entries", taxa.size()>100);
		assertTrue("taxa contains NCBITaxon_44689", taxa.contains("http://purl.obolibrary.org/obo/NCBITaxon_44689"));
		assertTrue("taxa contains NCBITaxon_9606", taxa.contains("http://purl.obolibrary.org/obo/NCBITaxon_9606"));
	}
	
	/**
	 * Test method for {@link org.geneontology.minerva.BlazegraphOntologyManager#getSubClasses(java.lang.String)}.
	 * @throws IOException 
	 */
	@Test	
	public void testGetGenesByTaxonId() throws IOException {
	//zfin
		String ncbi_tax_id = "7955"; //zfin
		Set<String> genes = onto_repo.getGenesByTaxid(ncbi_tax_id);
		assertTrue("http://identifiers.org/zfin/ZDB-GENE-010410-3 not returned for taxon 7955 zfin", genes.contains("http://identifiers.org/zfin/ZDB-GENE-010410-3"));
	}
	
	
	/**
	 * Test method for {@link org.geneontology.minerva.BlazegraphOntologyManager#getSubClasses(java.lang.String)}.
	 * @throws IOException 
	 */
	@Test	
	public void testGetSubClasses() throws IOException {
		//make sure its possible to get from leaf to root for the key classes
	//Evidence
		String uri = "http://purl.obolibrary.org/obo/ECO_0000000";
		Set<String> subs = onto_repo.getAllSubClasses(uri);
		assertTrue("ECO_0000314 not subclass of ECO_0000000", subs.contains("http://purl.obolibrary.org/obo/ECO_0000314"));
	//Anatomy
		//worm anatomy - note that it needs parts of the cl ontology in there
		uri = "http://purl.obolibrary.org/obo/CL_0000003";
		subs = onto_repo.getAllSubClasses(uri);
		//GO native cell - used a lot in shex
		assertTrue("WBbt_0005753 not subclass of CL_0000003", subs.contains("http://purl.obolibrary.org/obo/WBbt_0005753")); 
	//Cell component
		uri = "http://purl.obolibrary.org/obo/GO_0110165";
		subs = onto_repo.getAllSubClasses(uri);
		assertTrue("GO_0000776 not subclass of GO_0110165 'cellular anatomical entity'", subs.contains("http://purl.obolibrary.org/obo/GO_0000776"));
	//biological process
		uri = "http://purl.obolibrary.org/obo/GO_0008150";
		subs = onto_repo.getAllSubClasses(uri);
		assertTrue("GO_0022607 not subclass of GO_000815 biological process", subs.contains("http://purl.obolibrary.org/obo/GO_0022607"));
	//molecular function
		uri = "http://purl.obolibrary.org/obo/GO_0003674";
		subs = onto_repo.getAllSubClasses(uri);
		assertTrue("GO_0060090 not subclass of molecular function GO_0003674", subs.contains("http://purl.obolibrary.org/obo/GO_0060090"));	
	//Gene products
//this is a little extreme.. it works but takes a minute.  Should never have to do this in a live search system
//		//uniprot
//		uri = "http://purl.obolibrary.org/obo/CHEBI_36080";
//		subs = onto_repo.getAllSubClasses(uri);
//		//protein
//		assertTrue("uniprot/Q13253 not subclass of http://purl.obolibrary.org/obo/CHEBI_36080 protein", subs.contains("http://identifiers.org/uniprot/Q13253")); 
//		//"gene"..
//		//zfin
//		uri = "http://purl.obolibrary.org/obo/CHEBI_33695";
//		subs = onto_repo.getAllSubClasses(uri);
//		assertTrue("ZDB-GENE-010410-3 not subclass of CHEBI_36695 information biomacromolecule", subs.contains("http://identifiers.org/zfin/ZDB-GENE-010410-3"));
//		//wormbase
//		uri = "http://purl.obolibrary.org/obo/CHEBI_24431";
//		subs = onto_repo.getAllSubClasses(uri);
//		assertTrue("WBGene00000275 not subclass of CHEBI_24431 chemical entity", subs.contains("http://identifiers.org/wormbase/WBGene00000275"));

	}
	
	/**
	 * Test method for {@link org.geneontology.minerva.BlazegraphOntologyManager#getSuperClasses(java.lang.String)}.
	 * @throws IOException 
	 */
	@Test	
	public void testGetSuperClasses() throws IOException {
		//make sure its possible to get from leaf to root for the key classes
	//Evidence
		String uri = "http://purl.obolibrary.org/obo/ECO_0000314";
		Set<String> supers = onto_repo.getAllSuperClasses(uri);
		assertTrue("ECO_0000314 not subclass of ECO_0000000", supers.contains("http://purl.obolibrary.org/obo/ECO_0000000"));
	//Anatomy
		//worm anatomy - note that it needs parts of the cl ontology in there
		uri = "http://purl.obolibrary.org/obo/WBbt_0005753";
		supers = onto_repo.getAllSuperClasses(uri);
		//GO native cell - used a lot in shex
		assertTrue("WBbt_0005753 not subclass of CL_0000003", supers.contains("http://purl.obolibrary.org/obo/CL_0000003")); 
		//anatomy - also used a lot in shex
		assertTrue("WBbt_0005753 not subclass of CARO_0000000", supers.contains("http://purl.obolibrary.org/obo/CARO_0000000"));
	//Cell component
		uri = "http://purl.obolibrary.org/obo/GO_0000776";
		supers = onto_repo.getAllSuperClasses(uri);
		assertTrue("GO_0000776 not subclass of GO_0110165 'cellular anatomical entity'", supers.contains("http://purl.obolibrary.org/obo/GO_0110165"));
		assertTrue("GO_0000776 not subclass of GO_0005575 'cellular component'", supers.contains("http://purl.obolibrary.org/obo/GO_0005575"));
	//biological process
		uri = "http://purl.obolibrary.org/obo/GO_0022607";
		supers = onto_repo.getAllSuperClasses(uri);
		assertTrue("GO_0022607 not subclass of GO_000815 biological process", supers.contains("http://purl.obolibrary.org/obo/GO_0008150"));
	//molecular function
		uri = "http://purl.obolibrary.org/obo/GO_0060090";
		supers = onto_repo.getAllSuperClasses(uri);
		assertTrue("GO_0060090 not subclass of molecular function GO_0003674", supers.contains("http://purl.obolibrary.org/obo/GO_0003674"));	
	//Gene products
		//uniprot
		uri = "http://identifiers.org/uniprot/Q13253";
		supers = onto_repo.getAllSuperClasses(uri);
		//protein
		assertTrue("uniprot/Q13253 not subclass of CHEBI_36080 protein", supers.contains("http://purl.obolibrary.org/obo/CHEBI_36080")); 
		assertTrue("uniprot/Q13253 not subclass of CHEBI_36695 information biomacromolecule", supers.contains("http://purl.obolibrary.org/obo/CHEBI_33695"));
		assertTrue("uniprot/Q13253 not subclass of CHEBI_24431 chemical entity", supers.contains("http://purl.obolibrary.org/obo/CHEBI_24431"));
		//"gene"..
		//zfin
		uri = "http://identifiers.org/zfin/ZDB-GENE-010410-3";
		supers = onto_repo.getAllSuperClasses(uri);
		assertTrue("ZDB-GENE-010410-3 not subclass of CHEBI_36695 information biomacromolecule", supers.contains("http://purl.obolibrary.org/obo/CHEBI_33695"));
		assertTrue("ZDB-GENE-010410-3 not subclass of CHEBI_24431 chemical entity", supers.contains("http://purl.obolibrary.org/obo/CHEBI_24431"));
		//wormbase
		uri = "http://identifiers.org/wormbase/WBGene00000275";
		supers = onto_repo.getAllSuperClasses(uri);
		assertTrue("WBGene00000275 not subclass of CHEBI_36695 information biomacromolecule", supers.contains("http://purl.obolibrary.org/obo/CHEBI_33695"));
		assertTrue("WBGene00000275 not subclass of CHEBI_24431 chemical entity", supers.contains("http://purl.obolibrary.org/obo/CHEBI_24431"));
	}
	
	@Test	
	public void testGetUpperTypes() throws IOException {
		//make sure its possible to get from leaf to root for the key classes
		Set<String> uris = new HashSet<String>();
		String eco = "http://purl.obolibrary.org/obo/ECO_0000314";
		String wbbt = "http://purl.obolibrary.org/obo/WBbt_0005753";
		String cc = "http://purl.obolibrary.org/obo/GO_0000776";
		String bp = "http://purl.obolibrary.org/obo/GO_0022607";
		String mf = "http://purl.obolibrary.org/obo/GO_0060090";
		String human_protein = "http://identifiers.org/uniprot/Q13253";
		String zfin_protein = "http://identifiers.org/zfin/ZDB-GENE-010410-3";
		String worm_gene = "http://identifiers.org/wormbase/WBGene00000275";
		uris.add(eco);
		uris.add(wbbt);
		uris.add(cc);
		uris.add(bp);
		uris.add(mf);
		uris.add(human_protein);
		uris.add(zfin_protein);
		uris.add(worm_gene);
		
		Map<String, Set<String>> uri_roots = onto_repo.getSuperCategoryMap(uris);
	//Evidence
		Set<String> supers = uri_roots.get(eco);
		assertTrue("ECO_0000314 not subclass of ECO_0000000", supers.contains("http://purl.obolibrary.org/obo/ECO_0000000"));
	//Anatomy
		//worm anatomy - note that it needs parts of the cl ontology in there
		supers = uri_roots.get(wbbt);
		//GO native cell - used a lot in shex
		//assertTrue("WBbt_0005753 not subclass of CL_0000003", supers.contains("http://purl.obolibrary.org/obo/CL_0000003")); 
		//anatomy - also used a lot in shex
		assertTrue("WBbt_0005753 not subclass of CARO_0000000", supers.contains("http://purl.obolibrary.org/obo/CARO_0000000"));
	//Cell component
		supers = uri_roots.get(cc);
		assertTrue("GO_0000776 not subclass of GO_0110165 'cellular anatomical entity'", supers.contains("http://purl.obolibrary.org/obo/GO_0110165"));
		assertTrue("GO_0000776 not subclass of GO_0005575 'cellular component'", supers.contains("http://purl.obolibrary.org/obo/GO_0005575"));
	//biological process
		supers = uri_roots.get(bp);
		assertTrue("GO_0022607 not subclass of GO_000815 biological process", supers.contains("http://purl.obolibrary.org/obo/GO_0008150"));
	//molecular function
		supers = uri_roots.get(mf);
		assertTrue("GO_0060090 not subclass of molecular function GO_0003674", supers.contains("http://purl.obolibrary.org/obo/GO_0003674"));	
	//Gene products
		//uniprot
		supers = uri_roots.get(human_protein);
		//protein
		assertTrue("uniprot/Q13253 not subclass of CHEBI_36080 protein", supers.contains("http://purl.obolibrary.org/obo/CHEBI_36080")); 
		assertTrue("uniprot/Q13253 not subclass of CHEBI_36695 information biomacromolecule", supers.contains("http://purl.obolibrary.org/obo/CHEBI_33695"));
		assertTrue("uniprot/Q13253 not subclass of CHEBI_24431 chemical entity", supers.contains("http://purl.obolibrary.org/obo/CHEBI_24431"));
		//"gene"..
		//zfin
		supers = uri_roots.get(zfin_protein);
		assertTrue("ZDB-GENE-010410-3 not subclass of CHEBI_36695 information biomacromolecule", supers.contains("http://purl.obolibrary.org/obo/CHEBI_33695"));
		assertTrue("ZDB-GENE-010410-3 not subclass of CHEBI_24431 chemical entity", supers.contains("http://purl.obolibrary.org/obo/CHEBI_24431"));
		//wormbase
		supers = uri_roots.get(worm_gene);
		assertTrue("WBGene00000275 not subclass of CHEBI_36695 information biomacromolecule", supers.contains("http://purl.obolibrary.org/obo/CHEBI_33695"));
		assertTrue("WBGene00000275 not subclass of CHEBI_24431 chemical entity", supers.contains("http://purl.obolibrary.org/obo/CHEBI_24431"));
	}
	
	@Test	
	public void testGetComplexPortalTypes() throws IOException {
		//make sure its possible to get from leaf to root for the key classes
		Set<String> uris = new HashSet<String>();
		String cp1 = "http://purl.obolibrary.org/obo/ComplexPortal_CPX-9";
		String cp2 = "http://purl.obolibrary.org/obo/ComplexPortal_CPX-4082";
//this doesn't work now.  It should work while the ones above do not.  this is problem with the neo ontology.  
//		String cp3 = "https://www.ebi.ac.uk/complexportal/complex/CPX-9";
		uris.add(cp1);
		uris.add(cp2);
//		uris.add(cp3);
		
		Map<String, Set<String>> uri_roots = onto_repo.getSuperCategoryMap(uris);
		Set<String> supers = uri_roots.get(cp1);
		assertTrue("ComplexPortal_CPX-9 not an information biomacromolecule", supers.contains("http://purl.obolibrary.org/obo/CHEBI_33695"));
		supers = uri_roots.get(cp2);
		assertTrue("ComplexPortal_CPX-4082 not an information biomacromolecule", supers.contains("http://purl.obolibrary.org/obo/CHEBI_33695"));
//		supers = uri_roots.get(cp3);
//		assertTrue("ComplexPortal_CPX-9 as https://www.ebi.ac.uk/complexportal/complex/CPX-9 is not an information biomacromolecule", supers.contains("http://purl.obolibrary.org/obo/CHEBI_33695"));
		

	}
}
