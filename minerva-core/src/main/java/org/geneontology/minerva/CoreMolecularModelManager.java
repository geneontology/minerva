package org.geneontology.minerva;

import com.google.common.base.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.vocabulary.OWL;
import org.apache.log4j.Logger;
import org.geneontology.jena.OWLtoRules;
import org.geneontology.jena.SesameJena;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.geneontology.rules.engine.RuleEngine;
import org.geneontology.rules.engine.Triple;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RioRDFXMLDocumentFormatFactory;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.io.OWLParserFactory;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;
import org.semanticweb.owlapi.oboformat.OBOFormatOWLAPIParserFactory;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.rio.RioMemoryTripleSource;
import org.semanticweb.owlapi.rio.RioParserImpl;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.PriorityCollection;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import owltools.vocab.OBOUpperVocabulary;
import scala.collection.JavaConverters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Manager and core operations for in memory MolecularModels (aka lego diagrams).
 * <p>
 * Any number of models can be loaded at any time <br>
 * TODO - impose some limit to avoid using too much memory
 * <p>
 * Each model is an OWLOntology, see {@link ModelContainer}.
 *
 * @param <METADATA> object for holding meta data associated with each operation
 */
public abstract class CoreMolecularModelManager<METADATA> {

    private static Logger LOG = Logger.getLogger(CoreMolecularModelManager.class);

    // axiom has evidence RO:0002612
    private static final IRI HAS_EVIDENCE_IRI = IRI.create("http://purl.obolibrary.org/obo/RO_0002612");
    // legacy
    private static final IRI HAS_EVIDENCE_IRI_OLD = AnnotationShorthand.evidence.getAnnotationProperty();

    private static final OWLAnnotationProperty HAS_SHORTHAND = OWLManager.getOWLDataFactory().getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#shorthand"));
    private static final OWLAnnotationProperty IN_SUBSET = OWLManager.getOWLDataFactory().getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#inSubset"));
    private static final Set<IRI> DO_NOT_ANNOTATE_SUBSETS = new HashSet<>();

    static {
        DO_NOT_ANNOTATE_SUBSETS.add(IRI.create("http://purl.obolibrary.org/obo/go#gocheck_do_not_annotate"));
        DO_NOT_ANNOTATE_SUBSETS.add(IRI.create("http://purl.obolibrary.org/obo/go#gocheck_do_not_manually_annotate"));
    }

    final OWLOntology tbox;
    //	final OWLReasonerFactory rf;
    //	final OWLReasoner tbox_reasoner;
    //replacing tbox_reasoner structural reasoner functionality with blazegraph queries over pre-inferred relations..
    private BlazegraphOntologyManager go_lego_repo;
    private final IRI tboxIRI;

    final Map<IRI, ModelContainer> modelMap = new HashMap<IRI, ModelContainer>();
    Set<IRI> additionalImports;

    private final RuleEngine ruleEngine;
    private final Map<IRI, String> legacyRelationIndex = new HashMap<IRI, String>();
    private final Map<IRI, String> tboxLabelIndex = new HashMap<IRI, String>();
    private final Map<IRI, String> tboxShorthandIndex = new HashMap<IRI, String>();
    private final Set<IRI> doNotAnnotateSubset = new HashSet<>();


    /**
     * Use start up time to create a unique prefix for id generation
     */
    static String uniqueTop = Long.toHexString(Math.abs((System.currentTimeMillis() / 1000)));
    static final AtomicLong instanceCounter = new AtomicLong(0L);

    /**
     * Generate a new id from the unique server prefix and a global counter
     *
     * @return id
     */
    private static String localUnique() {
        final long counterValue = instanceCounter.getAndIncrement();
        String unique = uniqueTop + String.format("%08d", counterValue);
        return unique;
    }

