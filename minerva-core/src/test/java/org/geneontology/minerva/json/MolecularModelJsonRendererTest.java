package org.geneontology.minerva.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.json.JsonOwlObject.JsonOwlObjectType;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.geneontology.minerva.util.OntUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
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

public class MolecularModelJsonRendererTest {

	private static OWLOntology ont = null;
	private static CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
	private static OWLOntologyManager m = null;
	private static OWLDataFactory f = null;
	private static OWLObjectProperty partOf = null;
	private static OWLClass reproduction = null;
	private static OWLClass negRegTrans = null;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		m = OWLManager.createOWLOntologyManager();
		ont = m.loadOntologyFromOntologyDocument(MolecularModelJsonRendererTest.class.getResourceAsStream("/mgi-go.obo"));
		f = m.getOWLDataFactory();
		partOf = f.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/BFO_0000050"));
		reproduction = f.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0000003"));
		negRegTrans = f.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0000122"));
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Test
	public void testIRIConversion() throws Exception {
		IRI evidenceIRI = AnnotationShorthand.evidence.getAnnotationProperty();
		OWLAnnotationProperty p = f.getOWLAnnotationProperty(evidenceIRI);
		IRI iriValue = IRI.generateDocumentIRI();
		OWLAnnotation owlAnnotation = f.getOWLAnnotation(p, iriValue);
		JsonAnnotation json = JsonTools.create(p, owlAnnotation.getValue(), curieHandler);
		assertEquals(AnnotationShorthand.evidence.name(), json.key);
		assertEquals(curieHandler.getCuri(iriValue), json.value);
		assertEquals("IRI", json.valueType);
	}

	@Test
	public void testSimpleClass() throws Exception {
		testSimpleClassExpression(reproduction, "class");
	}
	
	@Test
	public void testSimpleSVF() throws Exception {
		OWLObjectSomeValuesFrom svf = f.getOWLObjectSomeValuesFrom(partOf, reproduction);
		testSimpleClassExpression(svf, "svf");
	}
	
	@Test
	public void testSimpleUnion() throws Exception {
		OWLObjectSomeValuesFrom svf = f.getOWLObjectSomeValuesFrom(partOf, reproduction);
		testSimpleClassExpression(f.getOWLObjectUnionOf(negRegTrans, svf), "union");
	}
	
	@Test
	public void testSimpleIntersection() throws Exception {
		OWLObjectSomeValuesFrom svf = f.getOWLObjectSomeValuesFrom(partOf, reproduction);
		testSimpleClassExpression(f.getOWLObjectIntersectionOf(negRegTrans, svf), "intersection");
	}
	
	@Test
	public void testAnnotations() throws Exception {
		// setup test model/ontology
		OWLOntology o = m.createOntology();
		OWLImportsDeclaration importDeclaration = f.getOWLImportsDeclaration(ont.getOntologyID().getOntologyIRI().get());
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
		m.addAxiom(o, f.getOWLClassAssertionAxiom(reproduction, ni1));
		
		MolecularModelJsonRenderer r = new MolecularModelJsonRenderer(null, o, null, curieHandler);
		
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
		OWLImportsDeclaration importDeclaration = f.getOWLImportsDeclaration(ont.getOntologyID().getOntologyIRI().get());
		m.applyChange(new AddImport(o, importDeclaration));
		
		// create indivdual with a ce type
		final IRI i1IRI = IRI.generateDocumentIRI();
		final OWLNamedIndividual ni1 = f.getOWLNamedIndividual(i1IRI);
		// declare individual
		m.addAxiom(o, f.getOWLDeclarationAxiom(ni1));
		// declare type
		m.addAxiom(o, f.getOWLClassAssertionAxiom(ce, ni1));
		
		
		MolecularModelJsonRenderer r = new MolecularModelJsonRenderer(null, o, null, curieHandler);
		
		JsonOwlIndividual jsonOwlIndividualOriginal = r.renderObject(ni1);
		
		String json = MolecularModelJsonRenderer.renderToJson(jsonOwlIndividualOriginal, true);
		assertTrue(json, json.contains("\"type\": \""+expectedJsonType+"\""));
		
		JsonOwlIndividual jsonOwlIndividualParse = MolecularModelJsonRenderer.parseFromJson(json, JsonOwlIndividual.class);
		
		assertNotNull(jsonOwlIndividualParse);
		assertEquals(jsonOwlIndividualOriginal, jsonOwlIndividualParse);
		
		Set<OWLClassExpression> ces = TestJsonOwlObjectParser.parse(o, jsonOwlIndividualParse.type);
		assertEquals(1, ces.size());
		assertEquals(ce, ces.iterator().next());
	}
	
