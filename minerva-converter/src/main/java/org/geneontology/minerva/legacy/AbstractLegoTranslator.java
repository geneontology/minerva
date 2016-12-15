package org.geneontology.minerva.legacy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.evidence.FindGoCodes;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.geneontology.minerva.taxon.FindTaxonTool;
import org.obolibrary.obo2owl.Obo2Owl;
import org.obolibrary.obo2owl.Owl2Obo;
import org.obolibrary.oboformat.parser.OBOFormatConstants.OboFormatTag;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValueVisitorEx;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import owltools.gaf.Bioentity;
import owltools.gaf.ExtensionExpression;
import owltools.gaf.GafDocument;
import owltools.gaf.GeneAnnotation;
import owltools.gaf.eco.SimpleEcoMapper;
import owltools.graph.OWLGraphWrapper;
import owltools.util.OwlHelper;
import owltools.vocab.OBOUpperVocabulary;

abstract class AbstractLegoTranslator extends LegoModelWalker<AbstractLegoTranslator.Summary> {

    protected final OWLClass mf;
    protected final Set<OWLClass> mfSet;

    protected final OWLClass cc;
    protected final Set<OWLClass> ccSet;

    protected final OWLClass bp;
    protected final Set<OWLClass> bpSet;

    protected final FindGoCodes goCodes;
    protected final CurieHandler curieHandler;

    protected String assignedByDefault;

    protected AbstractLegoTranslator(OWLOntology model, CurieHandler curieHandler, SimpleEcoMapper mapper) {
        super(model.getOWLOntologyManager().getOWLDataFactory());
        this.curieHandler = curieHandler;
        goCodes = new FindGoCodes(mapper, curieHandler);

        mf = OBOUpperVocabulary.GO_molecular_function.getOWLClass(f);
        cc = f.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0005575"));
        bp = OBOUpperVocabulary.GO_biological_process.getOWLClass(f);
    
        bpSet = new HashSet<>();
        mfSet = new HashSet<>();
        ccSet = new HashSet<>();
    
        ElkReasonerFactory rf = new ElkReasonerFactory();
        OWLReasoner reasoner = rf.createReasoner(model);
        fillAspects(model, reasoner, curieHandler, bpSet, mfSet, ccSet, bp, mf, cc);
        reasoner.dispose();

        assignedByDefault = "GO_Noctua";
    }

    static void fillAspects(OWLOntology model, OWLReasoner reasoner, CurieHandler curieHandler, Set<OWLClass> bpSet, Set<OWLClass> mfSet, Set<OWLClass> ccSet, 
            OWLClass bp, OWLClass mf, OWLClass cc) {
        final IRI namespaceIRI = Obo2Owl.trTagToIRI(OboFormatTag.TAG_NAMESPACE.getTag());
        final OWLDataFactory df = model.getOWLOntologyManager().getOWLDataFactory();
        final OWLAnnotationProperty namespaceProperty = df.getOWLAnnotationProperty(namespaceIRI);
        final Set<OWLOntology> ontologies = model.getImportsClosure();
        for(OWLClass cls : model.getClassesInSignature(Imports.INCLUDED)) {
            if (cls.isBuiltIn()) {
                continue;
            }
            String id = curieHandler.getCuri(cls);
            if (id.startsWith("GO:") == false) {
                continue;
            }
            
            // if a reasoner object is passed, use inference to populate
            // the 3 subset
            if (reasoner != null) {
                if (reasoner.getSuperClasses(cls, false).containsEntity(mf) ||
                        reasoner.getEquivalentClasses(cls).contains(mf)) {
                    mfSet.add(cls);
                }
                if (reasoner.getSuperClasses(cls, false).containsEntity(bp) ||
                        reasoner.getEquivalentClasses(cls).contains(bp)) {
                    bpSet.add(cls);
                }
                if (reasoner.getSuperClasses(cls, false).containsEntity(cc) ||
                        reasoner.getEquivalentClasses(cls).contains(cc)) {
                    ccSet.add(cls);
                }
            }

            
            for (OWLAnnotation annotation : OwlHelper.getAnnotations(cls, ontologies)) {
                if (annotation.getProperty().equals(namespaceProperty)) {
                    String value = annotation.getValue().accept(new OWLAnnotationValueVisitorEx<String>() {

                        @Override
                        public String visit(IRI iri) {
                            return null;
                        }

                        @Override
                        public String visit(OWLAnonymousIndividual individual) {
                            return null;
                        }

                        @Override
                        public String visit(OWLLiteral literal) {
                            return literal.getLiteral();
                        }
                    });
                    if (value != null) {
                        if ("molecular_function".equals(value)) {
                            mfSet.add(cls);
                        }
                        else if ("biological_process".equals(value)) {
                            bpSet.add(cls);
                        }
                        else if ("cellular_component".equals(value)) {
                            ccSet.add(cls);
                        }
                    }
                }
            }
        }
    }
    
