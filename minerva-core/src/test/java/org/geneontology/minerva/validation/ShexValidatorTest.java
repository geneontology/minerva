package org.geneontology.minerva.validation;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.geneontology.minerva.BlazegraphOntologyManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLOntology;

import fr.inria.lille.shexjava.schema.ShexSchema;
import fr.inria.lille.shexjava.schema.parsing.GenParser;

public class ShexValidatorTest {
/**
 * -c /Users/benjamingood/gocam_ontology/catalog-v001-for-noctua.xml
-s /Users/benjamingood/GitHub/GO_Shapes/shapes/go-cam-shapes.shex 
-m /Users/benjamingood/GitHub/GO_Shapes/shapes/metadata.shapemap 
-i /Users/benjamingood/GitHub/GO_Shapes/test_ttl/go_cams/should_pass/ 
-r ./shape_report_shouldpass.txt 
-e ./shape_explanation_shouldpass.txt
-ontojournal /Users/benjamingood/blazegraph/blazegraph-go-lego-with-reacto.jnl
 * @throws Exception
 */
	//TODO set up some kind of a configuration file that encapsulates these files
	static final String go_lego_journal_file = "/tmp/test-go-lego-blazegraph.jnl";
	static final String schemaFile = "src/test/resources/validation/go-cam-shapes.shex";
	static final String metadataSchemaFile = "src/test/resources/validation/metadata-shapes.shex";
	static final String metadataShapemapFile = "src/test/resources/validation/metadata.shapemap";
	static final String mainShapemapFile = "src/test/resources/validation/go-cam-shapes.shapemap";
	static ShexValidator shex;
	static ShexValidator shexMeta;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		BlazegraphOntologyManager go_lego = new BlazegraphOntologyManager(go_lego_journal_file);
		CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
		shex = new ShexValidator(schemaFile, mainShapemapFile, go_lego, curieHandler);
		shexMeta = new ShexValidator(metadataSchemaFile, metadataShapemapFile, go_lego, curieHandler);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}
	
	@Test
	public void testSchemaParse() throws Exception {
		ShexSchema schema = GenParser.parseSchema(new File(schemaFile).toPath());
	}
	
	@Test
	public void testMainMapParse() throws IOException {
		Map<String, String> query_map = ShexValidator.makeGoQueryMap(mainShapemapFile);
	}
	
	@Test
	public void testMetaMapParse() throws IOException {
		Map<String, String> query_map = ShexValidator.makeGoQueryMap(metadataShapemapFile);
		System.out.println(query_map);
	}
	
//	@Test
	public void testShexShouldPass() throws Exception {		
		boolean should_be_valid = true;
		validate("src/test/resources/validation/should_pass/", shex, should_be_valid);		
	}

//	@Test
	public void testShexShouldFail() throws Exception {		
		boolean should_be_valid = false;
		validate("src/test/resources/validation/should_fail/", shex, should_be_valid);	
	}	
	
	@Test 
	public void testShexMetadata() throws IOException {
		boolean should_be_valid = true;
		validate("src/test/resources/validation/should_pass/", shexMeta, should_be_valid);	
	}
	
	public void validate(String dir, ShexValidator shex, boolean should_be_valid) throws IOException {
		File directory = new File(dir);
		if(directory.isDirectory()) {
			for(File file : directory.listFiles()) {
				if(file.getName().endsWith("ttl")) {
					Model test_model = ModelFactory.createDefaultModel();
					System.out.println("validating "+file.getAbsolutePath());
					test_model.read(file.getAbsolutePath());
					//Note that in the live system, Arachne is executed on the model prior to this step, potentially adding inferred classes that are missed with this.
					//this is faster and useful for debugging the shex though.  See org.geneontology.minerva.server.validation.ValidationTest in the Test branch of Minerva server for a more complete test
					test_model = shex.enrichSuperClasses(test_model);
					ShexValidationReport report = shex.runShapeMapValidation(test_model);
					if(should_be_valid) {
						assertTrue(file+" not conformant", report.isConformant());
					}else {
						assertFalse(file+" is conformant (should not be)", report.isConformant());
					}
				}
			}
		}
	}
}
