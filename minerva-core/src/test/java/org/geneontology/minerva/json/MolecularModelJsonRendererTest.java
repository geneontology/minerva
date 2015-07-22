package org.geneontology.minerva.json;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.json.JsonOwlIndividual;
import org.geneontology.minerva.json.JsonOwlObject;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.json.JsonOwlObject.JsonOwlObjectType;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.geneontology.minerva.util.IdStringManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import owltools.graph.OWLGraphWrapper;
import owltools.io.ParserWrapper;

public class MolecularModelJsonRendererTest {

	private static OWLGraphWrapper g = null;
	private static OWLOntologyManager m = null;
	private static OWLDataFactory f = null;
	private static OWLObjectProperty partOf = null;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		ParserWrapper pw = new ParserWrapper();
		File file = new File("src/test/resources/mgi-go.obo").getCanonicalFile();
		OWLOntology ont = pw.parseOWL(IRI.create(file));
		g = new OWLGraphWrapper(ont);
		f = g.getDataFactory();
		m = g.getManager();
		partOf = g.getOWLObjectPropertyByIdentifier("BFO:0000050"); 
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		IOUtils.closeQuietly(g);
	}

	@Test
	public void testIRIConversion() throws Exception {
		IRI evidenceIRI = AnnotationShorthand.evidence.getAnnotationProperty();
		OWLAnnotationProperty p = f.getOWLAnnotationProperty(evidenceIRI);
		IRI iriValue = IRI.generateDocumentIRI();
		OWLAnnotation owlAnnotation = f.getOWLAnnotation(p, iriValue);
		JsonAnnotation json = JsonTools.create(p, owlAnnotation.getValue());
		assertEquals(AnnotationShorthand.evidence.name(), json.key);
		assertEquals(iriValue.toString(), json.value);
		assertEquals("IRI", json.valueType);
	}

	@Test
	public void testSimpleClass() throws Exception {
		testSimpleClassExpression(g.getOWLClassByIdentifier("GO:0000003"), "class");
	}
	
	@Test
	public void testSimpleSVF() throws Exception {
		OWLObjectSomeValuesFrom svf = f.getOWLObjectSomeValuesFrom(g.getOWLObjectPropertyByIdentifier("BFO:0000050"), g.getOWLClassByIdentifier("GO:0000003"));
		testSimpleClassExpression(svf, "svf");
	}
	
	@Test
	public void testSimpleUnion() throws Exception {
		OWLObjectSomeValuesFrom svf = f.getOWLObjectSomeValuesFrom(g.getOWLObjectPropertyByIdentifier("BFO:0000050"), g.getOWLClassByIdentifier("GO:0000003"));
		OWLClass cls = g.getOWLClassByIdentifier("GO:0000122");
		testSimpleClassExpression(f.getOWLObjectUnionOf(cls, svf), "union");
	}
	
	@Test
	public void testSimpleIntersection() throws Exception {
		OWLObjectSomeValuesFrom svf = f.getOWLObjectSomeValuesFrom(g.getOWLObjectPropertyByIdentifier("BFO:0000050"), g.getOWLClassByIdentifier("GO:0000003"));
		OWLClass cls = g.getOWLClassByIdentifier("GO:0000122");
		testSimpleClassExpression(f.getOWLObjectIntersectionOf(cls, svf), "intersection");
	}
	
	@Test
	public void testAnnotations() throws Exception {
		// setup test model/ontology
		OWLOntology o = m.createOntology();
		OWLImportsDeclaration importDeclaration = f.getOWLImportsDeclaration(g.getSourceOntology().getOntologyID().getOntologyIRI());
		m.applyChange(new AddImport(o, importDeclaration));
		
		final IRI i1IRI = IRI.generateDocumentIRI();
		final OWLNamedIndividual ni1 = f.getOWLNamedIndividual(i1IRI);
		// declare individual
		m.addAxiom(o, f.getOWLDeclarationAxiom(ni1));
		// add annotations
		m.addAxiom(o, f.getOWLAnnotationAssertionAxiom(i1IRI, 
				f.getOWLAnnotation(f.getOWLAnnotationProperty(
						AnnotationShorthand.comment.getAnnotationProperty()), 
						f.getOWLLiteral("Comment 1"))));
		m.addAxiom(o, f.getOWLAnnotationAssertionAxiom(i1IRI, 
				f.getOWLAnnotation(f.getOWLAnnotationProperty(
						AnnotationShorthand.comment.getAnnotationProperty()), 
						f.getOWLLiteral("Comment 2"))));
		// declare type
		m.addAxiom(o, f.getOWLClassAssertionAxiom(g.getOWLClassByIdentifier("GO:0000003"), ni1));
		
		MolecularModelJsonRenderer r = new MolecularModelJsonRenderer(o, null);
		
		JsonOwlIndividual jsonOwlIndividualOriginal = r.renderObject(ni1);
		assertEquals(2, jsonOwlIndividualOriginal.annotations.length);
		
		String json = MolecularModelJsonRenderer.renderToJson(jsonOwlIndividualOriginal, true);
		
		JsonOwlIndividual jsonOwlIndividualParse = MolecularModelJsonRenderer.parseFromJson(json, JsonOwlIndividual.class);
		
		assertNotNull(jsonOwlIndividualParse);
		assertEquals(jsonOwlIndividualOriginal, jsonOwlIndividualParse);
	}
	
	private void testSimpleClassExpression(OWLClassExpression ce, String expectedJsonType) throws Exception {
		// setup test model/ontology
		OWLOntology o = m.createOntology();
		OWLImportsDeclaration importDeclaration = f.getOWLImportsDeclaration(g.getSourceOntology().getOntologyID().getOntologyIRI());
		m.applyChange(new AddImport(o, importDeclaration));
		
		// create indivdual with a ce type
		final IRI i1IRI = IRI.generateDocumentIRI();
		final OWLNamedIndividual ni1 = f.getOWLNamedIndividual(i1IRI);
		// declare individual
		m.addAxiom(o, f.getOWLDeclarationAxiom(ni1));
		// declare type
		m.addAxiom(o, f.getOWLClassAssertionAxiom(ce, ni1));
		
		
		MolecularModelJsonRenderer r = new MolecularModelJsonRenderer(o, null);
		
		JsonOwlIndividual jsonOwlIndividualOriginal = r.renderObject(ni1);
		
		String json = MolecularModelJsonRenderer.renderToJson(jsonOwlIndividualOriginal, true);
		assertTrue(json, json.contains("\"type\": \""+expectedJsonType+"\""));
		
		JsonOwlIndividual jsonOwlIndividualParse = MolecularModelJsonRenderer.parseFromJson(json, JsonOwlIndividual.class);
		
		assertNotNull(jsonOwlIndividualParse);
		assertEquals(jsonOwlIndividualOriginal, jsonOwlIndividualParse);
		
		Set<OWLClassExpression> ces = TestJsonOwlObjectParser.parse(new OWLGraphWrapper(o), jsonOwlIndividualParse.type);
		assertEquals(1, ces.size());
		assertEquals(ce, ces.iterator().next());
	}
	
	@Test
	public void testPartialRenderer() throws Exception {
		OWLOntology o = m.createOntology();
		OWLImportsDeclaration importDeclaration = f.getOWLImportsDeclaration(g.getSourceOntology().getOntologyID().getOntologyIRI());
		m.applyChange(new AddImport(o, importDeclaration));
		
		// individuals
		final OWLNamedIndividual a = addIndividual(o, "A", null);
		final OWLNamedIndividual b = addIndividual(o, "B", null);
		final OWLNamedIndividual c = addIndividual(o, "C", null);
		final OWLNamedIndividual d = addIndividual(o, "D", null);
		final OWLNamedIndividual e = addIndividual(o, "E", null);
		final OWLNamedIndividual f = addIndividual(o, "F", null);
		
		// links
		addFact(o, a, b, partOf);
		addFact(o, b, a, partOf);
		
		addFact(o, b, c, partOf);
		addFact(o, d, b, partOf);
		addFact(o, e, a, partOf);
		addFact(o, a, f, partOf);
		
		MolecularModelJsonRenderer r = new MolecularModelJsonRenderer(o, null);
		
		final String aId = IdStringManager.getId(a);
		final String bId = IdStringManager.getId(b);
		
		Pair<JsonOwlIndividual[],JsonOwlFact[]> pair = r.renderIndividuals(Arrays.asList(a, b));
		assertEquals(2, pair.getLeft().length);
		assertEquals(2, pair.getRight().length);
		boolean foundAB = false;
		boolean foundBA = false;
		for(JsonOwlFact fact : pair.getRight()) {
			if (aId.equals(fact.subject) && bId.equals(fact.object)) {
				foundAB = true;
			}
			if (bId.equals(fact.subject) && aId.equals(fact.object)) {
				foundBA = true;
			}
		}
		assertTrue(foundAB);
		assertTrue(foundBA);
	}
	

	private static OWLNamedIndividual addIndividual(OWLOntology o, String name, OWLClass typeCls) {
		final IRI iri = IRI.generateDocumentIRI();
		final OWLNamedIndividual ni = f.getOWLNamedIndividual(iri);
		// declare individual
		m.addAxiom(o, f.getOWLDeclarationAxiom(ni));
		if (typeCls != null) {
			m.addAxiom(o, f.getOWLClassAssertionAxiom(typeCls, ni));	
		}
		m.addAxiom(o, f.getOWLAnnotationAssertionAxiom(iri, f.getOWLAnnotation(f.getRDFSLabel(), f.getOWLLiteral(name))));
		
		return ni;
	}
	
	private static void addFact(OWLOntology o, OWLNamedIndividual source, OWLNamedIndividual target, OWLObjectProperty property) {
		m.addAxiom(o, f.getOWLObjectPropertyAssertionAxiom(property, source, target));
	}
	
	static class TestJsonOwlObjectParser {
		static OWLClassExpression parse(OWLGraphWrapper g, JsonOwlObject expression)
				throws Exception {
			if (expression == null) {
				throw new Exception("Missing expression: null is not a valid expression.");
			}
			if (expression.type == null) {
				throw new Exception("An expression type is required.");
			}
			if (JsonOwlObjectType.Class == expression.type) {
				if (expression.id == null) {
					throw new Exception("Missing literal for expression of type 'class'");
				}
				if (StringUtils.containsWhitespace(expression.id)) {
					throw new Exception("Identifiers may not contain whitespaces: '"+expression.id+"'");
				}
				OWLClass cls = g.getOWLClassByIdentifier(expression.id);
				if (cls == null) {
					throw new Exception("Could not retrieve a class for id: "+expression.id);
				}
				return cls;
			}
			else if (JsonOwlObjectType.SomeValueFrom == expression.type) {
				if (expression.property == null) {
					throw new Exception("Missing property for expression of type 'svf'");
				}
				if (expression.property.type != JsonOwlObjectType.ObjectProperty) {
					throw new Exception("Unexpected type for Property in 'svf': "+expression.property.type);
				}
				if (expression.property.id == null) {
					throw new Exception("Missing property id for expression of type 'svf'");
				}
				OWLObjectProperty p = g.getOWLObjectPropertyByIdentifier(expression.property.id);
				if (p == null) {
					throw new UnknownIdentifierException("Could not find a property for: "+expression.property);
				}
				if (expression.filler != null) {
					OWLClassExpression ce = parse(g, expression.filler);
					return g.getDataFactory().getOWLObjectSomeValuesFrom(p, ce);
				}
				else {
					throw new Exception("Missing literal or expression for expression of type 'svf'.");
				}
			}
			else if (JsonOwlObjectType.IntersectionOf == expression.type) {
				return parse(g, expression.expressions, JsonOwlObjectType.IntersectionOf);
			}
			else if (JsonOwlObjectType.UnionOf == expression.type) {
				return parse(g, expression.expressions, JsonOwlObjectType.UnionOf);
			}
			else {
				throw new UnknownIdentifierException("Unknown expression type: "+expression.type);
			}
		}
		
		static OWLClassExpression parse(OWLGraphWrapper g, JsonOwlObject[] expressions, JsonOwlObjectType type)
				throws Exception {
			if (expressions.length == 0) {
				throw new Exception("Missing expressions: empty expression list is not allowed.");
			}
			if (expressions.length == 1) {
				return parse(g, expressions[0]);	
			}
			Set<OWLClassExpression> clsExpressions = new HashSet<OWLClassExpression>();
			for (JsonOwlObject m3Expression : expressions) {
				OWLClassExpression ce = parse(g, m3Expression);
				clsExpressions.add(ce);
			}
			if (type == JsonOwlObjectType.UnionOf) {
				return g.getDataFactory().getOWLObjectUnionOf(clsExpressions);
			}
			else if (type == JsonOwlObjectType.IntersectionOf) {
				return g.getDataFactory().getOWLObjectIntersectionOf(clsExpressions);
			}
			else {
				throw new UnknownIdentifierException("Unsupported expression type: "+type);
			}
		}
		
		static Set<OWLClassExpression> parse(OWLGraphWrapper g, JsonOwlObject[] expressions)
				throws Exception {
			if (expressions.length == 0) {
				throw new Exception("Missing expressions: empty expression list is not allowed.");
			}
			Set<OWLClassExpression> clsExpressions = new HashSet<OWLClassExpression>();
			for (JsonOwlObject m3Expression : expressions) {
				OWLClassExpression ce = parse(g, m3Expression);
				clsExpressions.add(ce);
			}
			return clsExpressions;
		}
	}
}