    /**
     * Check that the given string looks similar to a local unique id
     *
     * @param s
     * @return true if the string looks like a generated id
     */
    static boolean isLocalUnique(String s) {
        boolean result = false;
        if (s != null && s.length() > 8) {
            result = true;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (isHex(c) == false) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    private static boolean isHex(char c) {
        // check that char is a digit or a-e
        boolean result = false;
        if (Character.isDigit(c)) {
            result = true;
        } else if (c == 'a' || c == 'b' || c == 'c' || c == 'd' || c == 'e' || c == 'f') {
            result = true;
        }
        return result;
    }

    /**
     * Generate an id and prepend the given prefixes.
     * <p>
     * This method must should be used for model identifiers and individual identifiers.
     *
     * @param prefixes
     * @return id
     */
    static IRI generateId(CharSequence... prefixes) {
        StringBuilder sb = new StringBuilder();
        for (CharSequence prefix : prefixes) {
            sb.append(prefix);
        }
        sb.append(localUnique());
        return IRI.create(sb.toString());
    }

    /**
     * @param tbox
     * @throws OWLOntologyCreationException
     * @throws IOException
     */
    public CoreMolecularModelManager(OWLOntology tbox, String go_lego_repo_file, boolean downloadOntologyJournal) throws OWLOntologyCreationException, IOException {
        super();
        this.tbox = tbox;
        tboxIRI = getTboxIRI(tbox);
        this.ruleEngine = initializeRuleEngine();
        initializeLegacyRelationIndex();
        initializeTboxLabelIndex();
        initializeTboxShorthandIndex();
        initializeDoNotAnnotateSubset();
        if (go_lego_repo_file != null) {
            this.go_lego_repo = new BlazegraphOntologyManager(go_lego_repo_file, downloadOntologyJournal, tbox);
        }
        init();
    }


    private OWLReasoner initializeTboxReasoner(OWLOntology tbox) {
        OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
        OWLReasoner r = reasonerFactory.createReasoner(tbox);
        return r;
    }

    private static synchronized Set<OWLParserFactory> removeOBOParserFactories(OWLOntologyManager m) {
        // hacky workaround: remove the too liberal OBO parser
        PriorityCollection<OWLParserFactory> factories = m.getOntologyParsers();
        Set<OWLParserFactory> copied = new HashSet<>();
        for (OWLParserFactory factory : factories) {
            copied.add(factory);
        }
        for (OWLParserFactory factory : copied) {
            Class<?> cls = factory.getClass();
            boolean remove = false;
            if (OBOFormatOWLAPIParserFactory.class.equals(cls)) {
                remove = true;
            }
            if (remove) {
                factories.remove(factory);
            }
        }
        return copied;
    }

    private static synchronized void resetOBOParserFactories(OWLOntologyManager m, Set<OWLParserFactory> factories) {
        m.setOntologyParsers(factories);
    }

    /**
     * Executed before the init call {@link #init()}.
     *
     * @param tbox
     * @return IRI, never null
     * @throws OWLOntologyCreationException
     */
    protected IRI getTboxIRI(OWLOntology tbox) throws OWLOntologyCreationException {
        OWLOntologyID ontologyID = tbox.getOntologyID();
        if (ontologyID != null) {
            Optional<IRI> ontologyIRI = ontologyID.getOntologyIRI();
            if (ontologyIRI.isPresent()) {
                return ontologyIRI.get();
            }
        }
        throw new OWLOntologyCreationException("No ontology id available for tbox. An ontology IRI is required for the import into the abox.");
    }

    /**
     * @throws OWLOntologyCreationException
     */
    protected void init() throws OWLOntologyCreationException {
        // set default imports
        additionalImports = new HashSet<IRI>();
    }

    /**
     * @return core/source ontology
     */
    public OWLOntology getOntology() {
        return tbox;
    }

    public Map<IRI, String> getLegacyRelationShorthandIndex() {
        return Collections.unmodifiableMap(this.legacyRelationIndex);
    }

    public Map<IRI, String> getTboxLabelIndex() {
        return Collections.unmodifiableMap(this.tboxLabelIndex);
    }

    public Map<IRI, String> getTboxShorthandIndex() {
        return Collections.unmodifiableMap(this.tboxShorthandIndex);
    }

    public Set<IRI> getDoNotAnnotateSubset() {
        return Collections.unmodifiableSet(this.doNotAnnotateSubset);
    }

    public RuleEngine getRuleEngine() {
        return ruleEngine;
    }

    private RuleEngine initializeRuleEngine() {
        Set<Rule> rules = new HashSet<>();
        rules.addAll(JavaConverters.setAsJavaSetConverter(OWLtoRules.translate(getOntology(), Imports.INCLUDED, true, true, true, true)).asJava());
        rules.addAll(JavaConverters.setAsJavaSetConverter(OWLtoRules.indirectRules(getOntology())).asJava());
        return new RuleEngine(Bridge.rulesFromJena(JavaConverters.asScalaSetConverter(rules).asScala()), true);
    }

    public WorkingMemory createInferredModel(OWLOntology abox, IRI modelId) {
        Set<Statement> statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(abox)).asJava();
        Set<Triple> triples = statements.stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet());
        try {
            // Using model's ontology IRI so that a spurious different ontology declaration triple isn't added
            //	OWLOntology schemaOntology = OWLManager.createOWLOntologyManager().createOntology(getOntology().getRBoxAxioms(Imports.INCLUDED), modelId);
            // I think the re-use of the model IRI as the IRI of the rule ontology has some weird effects on the model in question, rendering its contents inaccesible.
            OWLOntologyManager tmp_man = OWLManager.createOWLOntologyManager();
            OWLOntology schemaOntology = tmp_man.createOntology(IRI.create("http://therules.org"));
            Set<OWLAxiom> owl_rules = getOntology().getRBoxAxioms(Imports.INCLUDED);
            tmp_man.addAxioms(schemaOntology, owl_rules);
            //
            Set<Statement> schemaStatements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(schemaOntology)).asJava();
            triples.addAll(schemaStatements.stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet()));
        } catch (OWLOntologyCreationException e) {
            LOG.error("Couldn't add rbox statements to data model.", e);
        }
        return getRuleEngine().processTriples(JavaConverters.asScalaSetConverter(triples).asScala());

    }

    /**
     * Return Arachne working memory representing LEGO model combined with inference rules.
     * This model will not remain synchronized with changes to data.
     *
     * @param modelId
     * @return Jena model
     */
    public WorkingMemory createInferredModel(IRI modelId) {
        return createInferredModel(getModelAbox(modelId), modelId);
    }

    public WorkingMemory createCanonicalInferredModel(IRI modelId) {
        //swap out any non-canonical types
        OWLOntology source_abox = getModelAbox(modelId);
        OWLOntologyManager aman = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = aman.getOWLDataFactory();
        OWLAnnotationProperty canonical_record = df.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/canonical_record"));
        OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
        try {
            OWLOntology abox = aman.copyOntology(source_abox, OntologyCopy.DEEP);
            OWLReasoner abox_reasoner = reasonerFactory.createReasoner(abox);
            //convert to canonical wherever possible
            abox.getIndividualsInSignature().forEach(i -> {
                Set<OWLClass> types = abox_reasoner.getTypes(i, true).getFlattened();
                for (OWLClass type : types) {
                    Collection<OWLAnnotation> canons = EntitySearcher.getAnnotationObjects(type, tbox, canonical_record);
                    //adding multiple types to an instance of a set object is
                    //probably not kosher.. but seems to work for now.
                    //more correct to create new instances for each
                    if (canons != null && canons.size() > 0) {
                        for (OWLAnnotation canon : canons) {
                            if (canon.getValue().asIRI().isPresent()) {
                                OWLClass canonical = df.getOWLClass(canon.getValue().asIRI().get());
                                //direct swap
                                //remove the old one
                                OWLClassAssertionAxiom original = df.getOWLClassAssertionAxiom(type, i);
                                aman.removeAxiom(abox, original);
                                //add the new one
                                OWLClassAssertionAxiom canonical_type = df.getOWLClassAssertionAxiom(canonical, i);
                                aman.addAxiom(abox, canonical_type);
                            }
                        }
                    }
                }
            });
            WorkingMemory inferred = createInferredModel(abox, modelId);
            abox_reasoner.dispose();
            aman.removeOntology(abox);
            return inferred;
        } catch (OWLOntologyCreationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return createInferredModel(source_abox, modelId);
        }
    }

    private void initializeLegacyRelationIndex() {
        synchronized (legacyRelationIndex) {
            OWLAnnotationProperty rdfsLabel = OWLManager.getOWLDataFactory().getRDFSLabel();
            for (OWLOntology ont : this.getOntology().getImportsClosure()) {
                for (OWLObjectProperty prop : ont.getObjectPropertiesInSignature()) {
                    for (OWLAnnotationAssertionAxiom axiom : ont.getAnnotationAssertionAxioms(prop.getIRI())) {
                        if (axiom.getProperty().equals(rdfsLabel)) {
                            Optional<OWLLiteral> literalOpt = axiom.getValue().asLiteral();
                            if (literalOpt.isPresent()) {
                                String label = literalOpt.get().getLiteral();
                                legacyRelationIndex.put(prop.getIRI(), label.replaceAll(" ", "_").replaceAll(",", ""));
                            }
                        }
                    }
                }
            }
        }
    }

    private void initializeTboxLabelIndex() {
        synchronized (tboxLabelIndex) {
            OWLAnnotationProperty rdfsLabel = OWLManager.getOWLDataFactory().getRDFSLabel();
            for (OWLAnnotationAssertionAxiom axiom : this.getOntology().getAxioms(AxiomType.ANNOTATION_ASSERTION, Imports.INCLUDED)) {
                if (axiom.getProperty().equals(rdfsLabel) && (axiom.getSubject() instanceof IRI) && axiom.getValue() instanceof OWLLiteral) {
                    IRI subject = (IRI) (axiom.getSubject());
                    String label = axiom.getValue().asLiteral().get().getLiteral();
                    tboxLabelIndex.put(subject, label);
                }
            }
        }
    }

    private void initializeTboxShorthandIndex() {
        synchronized (tboxShorthandIndex) {
            for (OWLAnnotationAssertionAxiom axiom : this.getOntology().getAxioms(AxiomType.ANNOTATION_ASSERTION, Imports.INCLUDED)) {
                if (axiom.getProperty().equals(HAS_SHORTHAND) && (axiom.getSubject() instanceof IRI) && axiom.getValue() instanceof OWLLiteral) {
                    IRI subject = (IRI) (axiom.getSubject());
                    String shorthand = axiom.getValue().asLiteral().get().getLiteral();
                    tboxShorthandIndex.put(subject, shorthand);
                }
            }
        }
    }

    private void initializeDoNotAnnotateSubset() {
        synchronized (doNotAnnotateSubset) {
            for (OWLAnnotationAssertionAxiom axiom : this.getOntology().getAxioms(AxiomType.ANNOTATION_ASSERTION, Imports.INCLUDED)) {
                if (axiom.getProperty().equals(IN_SUBSET) && (axiom.getSubject() instanceof IRI) && DO_NOT_ANNOTATE_SUBSETS.contains(axiom.getValue())) {
                    doNotAnnotateSubset.add((IRI) (axiom.getSubject()));
                }
            }
        }
    }

    /**
     * Add additional import declarations for any newly generated model.
     *
     * @param imports
     */
    public void addImports(Iterable<String> imports) {
        if (imports != null) {
            for (String importIRIString : imports) {
                additionalImports.add(IRI.create(importIRIString));
            }
        }
    }

    public Collection<IRI> getImports() {
        Set<IRI> allImports = new HashSet<IRI>();
        allImports.add(tboxIRI);
        allImports.addAll(additionalImports);
        return allImports;
    }

    /**
     * @param modelId
     * @return all individuals in the model
     */
    public Set<OWLNamedIndividual> getIndividuals(IRI modelId) {
        ModelContainer mod = getModel(modelId);
        return mod.getAboxOntology().getIndividualsInSignature();
    }


    //	/**
    //	 * @param mod
    //	 * @param q
    //	 * @return all individuals in the model that satisfy q
    //	 */
    //	public Set<OWLNamedIndividual> getIndividualsByQuery(ModelContainer mod, OWLClassExpression q) {
    //		return mod.getReasoner().getInstances(q, false).getFlattened();
    //	}

    /**
     * @param model
     * @param ce
     * @param metadata
     * @return individual
     */
    public OWLNamedIndividual createIndividual(ModelContainer model, OWLClassExpression ce, METADATA metadata) {
        OWLNamedIndividual individual = createIndividual(model, ce, null, metadata);
        return individual;
    }

    OWLNamedIndividual createIndividual(ModelContainer model, OWLClassExpression ce, Set<OWLAnnotation> annotations, METADATA metadata) {
        Pair<OWLNamedIndividual, Set<OWLAxiom>> pair = createIndividual(model.getModelId(), model.getAboxOntology(), ce, annotations);
        addAxioms(model, pair.getRight(), metadata);
        return pair.getLeft();
    }

    OWLNamedIndividual createIndividualWithIRI(ModelContainer model, IRI individualIRI, Set<OWLAnnotation> annotations, METADATA metadata) {
        Pair<OWLNamedIndividual, Set<OWLAxiom>> pair = createIndividualInternal(individualIRI, model.getAboxOntology(), null, annotations);
        addAxioms(model, pair.getRight(), metadata);
        return pair.getLeft();
    }

    public static Pair<OWLNamedIndividual, Set<OWLAxiom>> createIndividual(IRI modelId, OWLOntology abox, OWLClassExpression ce, Set<OWLAnnotation> annotations) {
        IRI iri = generateId(modelId, "/");
        return createIndividualInternal(iri, abox, ce, annotations);
    }

    private static Pair<OWLNamedIndividual, Set<OWLAxiom>> createIndividualInternal(IRI iri, OWLOntology abox, OWLClassExpression ce, Set<OWLAnnotation> annotations) {
        LOG.info("Generating individual for IRI: " + iri);
        OWLDataFactory f = abox.getOWLOntologyManager().getOWLDataFactory();
        OWLNamedIndividual i = f.getOWLNamedIndividual(iri);

        // create axioms
        Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
        // declaration
        axioms.add(f.getOWLDeclarationAxiom(i));
        // annotation assertions
        if (annotations != null) {
            for (OWLAnnotation annotation : annotations) {
                axioms.add(f.getOWLAnnotationAssertionAxiom(iri, annotation));
            }
        }

        if (ce != null) {
            OWLClassAssertionAxiom typeAxiom = createType(f, i, ce);
            if (typeAxiom != null) {
                axioms.add(typeAxiom);
            }
        }

        return Pair.of(i, axioms);
    }

    public static class DeleteInformation {
        public final Set<IRI> usedIRIs = new HashSet<IRI>();
        public final Set<OWLObjectPropertyAssertionAxiom> updated = new HashSet<OWLObjectPropertyAssertionAxiom>();
        public final Set<IRI> touched = new HashSet<IRI>();
    }

    /**
     * Deletes an individual and return all IRIs used as an annotation value.
     * Also tries to delete all annotations (OWLObjectPropertyAssertionAxiom
     * annotations and OWLAnnotationAssertionAxiom) with the individual IRI as
     * value.
     *
     * @param model
     * @param i
     * @param metadata
     * @return set of IRIs used in annotations
     */
    public DeleteInformation deleteIndividual(ModelContainer model, OWLNamedIndividual i, METADATA metadata) {
        Set<OWLAxiom> toRemoveAxioms = new HashSet<OWLAxiom>();
        final DeleteInformation deleteInformation = new DeleteInformation();

        final OWLOntology ont = model.getAboxOntology();
        final OWLDataFactory f = model.getOWLDataFactory();

        // Declaration axiom
        toRemoveAxioms.add(model.getOWLDataFactory().getOWLDeclarationAxiom(i));

        // Logic axiom
        for (OWLAxiom ax : ont.getAxioms(i, Imports.EXCLUDED)) {
            extractEvidenceIRIValues(ax.getAnnotations(), deleteInformation.usedIRIs);
            toRemoveAxioms.add(ax);
        }

        // OWLObjectPropertyAssertionAxiom
        Set<OWLObjectPropertyAssertionAxiom> allAssertions = ont.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION);
        final IRI iIRI = i.getIRI();
        for (OWLObjectPropertyAssertionAxiom ax : allAssertions) {
            if (toRemoveAxioms.contains(ax) == false) {
                Set<OWLNamedIndividual> currentIndividuals = ax.getIndividualsInSignature();
                if (currentIndividuals.contains(i)) {
                    extractEvidenceIRIValues(ax.getAnnotations(), deleteInformation.usedIRIs);
                    toRemoveAxioms.add(ax);
                    continue;
                }
                // check annotations for deleted individual IRI
                Set<OWLAnnotation> annotations = ax.getAnnotations();
                Set<OWLAnnotation> removeAnnotations = new HashSet<OWLAnnotation>();
                for (OWLAnnotation annotation : annotations) {
                    if (iIRI.equals(annotation.getValue())) {
                        removeAnnotations.add(annotation);
                    }
                }
                // if there is an annotations that needs to be removed,
                // recreate axiom with cleaned annotation set
                if (removeAnnotations.isEmpty() == false) {
                    annotations.removeAll(removeAnnotations);
                    toRemoveAxioms.add(ax);
                    deleteInformation.updated.add(f.
                            getOWLObjectPropertyAssertionAxiom(
                                    ax.getProperty(), ax.getSubject(), ax.getObject(), annotations));
                }
            }
        }
        // OWLAnnotationAssertionAxiom
        Set<OWLAnnotationAssertionAxiom> annotationAssertionAxioms = ont.getAnnotationAssertionAxioms(i.getIRI());
        for (OWLAnnotationAssertionAxiom axiom : annotationAssertionAxioms) {
            extractEvidenceIRIValues(axiom.getAnnotation(), deleteInformation.usedIRIs);
            toRemoveAxioms.add(axiom);
        }

        // search for all annotations which use individual IRI as value
        Set<OWLAnnotationAssertionAxiom> axioms = ont.getAxioms(AxiomType.ANNOTATION_ASSERTION);
        for (OWLAnnotationAssertionAxiom ax : axioms) {
            if (toRemoveAxioms.contains(ax) == false) {
                if (iIRI.equals(ax.getValue())) {
                    toRemoveAxioms.add(ax);
                    OWLAnnotationSubject subject = ax.getSubject();
                    subject.accept(new OWLAnnotationSubjectVisitor() {

                        @Override
                        public void visit(OWLAnonymousIndividual individual) {
                            // do nothing
                        }

                        @Override
                        public void visit(IRI iri) {
                            // check if they subject is a declared named individual
                            if (ont.containsIndividualInSignature(iri)) {
                                deleteInformation.touched.add(iri);
                            }
                        }
                    });
                }
            }
        }

        removeAxioms(model, toRemoveAxioms, metadata);
        if (deleteInformation.updated.isEmpty() == false) {
            addAxioms(model, deleteInformation.updated, metadata);
        }

        return deleteInformation;
    }

    public static Set<IRI> extractEvidenceIRIValues(Set<OWLAnnotation> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return Collections.emptySet();
        }
        Set<IRI> iriSet = new HashSet<IRI>();
        extractEvidenceIRIValues(annotations, iriSet);
        return iriSet;
    }

    private static void extractEvidenceIRIValues(Set<OWLAnnotation> annotations, final Set<IRI> iriSet) {
        if (annotations != null) {
            for (OWLAnnotation annotation : annotations) {
                extractEvidenceIRIValues(annotation, iriSet);
            }
        }
    }

    private static void extractEvidenceIRIValues(OWLAnnotation annotation, final Set<IRI> iriSet) {
        if (annotation != null) {
            OWLAnnotationProperty property = annotation.getProperty();
            if (HAS_EVIDENCE_IRI.equals(property.getIRI()) || HAS_EVIDENCE_IRI_OLD.equals(property.getIRI())) {
                annotation.getValue().accept(new OWLAnnotationValueVisitor() {

                    @Override
                    public void visit(OWLLiteral literal) {
                        // ignore
                    }

                    @Override
                    public void visit(OWLAnonymousIndividual individual) {
                        // ignore
                    }

                    @Override
                    public void visit(IRI iri) {
                        iriSet.add(iri);
                    }
                });
            }
        }
    }

    public void addAnnotations(ModelContainer model, OWLNamedIndividual i, Collection<OWLAnnotation> annotations, METADATA metadata) {
        addAnnotations(model, i.getIRI(), annotations, metadata);
    }

    public void addAnnotations(ModelContainer model, IRI subject, Collection<OWLAnnotation> annotations, METADATA metadata) {
        Set<OWLAxiom> axioms = new HashSet<OWLAxiom>();
        OWLDataFactory f = model.getOWLDataFactory();
        for (OWLAnnotation annotation : annotations) {
            axioms.add(f.getOWLAnnotationAssertionAxiom(subject, annotation));
        }
        addAxioms(model, axioms, metadata);
    }

    public void updateAnnotation(ModelContainer model, IRI subject, OWLAnnotation update, METADATA metadata) {
        Set<OWLAxiom> removeAxioms = new HashSet<OWLAxiom>();
        OWLDataFactory f = model.getOWLDataFactory();
        Set<OWLAnnotationAssertionAxiom> existing = model.getAboxOntology().getAnnotationAssertionAxioms(subject);
        OWLAnnotationProperty target = update.getProperty();
        for (OWLAnnotationAssertionAxiom axiom : existing) {
            if (target.equals(axiom.getProperty())) {
                removeAxioms.add(axiom);
            }
        }
        removeAxioms(model, removeAxioms, metadata);
        addAxiom(model, f.getOWLAnnotationAssertionAxiom(subject, update), metadata);
    }

    public void addModelAnnotations(ModelContainer model, Collection<OWLAnnotation> annotations, METADATA metadata) {
        OWLOntology aBox = model.getAboxOntology();
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
        for (OWLAnnotation annotation : annotations) {
            changes.add(new AddOntologyAnnotation(aBox, annotation));
        }
        applyChanges(model, changes, metadata);
    }

    public void updateAnnotation(ModelContainer model, OWLAnnotation update, METADATA metadata) {
        OWLOntology aBox = model.getAboxOntology();
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
        Set<OWLAnnotation> existing = model.getAboxOntology().getAnnotations();
        OWLAnnotationProperty target = update.getProperty();
        for (OWLAnnotation annotation : existing) {
            if (target.equals(annotation.getProperty())) {
                changes.add(new RemoveOntologyAnnotation(aBox, annotation));
            }
        }
        changes.add(new AddOntologyAnnotation(aBox, update));
        applyChanges(model, changes, metadata);
    }

    public void removeAnnotations(ModelContainer model, OWLNamedIndividual i, Collection<OWLAnnotation> annotations, METADATA metadata) {
        removeAnnotations(model, i.getIRI(), annotations, metadata);
    }

    void removeAnnotations(ModelContainer model, IRI subject, Collection<OWLAnnotation> annotations, METADATA metadata) {
        OWLOntology ont = model.getAboxOntology();
        Set<OWLAxiom> toRemove = new HashSet<OWLAxiom>();
        Set<OWLAnnotationAssertionAxiom> candidates = ont.getAnnotationAssertionAxioms(subject);
        for (OWLAnnotationAssertionAxiom axiom : candidates) {
            OWLAnnotation annotation = axiom.getAnnotation();
            if (annotations.contains(annotation)) {
                toRemove.add(axiom);
            }
        }
        removeAxioms(model, toRemove, metadata);
    }

    public void removeAnnotations(ModelContainer model, Collection<OWLAnnotation> annotations, METADATA metadata) {
        OWLOntology aBox = model.getAboxOntology();
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
        for (OWLAnnotation annotation : annotations) {
            changes.add(new RemoveOntologyAnnotation(aBox, annotation));
        }
        applyChanges(model, changes, metadata);
    }

    public void addDataProperty(ModelContainer model,
                                OWLNamedIndividual i, OWLDataProperty prop, OWLLiteral literal,
                                METADATA metadata) {
        OWLAxiom axiom = model.getOWLDataFactory().getOWLDataPropertyAssertionAxiom(prop, i, literal);
        addAxiom(model, axiom, metadata);
    }

    public void removeDataProperty(ModelContainer model,
                                   OWLNamedIndividual i, OWLDataProperty prop, OWLLiteral literal,
                                   METADATA metadata) {
        OWLAxiom toRemove = null;
        Set<OWLDataPropertyAssertionAxiom> existing = model.getAboxOntology().getDataPropertyAssertionAxioms(i);
        for (OWLDataPropertyAssertionAxiom ax : existing) {
            if (prop.equals(ax.getProperty()) && literal.equals(ax.getObject())) {
                toRemove = ax;
                break;
            }
        }

        if (toRemove != null) {
            removeAxiom(model, toRemove, metadata);
        }
    }

    /**
     * Fetches a model by its Id
     *
     * @param id
     * @return wrapped model
     */
    public ModelContainer getModel(IRI id) {
        synchronized (modelMap) {
            // synchronized to avoid race condition for simultaneous loads of the same model
            if (!modelMap.containsKey(id)) {
                try {
                    loadModel(id, false);
                } catch (OWLOntologyCreationException e) {
                    LOG.info("Could not load model with id: " + id, e);
                }
            }
            return modelMap.get(id);
        }
    }

    /**
     * Retrieve the abox ontology. May skip loading the imports.
     * This method is mostly intended to read metadata from a model.
     *
     * @param id
     * @return abox, maybe without any imports loaded
     */
    public OWLOntology getModelAbox(IRI id) {
        ModelContainer model = modelMap.get(id);
        if (model != null) {
            return model.getAboxOntology();
        }
        OWLOntology abox = null;
        try {
            abox = loadModelABox(id);
        } catch (OWLOntologyCreationException e) {
            LOG.info("Could not load model with id: " + id, e);
        }
        return abox;
    }

    public boolean isModelModified(IRI modelId) {
        ModelContainer model = modelMap.get(modelId);
        if (model != null) {
            // ask model about modification
            return model.isModified();
        }
        // non in-memory models are considered not modified.
        return false;
    }

    /**
     * @param modelId
     * @return ontology
     * @throws OWLOntologyCreationException
     */
    protected abstract OWLOntology loadModelABox(IRI modelId) throws OWLOntologyCreationException;

    /**
     * @param modelId
     * @param manager
     * @return ontology
     * @throws OWLOntologyCreationException
     */
    protected abstract OWLOntology loadModelABox(IRI modelId, OWLOntologyManager manager) throws OWLOntologyCreationException;

    /**
     * @param id
     */
    public void unlinkModel(IRI id) {
        ModelContainer model = modelMap.get(id);
        model.dispose();
        modelMap.remove(id);
    }

    /**
     * @return ids for all loaded models
     */
    public Set<IRI> getModelIds() {
        return modelMap.keySet();
    }

    /**
     * internal method to cleanup this instance
     */
    public void dispose() {
        Set<IRI> ids = new HashSet<IRI>(getModelIds());
        for (IRI id : ids) {
            unlinkModel(id);
        }
    }

    /**
     * Export the ABox, will try to set the ontologyID to the given modelId (to
     * ensure import assumptions are met)
     *
     * @param model
     * @param ontologyFormat
     * @return modelContent
     * @throws OWLOntologyStorageException
     */
    public String exportModel(ModelContainer model, OWLDocumentFormat ontologyFormat) throws OWLOntologyStorageException {
        final OWLOntology aBox = model.getAboxOntology();
        final OWLOntologyManager manager = aBox.getOWLOntologyManager();

        // make sure the exported ontology has an ontologyId and that it maps to the modelId
        final IRI expectedABoxIRI = model.getModelId();
        Optional<IRI> currentABoxIRI = aBox.getOntologyID().getOntologyIRI();
        if (currentABoxIRI.isPresent() == false) {
            manager.applyChange(new SetOntologyID(aBox, expectedABoxIRI));
        } else {
            if (expectedABoxIRI.equals(currentABoxIRI) == false) {
                OWLOntologyID ontologyID = new OWLOntologyID(Optional.of(expectedABoxIRI), Optional.of(expectedABoxIRI));
                manager.applyChange(new SetOntologyID(aBox, ontologyID));
            }
        }

        // write the model into a buffer
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (ontologyFormat != null) {
            manager.saveOntology(aBox, ontologyFormat, outputStream);
        } else {
            manager.saveOntology(aBox, outputStream);
        }

        // extract the string from the buffer
        String modelString = outputStream.toString();
        return modelString;
    }

    /**
     * Try to load (or replace) a model with the given ontology. It is expected
     * that the content is an A-Box ontology, which imports the T-BOX. Also the
     * ontology ID is used to extract the modelId.<br>
     * <br>
     * This method will currently <b>NOT<b> work due to a bug in the OWL-API.
     * The functional syntax parser does not properly report the exceptions and
     * will return an ontology with an wrong ontology ID!
     *
     * @param modelData
     * @return modelId
     * @throws OWLOntologyCreationException
     */
    public ModelContainer importModel(String modelData) throws OWLOntologyCreationException {
        // load data from String
        final OWLOntologyManager manager = tbox.getOWLOntologyManager();
        final OWLOntologyDocumentSource documentSource = new StringDocumentSource(modelData);
        OWLOntology modelOntology;
        final Set<OWLParserFactory> originalFactories = removeOBOParserFactories(manager);
        try {
            modelOntology = manager.loadOntologyFromOntologyDocument(documentSource);
        } catch (OWLOntologyAlreadyExistsException e) {
            // exception is thrown if there is an ontology with the same ID already in memory
            OWLOntologyID id = e.getOntologyID();
            IRI existingModelId = id.getOntologyIRI().orNull();

            // remove the existing memory model
            unlinkModel(existingModelId);

            // try loading the import version (again)
            modelOntology = manager.loadOntologyFromOntologyDocument(documentSource);
        } finally {
            resetOBOParserFactories(manager, originalFactories);
        }

        // try to extract modelId
        IRI modelId = null;
        Optional<IRI> ontologyIRI = modelOntology.getOntologyID().getOntologyIRI();
        if (ontologyIRI.isPresent()) {
            modelId = ontologyIRI.get();
        }
        if (modelId == null) {
            throw new OWLOntologyCreationException("Could not extract the modelId from the given model");
        }
        // paranoia check
        ModelContainer existingModel = modelMap.get(modelId);
        if (existingModel != null) {
            unlinkModel(modelId);
        }

        // add to internal model
        ModelContainer newModel = addModel(modelId, modelOntology);

        return newModel;
    }

    protected abstract void loadModel(IRI modelId, boolean isOverride) throws OWLOntologyCreationException;

    ModelContainer addModel(IRI modelId, OWLOntology abox) throws OWLOntologyCreationException {
        ModelContainer m = new ModelContainer(modelId, tbox, abox);
        modelMap.put(modelId, m);
        return m;
    }

    /**
     * Adds ClassAssertion(c,i) to specified model
     *
     * @param modelId
     * @param i
     * @param c
     * @param metadata
     */
    public void addType(IRI modelId, OWLNamedIndividual i, OWLClass c, METADATA metadata) {
        ModelContainer model = getModel(modelId);
        addType(model, i, c, metadata);
    }

    /**
     * Adds ClassAssertion(c,i) to specified model
     *
     * @param model
     * @param i
     * @param c
     * @param metadata
     */
    public void addType(ModelContainer model, OWLIndividual i,
                        OWLClassExpression c, METADATA metadata) {
        OWLClassAssertionAxiom axiom = createType(model.getOWLDataFactory(), i, c);
        addAxiom(model, axiom, metadata);
    }

    /**
     * @param f
     * @param i
     * @param c
     * @return axiom
     */
    public static OWLClassAssertionAxiom createType(OWLDataFactory f, OWLIndividual i, OWLClassExpression c) {
        OWLClassAssertionAxiom axiom = f.getOWLClassAssertionAxiom(c, i);
        return axiom;
    }

    /**
     * Adds a ClassAssertion, where the class expression instantiated is an
     * ObjectSomeValuesFrom expression
     * <p>
     * Example: Individual: i Type: enabledBy some PRO_123
     *
     * @param modelId
     * @param i
     * @param p
     * @param filler
     * @param metadata
     */
    public void addType(IRI modelId,
                        OWLNamedIndividual i,
                        OWLObjectPropertyExpression p,
                        OWLClassExpression filler,
                        METADATA metadata) {
        ModelContainer model = getModel(modelId);
        addType(model, i, p, filler, metadata);
    }

    /**
     * Adds a ClassAssertion, where the class expression instantiated is an
     * ObjectSomeValuesFrom expression
     * <p>
     * Example: Individual: i Type: enabledBy some PRO_123
     *
     * @param model
     * @param i
     * @param p
     * @param filler
     * @param metadata
     */
    void addType(ModelContainer model,
                 OWLIndividual i,
                 OWLObjectPropertyExpression p,
                 OWLClassExpression filler,
                 METADATA metadata) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding " + i + " type " + p + " some " + filler);
        }
        OWLDataFactory f = model.getOWLDataFactory();
        OWLObjectSomeValuesFrom c = f.getOWLObjectSomeValuesFrom(p, filler);
        OWLClassAssertionAxiom axiom = f.getOWLClassAssertionAxiom(c, i);
        addAxiom(model, axiom, metadata);
    }

    /**
     * remove ClassAssertion(c,i) from the model
     *
     * @param model
     * @param i
     * @param ce
     * @param metadata
     */
    public void removeType(ModelContainer model, OWLIndividual i,
                           OWLClassExpression ce, METADATA metadata) {
        Set<OWLClassAssertionAxiom> allAxioms = model.getAboxOntology().getClassAssertionAxioms(i);
        // use search to remove also axioms with annotations
        for (OWLClassAssertionAxiom ax : allAxioms) {
            if (ce.equals(ax.getClassExpression())) {
                removeAxiom(model, ax, metadata);
            }
        }

    }

    void removeType(ModelContainer model,
                    OWLIndividual i,
                    OWLObjectPropertyExpression p,
                    OWLClassExpression filler,
                    METADATA metadata) {
        OWLDataFactory f = model.getOWLDataFactory();
        OWLClassAssertionAxiom axiom = f.getOWLClassAssertionAxiom(f.getOWLObjectSomeValuesFrom(p, filler), i);
        removeAxiom(model, axiom, metadata);
    }

    public void addFact(ModelContainer model, OBOUpperVocabulary vocabElement,
                        OWLNamedIndividual i, OWLNamedIndividual j, Set<OWLAnnotation> annotations, METADATA metadata) {
        OWLObjectProperty p = vocabElement.getObjectProperty(model.getAboxOntology());
        addFact(model, p, i, j, annotations, metadata);
    }

    public void addFact(ModelContainer model, OWLObjectPropertyExpression p,
                        OWLIndividual i, OWLIndividual j, Set<OWLAnnotation> annotations, METADATA metadata) {
        OWLObjectPropertyAssertionAxiom axiom = createFact(model.getOWLDataFactory(), p, i, j, annotations);
        addAxiom(model, axiom, metadata);
    }

    /**
     * @param f
     * @param p
     * @param i
     * @param j
     * @param annotations
     * @return axiom
     */
    public static OWLObjectPropertyAssertionAxiom createFact(OWLDataFactory f,
                                                             OWLObjectPropertyExpression p, OWLIndividual i, OWLIndividual j,
                                                             Set<OWLAnnotation> annotations) {
        final OWLObjectPropertyAssertionAxiom axiom;
        if (annotations != null && !annotations.isEmpty()) {
            axiom = f.getOWLObjectPropertyAssertionAxiom(p, i, j, annotations);
        } else {
            axiom = f.getOWLObjectPropertyAssertionAxiom(p, i, j);
        }
        return axiom;
    }

    public Set<IRI> removeFact(ModelContainer model, OWLObjectPropertyExpression p,
                               OWLIndividual i, OWLIndividual j, METADATA metadata) {
        OWLDataFactory f = model.getOWLDataFactory();

        OWLOntology ont = model.getAboxOntology();
        OWLAxiom toRemove = null;
        Set<IRI> iriSet = new HashSet<IRI>();
        Set<OWLObjectPropertyAssertionAxiom> candidates = ont.getObjectPropertyAssertionAxioms(i);
        for (OWLObjectPropertyAssertionAxiom axiom : candidates) {
            if (p.equals(axiom.getProperty()) && j.equals(axiom.getObject())) {
                toRemove = axiom;
                extractEvidenceIRIValues(axiom.getAnnotations(), iriSet);
                break;
            }
        }
        if (toRemove == null) {
            // fall back solution
            toRemove = f.getOWLObjectPropertyAssertionAxiom(p, i, j);
        }
        removeAxiom(model, toRemove, metadata);
        return iriSet;
    }

    public void addAnnotations(ModelContainer model, OWLObjectPropertyExpression p,
                               OWLNamedIndividual i, OWLNamedIndividual j, Set<OWLAnnotation> annotations,
                               METADATA metadata) {
        OWLOntology ont = model.getAboxOntology();
        Set<OWLObjectPropertyAssertionAxiom> axioms = ont.getObjectPropertyAssertionAxioms(i);
        OWLObjectPropertyAssertionAxiom toModify = null;
        for (OWLObjectPropertyAssertionAxiom axiom : axioms) {
            if (p.equals(axiom.getProperty()) && j.equals(axiom.getObject())) {
                toModify = axiom;
                break;
            }
        }
        addAnnotations(model, toModify, annotations, metadata);
    }

    void addAnnotations(ModelContainer model, OWLObjectPropertyAssertionAxiom toModify,
                        Set<OWLAnnotation> annotations, METADATA metadata) {
        if (toModify != null) {
            Set<OWLAnnotation> combindedAnnotations = new HashSet<OWLAnnotation>(annotations);
            combindedAnnotations.addAll(toModify.getAnnotations());
            modifyAnnotations(toModify, combindedAnnotations, model, metadata);
        }
    }

    public void updateAnnotation(ModelContainer model, OWLObjectPropertyExpression p,
                                 OWLNamedIndividual i, OWLNamedIndividual j, OWLAnnotation update,
                                 METADATA metadata) {
        OWLOntology ont = model.getAboxOntology();
        Set<OWLObjectPropertyAssertionAxiom> axioms = ont.getObjectPropertyAssertionAxioms(i);
        OWLObjectPropertyAssertionAxiom toModify = null;
        for (OWLObjectPropertyAssertionAxiom axiom : axioms) {
            if (p.equals(axiom.getProperty()) && j.equals(axiom.getObject())) {
                toModify = axiom;
                break;
            }
        }
        updateAnnotation(model, toModify, update, metadata);
    }

    OWLObjectPropertyAssertionAxiom updateAnnotation(ModelContainer model,
                                                     OWLObjectPropertyAssertionAxiom toModify, OWLAnnotation update,
                                                     METADATA metadata) {
        OWLObjectPropertyAssertionAxiom newAxiom = null;
        if (toModify != null) {
            Set<OWLAnnotation> combindedAnnotations = new HashSet<OWLAnnotation>();
            OWLAnnotationProperty target = update.getProperty();
            for (OWLAnnotation existing : toModify.getAnnotations()) {
                if (target.equals(existing.getProperty()) == false) {
                    combindedAnnotations.add(existing);
                }
            }
            combindedAnnotations.add(update);
            newAxiom = modifyAnnotations(toModify, combindedAnnotations, model, metadata);
        }
        return newAxiom;
    }

    public OWLObjectPropertyAssertionAxiom removeAnnotations(ModelContainer model, OWLObjectPropertyExpression p,
                                                             OWLNamedIndividual i, OWLNamedIndividual j, Set<OWLAnnotation> annotations, METADATA metadata) {
        OWLOntology ont = model.getAboxOntology();
        Set<OWLObjectPropertyAssertionAxiom> axioms = ont.getObjectPropertyAssertionAxioms(i);
        OWLObjectPropertyAssertionAxiom toModify = null;
        for (OWLObjectPropertyAssertionAxiom axiom : axioms) {
            if (p.equals(axiom.getProperty()) && j.equals(axiom.getObject())) {
                toModify = axiom;
                break;
            }
        }
        OWLObjectPropertyAssertionAxiom newAxiom = null;
        if (toModify != null) {
            Set<OWLAnnotation> combindedAnnotations = new HashSet<OWLAnnotation>(toModify.getAnnotations());
            combindedAnnotations.removeAll(annotations);
            newAxiom = modifyAnnotations(toModify, combindedAnnotations, model, metadata);
        }
        return newAxiom;
    }

    private OWLObjectPropertyAssertionAxiom modifyAnnotations(OWLObjectPropertyAssertionAxiom axiom,
                                                              Set<OWLAnnotation> replacement,
                                                              ModelContainer model, METADATA metadata) {
        OWLOntology ont = model.getAboxOntology();
        OWLDataFactory f = model.getOWLDataFactory();
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>(2);
        changes.add(new RemoveAxiom(ont, axiom));
        OWLObjectPropertyAssertionAxiom newAxiom =
                f.getOWLObjectPropertyAssertionAxiom(axiom.getProperty(), axiom.getSubject(), axiom.getObject(), replacement);
        changes.add(new AddAxiom(ont, newAxiom));
        applyChanges(model, changes, metadata);
        return newAxiom;
    }

    public void addAxiom(ModelContainer model, OWLAxiom axiom, METADATA metadata) {
        OWLOntology ont = model.getAboxOntology();
        List<OWLOntologyChange> changes = Collections.<OWLOntologyChange>singletonList(new AddAxiom(ont, axiom));
        synchronized (ont) {
            /*
             * all changes to the ontology are synchronized via the ontology object
             */
            applyChanges(model, ont.getOWLOntologyManager(), changes, metadata);
        }
    }

    void addAxioms(ModelContainer model, Set<? extends OWLAxiom> axioms, METADATA metadata) {
        OWLOntology ont = model.getAboxOntology();
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>(axioms.size());
        for (OWLAxiom axiom : axioms) {
            changes.add(new AddAxiom(ont, axiom));
        }
        synchronized (ont) {
            /*
             * all changes to the ontology are synchronized via the ontology object
             */
            applyChanges(model, ont.getOWLOntologyManager(), changes, metadata);
        }
    }

    void removeAxiom(ModelContainer model, OWLAxiom axiom, METADATA metadata) {
        OWLOntology ont = model.getAboxOntology();
        List<OWLOntologyChange> changes = Collections.<OWLOntologyChange>singletonList(new RemoveAxiom(ont, axiom));
        synchronized (ont) {
            /*
             * all changes to the ontology are synchronized via the ontology object
             */
            applyChanges(model, ont.getOWLOntologyManager(), changes, metadata);
        }
    }

    void removeAxioms(IRI modelId, Set<OWLAxiom> axioms, METADATA metadata) {
        ModelContainer model = getModel(modelId);
        removeAxioms(model, axioms, metadata);
    }

    void removeAxioms(ModelContainer model, Set<OWLAxiom> axioms, METADATA metadata) {
        OWLOntology ont = model.getAboxOntology();
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>(axioms.size());
        for (OWLAxiom axiom : axioms) {
            changes.add(new RemoveAxiom(ont, axiom));
        }
        synchronized (ont) {
            /*
             * all changes to the ontology are synchronized via the ontology object
             */
            applyChanges(model, ont.getOWLOntologyManager(), changes, metadata);
        }
    }

    protected void applyChanges(ModelContainer model, List<OWLOntologyChange> changes, METADATA metadata) {
        OWLOntology ont = model.getAboxOntology();
        synchronized (ont) {
            /*
             * all changes to the ontology are synchronized via the ontology object
             */
            applyChanges(model, ont.getOWLOntologyManager(), changes, metadata);
        }
    }

    private void applyChanges(ModelContainer model, OWLOntologyManager m,
                              List<? extends OWLOntologyChange> changes, METADATA metadata) {
        List<OWLOntologyChange> appliedChanges = model.applyChanges(changes);
        addToHistory(model, appliedChanges, metadata);
    }

    /**
     * Hook for implementing an undo and redo.
     *
     * @param model
     * @param appliedChanges
     * @param metadata
     */
    protected void addToHistory(ModelContainer model,
                                List<OWLOntologyChange> appliedChanges, METADATA metadata) {
        // do nothing, for now
    }

    protected OWLOntology loadOntologyDocumentSource(final OWLOntologyDocumentSource source, boolean minimal) throws OWLOntologyCreationException {
        return loadOntologyDocumentSource(source, minimal, tbox.getOWLOntologyManager());
    }

    public static OWLOntology loadOntologyDocumentSource(final OWLOntologyDocumentSource source, boolean minimal, OWLOntologyManager manager) throws OWLOntologyCreationException {
        // silence the OBO parser in the OWL-API
        java.util.logging.Logger.getLogger("org.obolibrary").setLevel(java.util.logging.Level.SEVERE);
        final Set<OWLParserFactory> originalFactories = removeOBOParserFactories(manager);
        try {
            // load model from source
            if (minimal == false) {
                //this gets the model to load all the OWL properly because it is using the tbox manager
                //otherwise it doesn't understand the object properties.
                OWLOntology abox_tbox_manager = loadOWLOntologyDocumentSource(source, manager);
                //unfortunately it bizarrely does not retrieve the http://purl.org/dc/elements/1.1/title annotation
                return abox_tbox_manager;
            } else {
                // only load the model, skip imports
                // approach: return an empty ontology IRI for any IRI mapping request using.
                final OWLOntologyManager m = OWLManager.createOWLOntologyManager();
                final Set<IRI> emptyOntologies = new HashSet<IRI>();
                m.getIRIMappers().add(new OWLOntologyIRIMapper() {

                    // generated
                    private static final long serialVersionUID = -8200679663396870351L;

                    @Override
                    public IRI getDocumentIRI(IRI ontologyIRI) {

                        // quick check:
                        // do nothing for the original IRI and known empty ontologies
                        if (source.getDocumentIRI().equals(ontologyIRI) || emptyOntologies.contains(ontologyIRI)) {
                            return null;
                        }
                        emptyOntologies.add(ontologyIRI);
                        try {
                            OWLOntology emptyOntology = m.createOntology(ontologyIRI);
                            return emptyOntology.getOntologyID().getDefaultDocumentIRI().orNull();
                        } catch (OWLOntologyCreationException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                OWLOntology minimalAbox = loadOWLOntologyDocumentSource(source, m);
                return minimalAbox;
            }
        } finally {
            resetOBOParserFactories(manager, originalFactories);
        }
    }

    private static OWLOntology loadOWLOntologyDocumentSource(final OWLOntologyDocumentSource source, final OWLOntologyManager manager) throws OWLOntologyCreationException {
        final OWLOntology ontology;
        if (source instanceof RioMemoryTripleSource) {
            RioParserImpl parser = new RioParserImpl(new RioRDFXMLDocumentFormatFactory());
            ontology = manager.createOntology();
            OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();
            try {
                parser.parse(source, ontology, config);
            } catch (IOException e) {
                throw new OWLOntologyCreationException(e);
            }
        } else {
            ontology = manager.loadOntologyFromOntologyDocument(source);
        }
        return ontology;
    }

    public OWLOntology getTbox() {
        return tbox;
    }

    public BlazegraphOntologyManager getGolego_repo() {
        return go_lego_repo;
    }


    /**
     * even if the manager has loaded a property before, and should know what kind it is,
     * if the next ontology doesn't include an import statement or otherwise declare the properties used, the loader will guess that
     * object properties are annotation properties and screw up.
     * This purifies the gocam
     *
     * @param ont
     * @return
     * @throws OWLOntologyCreationException
     */
    public static OWLOntology fixBrokenObjectPropertiesAndAxioms(OWLOntology ont) throws OWLOntologyCreationException {
        OWLOntologyManager newman = OWLManager.createOWLOntologyManager();
        OWLOntology frank = newman.createOntology(ont.getOntologyID());
        OWLDataFactory df = newman.getOWLDataFactory();

        //declare known annotation properties
        OWLAnnotationProperty title_prop = df.getOWLAnnotationProperty(IRI.create("http://purl.org/dc/elements/1.1/title"));
        OWLDeclarationAxiom title_prop_declaration = df.getOWLDeclarationAxiom(title_prop);
        newman.addAxiom(frank, title_prop_declaration);
        OWLAnnotationProperty title_prop2 = df.getOWLAnnotationProperty(IRI.create("http://purl.org/dc/terms/title"));
        OWLDeclarationAxiom title_prop2_declaration = df.getOWLDeclarationAxiom(title_prop2);
        newman.addAxiom(frank, title_prop2_declaration);
        OWLAnnotationProperty skos_note = df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#note"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(skos_note));
        OWLAnnotationProperty version_info = df.getOWLAnnotationProperty(IRI.create(OWL.versionInfo.getURI()));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(version_info));
        OWLAnnotationProperty contributor_prop = df.getOWLAnnotationProperty(IRI.create("http://purl.org/dc/elements/1.1/contributor"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(contributor_prop));
        OWLAnnotationProperty date_prop = df.getOWLAnnotationProperty(IRI.create("http://purl.org/dc/elements/1.1/date"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(date_prop));
        OWLAnnotationProperty source_prop = df.getOWLAnnotationProperty(IRI.create("http://purl.org/dc/elements/1.1/source"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(source_prop));
        OWLAnnotationProperty state_prop = df.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/modelstate"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(state_prop));
        OWLAnnotationProperty evidence_prop = df.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/evidence"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(evidence_prop));
        OWLAnnotationProperty provided_by_prop = df.getOWLAnnotationProperty(IRI.create("http://purl.org/pav/providedBy"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(provided_by_prop));
        OWLAnnotationProperty x_prop = df.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/hint/layout/x"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(x_prop));
        OWLAnnotationProperty y_prop = df.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/hint/layout/y"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(y_prop));
        OWLAnnotationProperty rdfs_label = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(rdfs_label));
        OWLAnnotationProperty rdfs_comment = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI());
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(rdfs_comment));
        OWLAnnotationProperty rdfs_seealso = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_SEE_ALSO.getIRI());
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(rdfs_seealso));
        OWLAnnotationProperty skos_exact_match = df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#exactMatch"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(skos_exact_match));
        OWLAnnotationProperty skos_altlabel = df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#altLabel"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(skos_altlabel));
        OWLAnnotationProperty definition = df.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0000115"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(definition));
        OWLAnnotationProperty database_cross_reference = df.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasDbXref"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(database_cross_reference));
        OWLAnnotationProperty canonical_record = df.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/canonical_record"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(canonical_record));
        OWLAnnotationProperty iuphar_id = df.getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/iuphar_id"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(iuphar_id));
        OWLAnnotationProperty in_taxon = df.getOWLAnnotationProperty(IRI.create("https://w3id.org/biolink/vocab/in_taxon"));
        newman.addAxiom(frank, df.getOWLDeclarationAxiom(in_taxon));

        //copy over ontology annotations
        for (OWLAnnotation anno : ont.getAnnotations()) {
            AddOntologyAnnotation add = new AddOntologyAnnotation(frank, anno);
            newman.applyChange(add);
        }

        //add correct property declarations
        Set<OWLAnnotationProperty> anno_properties = ont.getAnnotationPropertiesInSignature();
        Set<String> bad_props = new HashSet<String>();
        for (OWLAnnotationProperty anno_prop : anno_properties) {
            if (anno_prop.getIRI().toString().contains("http://purl.obolibrary.org/obo/RO_") ||
                    anno_prop.getIRI().toString().contains("http://purl.obolibrary.org/obo/BFO_")) {
                bad_props.add(anno_prop.getIRI().toString());
                OWLObjectProperty object_prop = df.getOWLObjectProperty(anno_prop.getIRI());
                OWLDeclarationAxiom object_prop_declaration = df.getOWLDeclarationAxiom(object_prop);
                newman.addAxiom(frank, object_prop_declaration);
            }
        }
        //fix screwed up axioms, collect the rest
        for (OWLAxiom axiom : ont.getAxioms()) {
            if (axiom.isOfType(AxiomType.ANNOTATION_ASSERTION)) {
                OWLAnnotationAssertionAxiom a = (OWLAnnotationAssertionAxiom) axiom;
                String prop_iri = a.getProperty().getIRI().toString();
                if (bad_props.contains(prop_iri)) {
                    Set<OWLAnnotation> annos = a.getAnnotations();
                    OWLObjectProperty p = df.getOWLObjectProperty(IRI.create(prop_iri));
                    IRI object = a.getValue().asIRI().get();
                    IRI subject = IRI.create(a.getSubject().toString());
                    OWLObjectPropertyAssertionAxiom new_ass = df.getOWLObjectPropertyAssertionAxiom(p, df.getOWLNamedIndividual(subject), df.getOWLNamedIndividual(object), annos);
                    newman.addAxiom(frank, new_ass);
                } else {
                    newman.addAxiom(frank, axiom);
                }
            } else {
                newman.addAxiom(frank, axiom);
            }
        }
        //return new fixed ontology
        return frank;
    }

    public static OWLOntology removeDeadAnnotationsAndImports(OWLOntology ont) throws OWLOntologyCreationException {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLAnnotationProperty json_model_prop = m.getOWLDataFactory().getOWLAnnotationProperty(IRI.create("http://geneontology.org/lego/json-model"));
        //get rid of all imports
        Set<OWLImportsDeclaration> imports = ont.getImportsDeclarations();
        for (OWLImportsDeclaration import_declaration : imports) {
            m.applyChange(new RemoveImport(ont, import_declaration));
        }
        //get rid of the json annotations lurking about
        for (OWLAnnotation anno : ont.getAnnotations()) {
            if (anno.getProperty().equals(json_model_prop)) {
                RemoveOntologyAnnotation rm = new RemoveOntologyAnnotation(ont, anno);
                m.applyChange(rm);
            }
        }
        //purify of the json annotation property as well
        OWLDeclarationAxiom json_prop_declaration = m.getOWLDataFactory().getOWLDeclarationAxiom(json_model_prop);
        m.removeAxiom(ont, json_prop_declaration);
        return ont;
    }
}