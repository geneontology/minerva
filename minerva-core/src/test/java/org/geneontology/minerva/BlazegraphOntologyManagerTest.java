/**
 * 
 */
package org.geneontology.minerva;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author benjamingood
 *
 */
public class BlazegraphOntologyManagerTest {

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	/**
	 * Test method for {@link org.geneontology.minerva.BlazegraphOntologyManager#getSuperClasses(java.lang.String)}.
	 * @throws IOException 
	 */
	@Test	
	public void testGetSuperClasses() throws IOException {
		//need a way to get this that will work in travis environment
		//or option to use a remote service...
		String onto_repo = "/Users/benjamingood/blazegraph/blazegraph-lego.jnl";
		BlazegraphOntologyManager m = new BlazegraphOntologyManager(onto_repo);
		String uri = "http://purl.obolibrary.org/obo/ECO_0000314";
		Set<String> supers = m.getAllSuperClasses(uri);
		System.out.println(supers);
		assertTrue(supers.contains("http://purl.obolibrary.org/obo/ECO_0000002"));
	}

}
