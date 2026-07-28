package org.geneontology.minerva.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.Test;
import org.openrdf.model.BNode;
import org.openrdf.model.Literal;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.model.vocabulary.OWL;
import org.openrdf.model.vocabulary.RDF;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

public class DeterministicTurtleRendererTest {

    private static final String MODEL = "http://model.geneontology.org/0001";
    private static final String OBO = "http://purl.obolibrary.org/obo/";
    private static final String LEGO = "http://geneontology.org/lego/";
    private static final String OWL_NS = "http://www.w3.org/2002/07/owl#";
    private static final String DATE = "http://purl.org/dc/elements/1.1/date";

    /**
     * Build a logical graph. The blank node identifiers, the statement order and the
     * date annotation on the first axiom are all parameterized so the tests can vary
     * them independently. The two axioms describe different triples (distinct
     * skeletons) so that each gets a stable, independent position.
     */
    private List<Statement> buildGraph(String bnodeNamespace, long shuffleSeed, String firstAxiomDate) {
        ValueFactory f = new ValueFactoryImpl();
        List<Statement> s = new ArrayList<>();

        // ontology declaration
        s.add(f.createStatement(f.createURI(MODEL), RDF.TYPE, OWL.ONTOLOGY));

        // named individuals with types
        s.add(f.createStatement(f.createURI(MODEL + "/i1"), RDF.TYPE, f.createURI(OBO + "GO_0003674")));
        s.add(f.createStatement(f.createURI(MODEL + "/i2"), RDF.TYPE, f.createURI(OBO + "GO_0008150")));
        s.add(f.createStatement(f.createURI(MODEL + "/i3"), RDF.TYPE, f.createURI(OBO + "GO_0005575")));
        s.add(f.createStatement(f.createURI(MODEL + "/i1"), f.createURI(OBO + "RO_0002333"), f.createURI(MODEL + "/i2")));
        s.add(f.createStatement(f.createURI(MODEL + "/i1"), f.createURI(OBO + "RO_0002333"), f.createURI(MODEL + "/i3")));

        // two owl:Axiom reification nodes (subject-only blank nodes), each about a
        // different triple
        for (int i = 1; i <= 2; i++) {
            String target = MODEL + "/i" + (i + 1);
            String date = (i == 1 && firstAxiomDate != null) ? firstAxiomDate : "2026-0" + i + "-01";
            org.openrdf.model.BNode ax = f.createBNode(bnodeNamespace + "ax" + i);
            s.add(f.createStatement(ax, RDF.TYPE, f.createURI(OWL_NS + "Axiom")));
            s.add(f.createStatement(ax, f.createURI(OWL_NS + "annotatedSource"), f.createURI(MODEL + "/i1")));
            s.add(f.createStatement(ax, f.createURI(OWL_NS + "annotatedProperty"), f.createURI(OBO + "RO_0002333")));
            s.add(f.createStatement(ax, f.createURI(OWL_NS + "annotatedTarget"), f.createURI(target)));
            s.add(f.createStatement(ax, f.createURI(LEGO + "evidence"), f.createURI(MODEL + "/ev" + i)));
            s.add(f.createStatement(ax, f.createURI(DATE), f.createLiteral(date)));
        }

        // a blank node referenced more than once (must keep a stable id)
        org.openrdf.model.BNode shared = f.createBNode(bnodeNamespace + "shared");
        s.add(f.createStatement(shared, RDF.TYPE, f.createURI(OWL_NS + "Class")));
        s.add(f.createStatement(f.createURI(MODEL + "/i1"), f.createURI("http://www.w3.org/2000/01/rdf-schema#subClassOf"), shared));
        s.add(f.createStatement(f.createURI(MODEL + "/i2"), f.createURI("http://www.w3.org/2000/01/rdf-schema#subClassOf"), shared));

        Collections.shuffle(s, new Random(shuffleSeed));
        return s;
    }