    protected static Set<OWLClass> getAllSubClasses(OWLClass cls, OWLReasoner r, boolean reflexive, String idSpace, CurieHandler curieHandler) {
        Set<OWLClass> allSubClasses = r.getSubClasses(cls, false).getFlattened();
        Iterator<OWLClass> it = allSubClasses.iterator();
        while (it.hasNext()) {
            OWLClass current = it.next();
            if (current.isBuiltIn()) {
                it.remove();
                continue;
            }
            String id = curieHandler.getCuri(current);
            if (id.startsWith(idSpace) == false) {
                it.remove();
                continue;
            }
        }
        if (reflexive) {
            allSubClasses.add(cls);
        }
        return allSubClasses;
    }

    protected class Summary {

        Set<Entry<OWLClass>> activities = null;
        Set<Entry<OWLClass>> locations = null;
        Set<Entry<OWLClass>> processes = null;
        OWLClass entity = null;
        String entityType = null;
        String entityTaxon = null;

        boolean addMf(OWLClass cls, Metadata metadata, Set<Evidence> evidences, Set<OWLObjectSomeValuesFrom> expressions) {
            if (isMf(cls)) {
                activities = addAnnotation(cls, metadata, evidences, expressions, activities);
                return true;
            }
            return false;
        }

        boolean addBp(OWLClass cls, Metadata metadata, Set<Evidence> evidences, Set<OWLObjectSomeValuesFrom> expressions) {
            if (isBp(cls)) {
                processes = addAnnotation(cls, metadata, evidences, expressions, processes);
                return true;
            }
            return false;
        }

        boolean addCc(OWLClass cls, Metadata metadata, Set<Evidence> evidences, Set<OWLObjectSomeValuesFrom> expressions) {
            if (isCc(cls)) {
                locations = addAnnotation(cls, metadata, evidences, expressions, locations);
                return true;
            }
            return false;
        }

        private <T> Set<Entry<T>> addAnnotation(T cls, Metadata metadata, Set<Evidence> evidences, Set<OWLObjectSomeValuesFrom> expressions, Set<Entry<T>> set) {
            if (set == null) {
                set = new HashSet<Entry<T>>();
            }
            Entry<T> entry = new Entry<T>();
            entry.value = cls;
            entry.metadata = metadata;
            entry.evidences = new ArrayList<>(evidences);
            if (expressions != null) {
                entry.expressions = expressions;
            }
            set.add(entry);
            return set;
        }

        void addProcesses(Set<Entry<OWLClass>> processes, Metadata metadata) {
            if (processes != null) {
                if (this.processes == null) {
                    this.processes = new HashSet<Entry<OWLClass>>(processes);
                }
                else {
                    this.processes.addAll(processes);
                }
            }
        }

        void addLocations(Set<Entry<OWLClass>> locations) {
            if (locations != null) {
                if (this.locations == null) {
                    this.locations = new HashSet<Entry<OWLClass>>(locations);
                }
                else {
                    this.locations.addAll(locations);
                }
            }
        }
    }

    protected boolean isMf(OWLClass cls) {
        return mfSet.contains(cls);
    }

    protected boolean isBp(OWLClass cls) {
        return bpSet.contains(cls);
    }

    protected boolean isCc(OWLClass cls) {
        return ccSet.contains(cls);
    }

    @Override
    protected String getShortHand(IRI iri) {
        return curieHandler.getCuri(iri);
    }

    @Override
    protected boolean isAnnotationIndividual(OWLNamedIndividual i, Set<OWLClass> types) {
        boolean isGoAnnotation = false;
        Iterator<OWLClass> typeIterator = types.iterator();
        while (typeIterator.hasNext() && isGoAnnotation == false) {
            OWLClass cls = typeIterator.next();
            isGoAnnotation = mfSet.contains(cls) || bpSet.contains(cls) || ccSet.contains(cls);
        }
        return isGoAnnotation;
    }

    public abstract void translate(OWLOntology modelAbox, ExternalLookupService lookup, GafDocument annotations, List<String> additionalRefs) throws UnknownIdentifierException;

