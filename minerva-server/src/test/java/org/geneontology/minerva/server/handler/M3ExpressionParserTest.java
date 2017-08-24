package org.geneontology.minerva.server.handler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.json.JsonOwlObject;
import org.geneontology.minerva.json.JsonOwlObject.JsonOwlObjectType;
import org.geneontology.minerva.server.handler.M3ExpressionParser;
import org.geneontology.minerva.server.handler.OperationsTools.MissingParameterException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class M3ExpressionParserTest {

	private static final CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
	private static OWLGraphWrapper graph;
	
	// these are present in the test module
    private static final String CELL_MORPHOGENESIS = "GO:0000902";
    private static final String NUCLEUS = "GO:0005623";
    private static final String OCCURS_IN = "BFO:0000066";
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		init(new ParserWrapper());
	}

	static void init(ParserWrapper pw) throws OWLOntologyCreationException, IOException {
	    File file = new File("src/test/resources/go-lego-module.omn.gz").getCanonicalFile();
		graph = new OWLGraphWrapper(pw.parseOWL(IRI.create(file)));
	}

	@Test(expected=MissingParameterException.class)
	public void testMissing0() throws Exception {
		JsonOwlObject expression = null;
		new M3ExpressionParser(curieHandler).parse(graph, expression, null);
	}
	
	@Test(expected=MissingParameterException.class)
	public void testMissing1() throws Exception {
		JsonOwlObject expression = new JsonOwlObject();
		new M3ExpressionParser(curieHandler).parse(graph, expression, null);
	}
	
	@Test(expected=MissingParameterException.class)
	public void testMissing2() throws Exception {
		JsonOwlObject expression = new JsonOwlObject();
		expression.type = JsonOwlObjectType.Class;
		new M3ExpressionParser(curieHandler).parse(graph, expression, null);
	}
	
	@Test(expected=MissingParameterException.class)
	public void testMissing3() throws Exception {
		JsonOwlObject expression = new JsonOwlObject();
		expression.type = JsonOwlObjectType.SomeValueFrom;
		expression.property = new JsonOwlObject();
		expression.property.type = JsonOwlObjectType.ObjectProperty;
		expression.property.id = OCCURS_IN; // occurs_in
		new M3ExpressionParser(curieHandler).parse(graph, expression, null);
	}
	
	@Test(expected=MissingParameterException.class)
	public void testMissing4() throws Exception {
		JsonOwlObject expression = new JsonOwlObject();
		expression.type = JsonOwlObjectType.SomeValueFrom;
		expression.property = new JsonOwlObject();
		expression.property.type = JsonOwlObjectType.ObjectProperty;
		expression.property.id = OCCURS_IN; // occurs_in
		expression.filler = new JsonOwlObject();
		new M3ExpressionParser(curieHandler).parse(graph, expression, null);
	}
	
	@Test(expected=MissingParameterException.class)
	public void testMissing5() throws Exception {
		JsonOwlObject expression = new JsonOwlObject();
		expression.type = JsonOwlObjectType.SomeValueFrom;
		expression.property = new JsonOwlObject();
		expression.property.type = JsonOwlObjectType.ObjectProperty;
		expression.property.id = OCCURS_IN; // occurs_in
		expression.filler = new JsonOwlObject();
		expression.filler.type = JsonOwlObjectType.Class;
		new M3ExpressionParser(curieHandler).parse(graph, expression, null);
	}
	
	@Test(expected=MissingParameterException.class)
	public void testMissing6() throws Exception {
		JsonOwlObject expression = new JsonOwlObject();
		expression.type = JsonOwlObjectType.SomeValueFrom;
		expression.property = new JsonOwlObject();
		expression.property.type = JsonOwlObjectType.ObjectProperty;
		expression.property.id = OCCURS_IN; // occurs_in
		expression.filler = new JsonOwlObject();
		expression.filler.id = NUCLEUS;
		new M3ExpressionParser(curieHandler).parse(graph, expression, null);
	}
	
	@Test
	public void testParseClazz() throws Exception {
		
		JsonOwlObject expression = new JsonOwlObject();
		expression.type = JsonOwlObjectType.Class;
		expression.id = CELL_MORPHOGENESIS;
		
		OWLClassExpression ce = new M3ExpressionParser(curieHandler).parse(graph, expression, null);
		assertEquals(graph.getOWLClassByIdentifier(CELL_MORPHOGENESIS), ce);
	}
	
	/**
     * test that Default expression parser will throw UnknownIdentifierException
     * when confronted with a non-CURIE
     * 
     * @throws Exception
     */
    @Test(expected=UnknownIdentifierException.class)
    public void testBadCurieFail() throws Exception {
        
        JsonOwlObject expression = new JsonOwlObject();
        expression.type = JsonOwlObjectType.Class;
        expression.id = "ABC"; // not a CURIE
        
        OWLClassExpression ce = new M3ExpressionParser(curieHandler).parse(graph, expression, null);
    }

    /**
     * test that Default expression parser will throw UnknownIdentifierException
     * when confronted with an ID with an unknown prefix
     * 
     * @throws Exception
     */
    @Test(expected=UnknownIdentifierException.class)
    public void testParseClazzFail() throws Exception {
        
        JsonOwlObject expression = new JsonOwlObject();
        expression.type = JsonOwlObjectType.Class;
        expression.id = "FO:0006915";
        
        OWLClassExpression ce = new M3ExpressionParser(curieHandler).parse(graph, expression, null);
    }

    /**
     * test that unknown prefixes cannot be entered even with id-literal checking off
     * 
     * @throws Exception
     */
    @Test(expected=UnknownIdentifierException.class)
    public void testParseClazzFailNoCheckLiteralIds() throws Exception {
        
        JsonOwlObject expression = new JsonOwlObject();
        expression.type = JsonOwlObjectType.Class;
        expression.id = "THISISNOTAPREFIX:0006915";
        
        OWLClassExpression ce = new M3ExpressionParser(false, curieHandler).parse(graph, expression, null);
    }
    
    @Test
    public void testParseClazzNoCheckLiteralIds() throws Exception {
        
        JsonOwlObject expression = new JsonOwlObject();
        expression.type = JsonOwlObjectType.Class;
        expression.id = "GO:23"; // valid prefix, not a known class
        
        // create a parser that explicitly disables checking so-called literal ids 
        OWLClassExpression ce = new M3ExpressionParser(false, curieHandler).parse(graph, expression, null);
        
        // check the retrieved class is the same as the input
        // note: we don't use the owltools getClass method directly, as that depends on the class
        // being known
        IRI iri = graph.getIRIByIdentifier("GO:23");
        assertEquals(iri, ce.asOWLClass().getIRI());
    }


	@Test
	public void testParseSvf() throws Exception {
		
		JsonOwlObject expression = new JsonOwlObject();
		expression.type = JsonOwlObjectType.SomeValueFrom;
		expression.property = new JsonOwlObject();
		expression.property.type = JsonOwlObjectType.ObjectProperty;
		expression.property.id = OCCURS_IN; // occurs_in
		expression.filler = new JsonOwlObject();
		expression.filler.type = JsonOwlObjectType.Class;
		expression.filler.id = NUCLEUS;
		
		OWLClassExpression ce = new M3ExpressionParser(curieHandler).parse(graph, expression, null);
		assertNotNull(ce);
	}
	
	@Test(expected=UnknownIdentifierException.class)
	public void testParseSvfFail1() throws Exception {
		
		JsonOwlObject expression = new JsonOwlObject();
		expression.type = JsonOwlObjectType.SomeValueFrom;
		expression.property = new JsonOwlObject();
		expression.property.type = JsonOwlObjectType.ObjectProperty;
		expression.property.id = OCCURS_IN; // occurs_in
		expression.filler = new JsonOwlObject();
		expression.filler.type = JsonOwlObjectType.Class;
		expression.filler.id = "FO:0005623"; // error
		
		new M3ExpressionParser(curieHandler).parse(graph, expression, null);
	}
	
	@Test(expected=UnknownIdentifierException.class)
	public void testParseSvfFailNoCheckLiteralIds1() throws Exception {

	    JsonOwlObject expression = new JsonOwlObject();
	    expression.type = JsonOwlObjectType.SomeValueFrom;
	    expression.property = new JsonOwlObject();
	    expression.property.type = JsonOwlObjectType.ObjectProperty;
	    expression.property.id = OCCURS_IN; // occurs_in
	    expression.filler = new JsonOwlObject();
	    expression.filler.type = JsonOwlObjectType.Class;
	    expression.filler.id = "DEFINITELYNOTAPREFIX:0005623"; // error

	    new M3ExpressionParser(false, curieHandler).parse(graph, expression, null);
	}

	@Test(expected=UnknownIdentifierException.class)
	public void testParseSvfFailNoCheckLiteralIds2() throws Exception {

	    JsonOwlObject expression = new JsonOwlObject();
	    expression.type = JsonOwlObjectType.SomeValueFrom;
	    expression.property = new JsonOwlObject();
	    expression.property.type = JsonOwlObjectType.ObjectProperty;
	    expression.property.id = "NOTARELATIONPREFIX:123"; 
	    expression.filler = new JsonOwlObject();
	    expression.filler.type = JsonOwlObjectType.Class;
	    expression.filler.id = NUCLEUS; // error

	    new M3ExpressionParser(false, curieHandler).parse(graph, expression, null);
	}

	@Test(expected=UnknownIdentifierException.class)
	public void testParseSvfFail2() throws Exception {
		
		JsonOwlObject expression = new JsonOwlObject();
		expression.type = JsonOwlObjectType.SomeValueFrom;
		expression.property = new JsonOwlObject();
		expression.property.type = JsonOwlObjectType.ObjectProperty;
		expression.property.id = "FFO:0000066"; // error
		expression.filler = new JsonOwlObject();
		expression.filler.type = JsonOwlObjectType.Class;
		expression.filler.id = NUCLEUS; 
		
		new M3ExpressionParser(curieHandler).parse(graph, expression, null);
	}
	
}