	@Test
	public void testPartialRenderer() throws Exception {
		OWLOntology o = m.createOntology();
		OWLImportsDeclaration importDeclaration = f.getOWLImportsDeclaration(ont.getOntologyID().getOntologyIRI().get());
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
		
		MolecularModelJsonRenderer r = new MolecularModelJsonRenderer(null, o, null, curieHandler);
		
		final String aId = curieHandler.getCuri(a);
		final String bId = curieHandler.getCuri(b);
		
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
		static OWLClassExpression parse(OWLOntology ont, JsonOwlObject expression)
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
				OWLClass cls = f.getOWLClass(OntUtil.getIRIByIdentifier(expression.id, ont));
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
				OWLObjectProperty p = f.getOWLObjectProperty(OntUtil.getIRIByIdentifier(expression.property.id, ont));
				if (p == null) {
					throw new UnknownIdentifierException("Could not find a property for: "+expression.property);
				}
				if (expression.filler != null) {
					OWLClassExpression ce = parse(ont, expression.filler);
					return f.getOWLObjectSomeValuesFrom(p, ce);
				}
				else {
					throw new Exception("Missing literal or expression for expression of type 'svf'.");
				}
			}
			else if (JsonOwlObjectType.IntersectionOf == expression.type) {
				return parse(ont, expression.expressions, JsonOwlObjectType.IntersectionOf);
			}
			else if (JsonOwlObjectType.UnionOf == expression.type) {
				return parse(ont, expression.expressions, JsonOwlObjectType.UnionOf);
			}
			else {
				throw new UnknownIdentifierException("Unknown expression type: "+expression.type);
			}
		}
		
		static OWLClassExpression parse(OWLOntology o, JsonOwlObject[] expressions, JsonOwlObjectType type)
				throws Exception {
			if (expressions.length == 0) {
				throw new Exception("Missing expressions: empty expression list is not allowed.");
			}
			if (expressions.length == 1) {
				return parse(o, expressions[0]);	
			}
			Set<OWLClassExpression> clsExpressions = new HashSet<OWLClassExpression>();
			for (JsonOwlObject m3Expression : expressions) {
				OWLClassExpression ce = parse(o, m3Expression);
				clsExpressions.add(ce);
			}
			if (type == JsonOwlObjectType.UnionOf) {
				return f.getOWLObjectUnionOf(clsExpressions);
			}
			else if (type == JsonOwlObjectType.IntersectionOf) {
				return f.getOWLObjectIntersectionOf(clsExpressions);
			}
			else {
				throw new UnknownIdentifierException("Unsupported expression type: "+type);
			}
		}
		
		static Set<OWLClassExpression> parse(OWLOntology o, JsonOwlObject[] expressions)
				throws Exception {
			if (expressions.length == 0) {
				throw new Exception("Missing expressions: empty expression list is not allowed.");
			}
			Set<OWLClassExpression> clsExpressions = new HashSet<OWLClassExpression>();
			for (JsonOwlObject m3Expression : expressions) {
				OWLClassExpression ce = parse(o, m3Expression);
				clsExpressions.add(ce);
			}
			return clsExpressions;
		}
	}
}
