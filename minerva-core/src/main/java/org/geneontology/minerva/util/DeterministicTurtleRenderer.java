package org.geneontology.minerva.util;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.openrdf.model.BNode;
import org.openrdf.model.Literal;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders a set of RDF statements (as read from Blazegraph via the Sesame/OpenRDF
 * API) to Turtle in a deterministic way, so that the same RDF graph always produces
 * byte-identical output regardless of the machine or the order in which the
 * triples were returned by the triplestore.
 * <p>
 * Determinism is achieved by:
 * <ul>
 * <li>using a fixed set of namespace prefixes;</li>
 * <li>assigning every blank node a content-derived identifier instead of the
 * volatile identifier handed out by the triplestore. This both fixes the iteration
 * order of Jena's pretty Turtle writer and keeps any identifiers that must be
 * written (for blank nodes referenced more than once) stable across runs;</li>
 * <li>delegating the actual serialization to Jena's {@code TURTLE_PRETTY} writer,
 * which renders blank nodes anonymously ({@code [ ... ]}) wherever possible and
 * sorts predicates within each subject.</li>
 * </ul>
 * For {@code owl:Axiom} reification nodes the identifier (and therefore the output
 * position) is derived only from the triple the axiom is about
 * ({@code owl:annotatedSource}/{@code annotatedProperty}/{@code annotatedTarget}),
 * so that editing an axiom's annotations (evidence, dates, comments, ...) changes
 * only the affected lines without moving the whole block.
 */
public class DeterministicTurtleRenderer {

    private static final String OWL_NS = "http://www.w3.org/2002/07/owl#";
    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String OWL_AXIOM = OWL_NS + "Axiom";
    private static final String ANNOTATED_SOURCE = OWL_NS + "annotatedSource";
    private static final String ANNOTATED_PROPERTY = OWL_NS + "annotatedProperty";
    private static final String ANNOTATED_TARGET = OWL_NS + "annotatedTarget";

    // Separators for content keys, chosen so they cannot occur in IRIs or labels.
    private static final String SEP = "\u0001";
    private static final String PART_SEP = "\u0002";

    private static final Map<String, String> PREFIXES = buildPrefixes();