    private String render(List<Statement> statements) throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DeterministicTurtleRenderer.render(statements, MODEL, out);
        return new String(out.toByteArray(), "UTF-8");
    }

    private static String stripDateLines(String ttl) {
        StringBuilder sb = new StringBuilder();
        for (String line : ttl.split("\n", -1)) {
            if (!line.contains("dc:date")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    public void rendersIdenticalOutputRegardlessOfBlankNodeIdsAndOrder() throws Exception {
        String a = render(buildGraph("aaa", 1, null));
        String b = render(buildGraph("zzz", 99, null));
        assertEquals("Rendered Turtle must be byte-identical for the same RDF graph", a, b);
    }

    @Test
    public void doesNotLeakTriplestoreBlankNodeIds() throws Exception {
        String ttl = render(buildGraph("tttt", 7, null));
        // The triplestore-assigned identifiers must not appear in the output.
        assertFalse(ttl.contains("tttt"));
        // Subject-only axiom nodes must be rendered in anonymous blank node form
        // ("[ a owl:Axiom"), not as labeled blank nodes ("_:b0 a owl:Axiom").
        assertTrue("axiom nodes should be rendered as anonymous blank nodes",
                Pattern.compile("\\[\\s+a\\s+owl:Axiom").matcher(ttl).find());
        assertFalse("axiom nodes must not be rendered as labeled blank nodes",
                Pattern.compile("_:\\S+\\s+a\\s+owl:Axiom").matcher(ttl).find());
    }

    @Test
    public void usesConfiguredPrefixes() throws Exception {
        String ttl = render(buildGraph("p", 3, null));
        assertTrue(ttl.contains("@prefix gomodel:"));
        assertTrue(ttl.contains("@prefix obo:"));
        assertTrue(ttl.contains("@prefix owl:"));
        assertTrue(ttl.contains("@prefix oboInOwl:"));
        assertTrue(ttl.contains("@prefix layout:"));
        // gomodel: abbreviates the model IRI itself; the empty ':' prefix abbreviates
        // the model's own entities (which live under modelIRI + "/")
        assertTrue(ttl.contains("gomodel:0001"));
        assertTrue(ttl.contains("@prefix :"));
        assertTrue(ttl.contains(":i1"));
    }

    /**
     * Editing an axiom's annotation (here, its date) should change only the affected
     * line and must not move the axiom's block: its position is derived from the
     * triple it annotates, not from its annotations. Stripping the date lines from
     * both renderings should therefore leave identical text.
     */
    @Test
    public void editingAxiomAnnotationKeepsBlockInPlace() throws Exception {
        String base = render(buildGraph("n", 4, null));
        String edited = render(buildGraph("n", 4, "2099-12-31"));
        assertNotEquals(base, edited);
        assertTrue(edited.contains("2099-12-31"));
        assertEquals("changing an axiom annotation must not reorder the output",
                stripDateLines(base), stripDateLines(edited));
    }

    /**
     * The renderer must be lossless: the rendered Turtle, parsed back, must contain
     * exactly the input triples - nothing added, dropped, or altered - up to blank
     * node identity (checked via graph isomorphism).
     */
    @Test
    public void outputContainsExactlyTheInputTriples() throws Exception {
        List<Statement> input = buildGraph("iso", 11, null);
        Model inputModel = toJenaModel(input);

        String ttl = render(input);
        Model outputModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(outputModel, new ByteArrayInputStream(ttl.getBytes("UTF-8")), MODEL + "/", Lang.TURTLE);

        assertEquals("triple count must be preserved", inputModel.size(), outputModel.size());
        assertTrue("rendered output must contain exactly the input triples (up to blank node identity)",
                inputModel.isIsomorphicWith(outputModel));
    }

    /** Convert Sesame statements to a Jena model for graph comparison. */
    private static Model toJenaModel(List<Statement> statements) {
        Model model = ModelFactory.createDefaultModel();
        Map<String, Resource> bnodes = new HashMap<>();
        for (Statement s : statements) {
            Resource subj = s.getSubject() instanceof BNode
                    ? bnodes.computeIfAbsent(((BNode) s.getSubject()).getID(), k -> model.createResource())
                    : model.createResource(s.getSubject().stringValue());
            Property pred = model.createProperty(s.getPredicate().stringValue());
            Value o = s.getObject();
            RDFNode obj;
            if (o instanceof BNode) {
                obj = bnodes.computeIfAbsent(((BNode) o).getID(), k -> model.createResource());
            } else if (o instanceof URI) {
                obj = model.createResource(o.stringValue());
            } else {
                Literal lit = (Literal) o;
                if (lit.getLanguage() != null) {
                    obj = model.createLiteral(lit.getLabel(), lit.getLanguage());
                } else if (lit.getDatatype() != null) {
                    obj = model.createTypedLiteral(lit.getLabel(),
                            TypeMapper.getInstance().getSafeTypeByName(lit.getDatatype().stringValue()));
                } else {
                    obj = model.createLiteral(lit.getLabel());
                }
            }
            model.add(subj, pred, obj);
        }
        return model;
    }
}