    /**
     * Get the type of an enabled by entity, e.g. gene, protein
     * 
     * @param modelGraph 
     * @param entity 
     * @param individual
     * @param lookup
     * @return type
     */
    protected String getEntityType(OWLClass entity, OWLNamedIndividual individual, OWLGraphWrapper modelGraph, ExternalLookupService lookup) {
        if (lookup != null) {
            List<LookupEntry> result = lookup.lookup(entity.getIRI());
            if ((result != null) && (result.isEmpty() == false)) {
                LookupEntry entry = result.get(0);
                if ("protein".equalsIgnoreCase(entry.type)) {
                    return "protein";
                }
                else if ("gene".equalsIgnoreCase(entry.type)) {
                    return "gene";
                }
            }
        }
        return "gene";
    }

    protected String getEntityTaxon(OWLClass entity, OWLOntology model) throws UnknownIdentifierException {
        if (entity == null) {
            return null;
        }
        FindTaxonTool tool = new FindTaxonTool(curieHandler, model.getOWLOntologyManager().getOWLDataFactory());
        return tool.getEntityTaxon(curieHandler.getCuri(entity), model);
    }

    public GafDocument translate(String id, OWLOntology modelAbox, ExternalLookupService lookup, List<String> additionalReferences) throws UnknownIdentifierException {
        final GafDocument annotations = new GafDocument(id, null);
        translate(modelAbox, lookup, annotations, additionalReferences);
        return annotations;
    }

    protected List<GeneAnnotation> createAnnotations(Entry<OWLClass> entry, Bioentity entity, String aspect,
            List<String> additionalReferences,
            OWLGraphWrapper g, Collection<OWLObjectSomeValuesFrom> c16) {
        List<GeneAnnotation> result = new ArrayList<>();
        if (entry.evidences != null) {
            for(Evidence evidence : entry.evidences) {
                GeneAnnotation ann = createAnnotation(entry.value, entry.metadata, evidence, entity, aspect, additionalReferences, g, c16);
                result.add(ann);
            }
        }
        return result;
    }
    
    protected GeneAnnotation createAnnotation(OWLClass cls, Metadata meta, Evidence evidence, Bioentity entity, String aspect,
            List<String> additionalReferences,
            OWLGraphWrapper g, Collection<OWLObjectSomeValuesFrom> c16) {
        GeneAnnotation annotation = new GeneAnnotation();
        annotation.setBioentityObject(entity);
        annotation.setBioentity(entity.getId());
        annotation.setAspect(aspect);
        
        String assignedBy = assignedByDefault;
        if (meta.groups != null) {
            if (meta.groups.size() == 1) {
                assignedBy = meta.groups.iterator().next();
            }
            for(String group : meta.groups) {
                annotation.addProperty("group", group);
            }
        }
        annotation.setAssignedBy(assignedBy);
        
        annotation.setCls(curieHandler.getCuri(cls));
        
        if (meta.modelId != null) {
            annotation.addProperty("lego-model-id", meta.modelId);
        }
        if (meta.contributors != null) {
            for(String contributor : meta.contributors) {
                annotation.addProperty("contributor", contributor);
            }
        }
        if (meta.individualIds != null) {
            for(IRI individual : meta.individualIds) {
                annotation.addProperty("individual", individual.toString());
            }
        }

        if (evidence != null) {
            String ecoId = curieHandler.getCuri(evidence.evidenceCls);
            if (ecoId != null) {
                String goCode = null;
                Pair<String, String> pair = goCodes.findShortEvidence(evidence.evidenceCls, ecoId, g.getSourceOntology());
                if (pair != null) {
                    goCode = pair.getLeft();
                    String goRef = pair.getRight();
                    if (goRef != null) {
                        if (additionalReferences == null) {
                            additionalReferences = Collections.singletonList(goRef);
                        }
                        else {
                            additionalReferences = new ArrayList<String>(additionalReferences);
                            additionalReferences.add(goRef);
                        }
                    }
                }
                annotation.setEvidence(goCode, ecoId);
            }
        }
        if (meta.date != null) {
            // assumes that the date is YYYY-MM-DD
            // gene annotations require YYYYMMDD
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < meta.date.length(); i++) {
                char c = meta.date.charAt(i);
                if (Character.isDigit(c)) {
                    sb.append(c);
                }
            }
            annotation.setLastUpdateDate(sb.toString());
        }
        
        if (evidence.with != null) {
            List<String> withInfos = Collections.singletonList(evidence.with);
            annotation.setWithInfos(withInfos);
        }

        String relation = "enables";
        if ("C".equals(aspect)) {
            relation = "part_of";
        }
        else if ("P".equals(aspect)) {
            relation = "involved_in";
        }
        annotation.setRelation(relation);
        if (evidence.source != null) {
            annotation.addReferenceId(evidence.source);
        }
        if (additionalReferences != null) {
            for (String ref : additionalReferences) {
                annotation.addReferenceId(ref);
            }
        }