    private static Map<String, String> buildPrefixes() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        p.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        p.put("owl", "http://www.w3.org/2002/07/owl#");
        p.put("xsd", "http://www.w3.org/2001/XMLSchema#");
        p.put("dc", "http://purl.org/dc/elements/1.1/");
        p.put("dcterms", "http://purl.org/dc/terms/");
        p.put("obo", "http://purl.obolibrary.org/obo/");
        p.put("oboInOwl", "http://www.geneontology.org/formats/oboInOwl#");
        p.put("lego", "http://geneontology.org/lego/");
        p.put("layout", "http://geneontology.org/lego/hint/layout/");
        p.put("pav", "http://purl.org/pav/");
        p.put("skos", "http://www.w3.org/2004/02/skos/core#");
        p.put("biolink", "https://w3id.org/biolink/vocab/");
        p.put("gomodel", "http://model.geneontology.org/");
        return Collections.unmodifiableMap(p);
    }

    /**
     * Write the given statements to {@code out} as deterministic Turtle. The
     * {@code modelId} is the model/ontology IRI; the model's own entities (which live
     * under {@code modelId + "/"}) are abbreviated with the empty {@code :} prefix.
     * The stream is not closed by this method.
     */
    public static void render(Collection<Statement> statements, String modelId, OutputStream out) {
        DeterministicTurtleRenderer renderer = new DeterministicTurtleRenderer(statements, modelId);
        renderer.write(out);
    }

    // outgoing statements grouped by subject, used to describe blank nodes
    private final Map<Resource, List<Statement>> outgoing = new HashMap<>();
    private final Collection<Statement> statements;
    private final String modelBaseIri;
    private final Map<BNode, String> keyCache = new HashMap<>();
    private final Set<BNode> keyInProgress = new HashSet<>();
    private final Map<BNode, String> bnodeLabels = new HashMap<>();

    private DeterministicTurtleRenderer(Collection<Statement> statements, String modelId) {
        this.statements = statements;
        this.modelBaseIri = (modelId == null) ? null : modelId + "/";
        for (Statement s : statements) {
            outgoing.computeIfAbsent(s.getSubject(), k -> new ArrayList<>()).add(s);
        }
        assignBlankNodeLabels();
    }

    private void write(OutputStream out) {
        Model model = ModelFactory.createDefaultModel();
        // Insert statements in canonical order. Combined with the content-derived
        // blank node ids this makes Jena's output byte-stable across runs.
        List<Statement> sorted = new ArrayList<>(statements);
        sorted.sort(Comparator
                .comparing((Statement s) -> key(s.getSubject()))
                .thenComparing(s -> key(s.getPredicate()))
                .thenComparing(s -> key(s.getObject())));
        for (Statement s : sorted) {
            org.apache.jena.rdf.model.Resource subj = toJenaResource(model, s.getSubject());
            Property pred = model.createProperty(s.getPredicate().stringValue());
            RDFNode obj = toJenaNode(model, s.getObject());
            model.add(subj, pred, obj);
        }
        for (Map.Entry<String, String> e : PREFIXES.entrySet()) {
            model.setNsPrefix(e.getKey(), e.getValue());
        }
        if (modelBaseIri != null) {
            model.setNsPrefix("", modelBaseIri);
        }
        RDFDataMgr.write(out, model, RDFFormat.TURTLE_PRETTY);
    }

    private org.apache.jena.rdf.model.Resource toJenaResource(Model model, Resource res) {
        if (res instanceof BNode) {
            return model.createResource(new AnonId(bnodeLabels.get(res)));
        }
        return model.createResource(res.stringValue());
    }

    private RDFNode toJenaNode(Model model, Value value) {
        if (value instanceof Resource) {
            return toJenaResource(model, (Resource) value);
        }
        Literal lit = (Literal) value;
        if (lit.getLanguage() != null) {
            return model.createLiteral(lit.getLabel(), lit.getLanguage());
        }
        if (lit.getDatatype() != null) {
            RDFDatatype dt = TypeMapper.getInstance().getSafeTypeByName(lit.getDatatype().stringValue());
            return model.createTypedLiteral(lit.getLabel(), dt);
        }
        return model.createLiteral(lit.getLabel());
    }

    /**
     * Assign a stable, content-derived identifier to every blank node. The
     * identifier is hashed from the node's {@link #positioningKey}; nodes that hash
     * to the same value are disambiguated, in order of their full description, with
     * a counter so that distinct nodes remain distinct.
     */
    private void assignBlankNodeLabels() {
        Set<BNode> bnodes = new HashSet<>();
        for (Statement s : statements) {
            if (s.getSubject() instanceof BNode) {
                bnodes.add((BNode) s.getSubject());
            }
            if (s.getObject() instanceof BNode) {
                bnodes.add((BNode) s.getObject());
            }
        }
        List<BNode> sorted = new ArrayList<>(bnodes);
        sorted.sort(Comparator.comparing(this::positioningKey).thenComparing(this::key));
        String previousKey = null;
        int tie = 0;
        for (BNode b : sorted) {
            String pk = positioningKey(b);
            if (pk.equals(previousKey)) {
                tie++;
            } else {
                tie = 0;
                previousKey = pk;
            }
            bnodeLabels.put(b, sha1(pk) + "-" + tie);
        }
    }

    /**
     * The key that determines a blank node's identifier, and therefore its position
     * in the output. For {@code owl:Axiom} reification nodes this is derived only
     * from the triple the axiom is about, so that editing the axiom's annotations
     * does not move the block. For all other blank nodes the full description (see
     * {@link #key}) is used.
     */
    private String positioningKey(BNode bnode) {
        List<Statement> out = outgoing.get(bnode);
        if (out != null && isAxiom(out)) {
            List<String> parts = new ArrayList<>();
            for (Statement s : out) {
                String p = s.getPredicate().stringValue();
                if (p.equals(ANNOTATED_SOURCE) || p.equals(ANNOTATED_PROPERTY) || p.equals(ANNOTATED_TARGET)) {
                    parts.add(p + SEP + key(s.getObject()));
                }
            }
            Collections.sort(parts);
            return "axiom[" + String.join(PART_SEP, parts) + "]";
        }
        return key(bnode);
    }

    private static boolean isAxiom(List<Statement> out) {
        for (Statement s : out) {
            if (s.getPredicate().stringValue().equals(RDF_TYPE)
                    && (s.getObject() instanceof URI)
                    && s.getObject().stringValue().equals(OWL_AXIOM)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A content-based key for a value. For blank nodes this is built recursively
     * from the (predicate, object) pairs that describe the node, so it does not
     * depend on the blank node's triplestore-assigned identifier.
     */
    private String key(Value value) {
        if (value instanceof URI) {
            return "u" + SEP + value.stringValue();
        }
        if (value instanceof Literal) {
            Literal lit = (Literal) value;
            return "l" + SEP + lit.getLabel()
                    + SEP + (lit.getDatatype() == null ? "" : lit.getDatatype().stringValue())
                    + SEP + (lit.getLanguage() == null ? "" : lit.getLanguage());
        }
        BNode bnode = (BNode) value;
        String cached = keyCache.get(bnode);
        if (cached != null) {
            return cached;
        }
        if (!keyInProgress.add(bnode)) {
            // Cycle among blank nodes (not expected in GO-CAM data); break it.
            return "b" + SEP + "<cycle>";
        }
        List<String> parts = new ArrayList<>();
        List<Statement> out = outgoing.get(bnode);
        if (out != null) {
            for (Statement s : out) {
                parts.add(key(s.getPredicate()) + SEP + key(s.getObject()));
            }
        }
        Collections.sort(parts);
        String result = "b[" + String.join(PART_SEP, parts) + "]";
        keyInProgress.remove(bnode);
        keyCache.put(bnode, result);
        return result;
    }

    private static String sha1(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}