        if (c16 != null && !c16.isEmpty()) {
            List<ExtensionExpression> expressions = new ArrayList<ExtensionExpression>();
            for (OWLObjectSomeValuesFrom svf : c16) {
                OWLObjectPropertyExpression property = svf.getProperty();
                OWLClassExpression filler = svf.getFiller();
                if (property instanceof OWLObjectProperty && filler instanceof OWLClass) {
                    String rel = getRelId(property, g);
                    String objectId = curieHandler.getCuri((OWLClass) filler);
                    ExtensionExpression expr = new ExtensionExpression(rel, objectId);
                    expressions.add(expr);
                }
            }
            annotation.setExtensionExpressions(Collections.singletonList(expressions));
        }
        
        return annotation;
    }

    protected String getRelId(OWLObjectPropertyExpression p, OWLGraphWrapper graph) {
        String relId = null;
        for(OWLOntology ont : graph.getAllOntologies()) {
            relId = Owl2Obo.getIdentifierFromObject(p, ont, null);
            if (relId != null && relId.indexOf(':') < 0) {
                return relId;
            }
        }
        return relId;
    }

    protected Bioentity createBioentity(OWLClass entityCls, String entityType, String taxon, OWLGraphWrapper g, ExternalLookupService lookup) {
        Bioentity bioentity = new Bioentity();
        BioentityStrings strings = getBioentityStrings(entityCls, entityType, taxon, g, lookup);
        String id = strings.id;
        bioentity.setId(id);
        if (strings.db != null) {
            bioentity.setDb(strings.db);
        }
        bioentity.setSymbol(strings.symbol);
        bioentity.setTypeCls(strings.type);
        if (taxon != null) {
            bioentity.setNcbiTaxonId(taxon);    
        }
        return bioentity;
    }

    protected static class BioentityStrings {
        String id;
        String db;
        String symbol;
        String type;
    }

    protected BioentityStrings getBioentityStrings(OWLClass entityCls, String entityType, String taxon, OWLGraphWrapper g, ExternalLookupService lookup) {
        BioentityStrings strings = new BioentityStrings();
        strings.id = curieHandler.getCuri(entityCls);
        strings.db = null;
        String[] split = StringUtils.split(strings.id, ":", 2);
        if (split.length == 2) {
            strings.db = split[0];
        }
        strings.symbol = getLabelForBioentity(entityCls, entityType, taxon, g, lookup);
        strings.type = entityType;
        return strings;
    }

    private String getLabelForBioentity(OWLClass entityCls, String entityType, String taxon, OWLGraphWrapper g, ExternalLookupService lookup) {
        String lbl = g.getLabel(entityCls);
        if (lbl == null && lookup != null) {
            List<LookupEntry> result = lookup.lookup(entityCls.getIRI());
            if (!result.isEmpty()) {
                LookupEntry entry = result.get(0);
                lbl = entry.label;
            }
        }
        return lbl;
    }

    protected void addAnnotations(OWLGraphWrapper modelGraph, ExternalLookupService lookup,
            Summary summary, List<String> additionalRefs,
            GafDocument annotations) 
    {
        Bioentity entity = createBioentity(summary.entity, summary.entityType, summary.entityTaxon , modelGraph, lookup);
        entity = annotations.addBioentity(entity);

        if (summary.activities != null) {
            for (Entry<OWLClass> e: summary.activities) {
                if (isMf(e.value) && !mf.equals(e.value)) {
                    List<GeneAnnotation> current = createAnnotations(e, entity, "F", additionalRefs, modelGraph, e.expressions);
                    for (GeneAnnotation annotation : current) {
                        annotations.addGeneAnnotation(annotation);
                    }
                }
            }
        }
        if (summary.processes != null) {
            for (Entry<OWLClass> e : summary.processes) {
                if (isBp(e.value) && !bp.equals(e.value)) {
                    List<GeneAnnotation> current = createAnnotations(e, entity, "P", additionalRefs, modelGraph, e.expressions);
                    for (GeneAnnotation annotation : current) {
                        annotations.addGeneAnnotation(annotation);
                    }
                }
            }
        }
        if (summary.locations != null) {
            for (Entry<OWLClass> e : summary.locations) {
                if (isCc(e.value) && !cc.equals(e.value)) {
                    List<GeneAnnotation> current = createAnnotations(e, entity, "C", additionalRefs, modelGraph, e.expressions);
                    for (GeneAnnotation annotation : current) {
                        annotations.addGeneAnnotation(annotation);
                    }
                }
            }
        }
    }
}
