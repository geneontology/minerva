package org.geneontology.minerva.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.geneontology.minerva.BlazegraphOntologyManager;
import org.geneontology.minerva.MinervaOWLGraphWrapper;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.MolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;
import owltools.gaf.eco.EcoMapper;
import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.EcoMapperFactory.OntologyMapperPair;
import owltools.util.OwlHelper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A Renderer that takes a MolecularModel (an OWL ABox) and generates Map objects
 * that can be translated to JSON using Gson.
 *
 * @author cjm
 */
public class MolecularModelJsonRenderer {

    private static Logger LOG = Logger.getLogger(MolecularModelJsonRenderer.class);

    private final String modelId;
    private final OWLOntology ont;
    //TODO get rid of this graph entity
    private MinervaOWLGraphWrapper graph;
    private final CurieHandler curieHandler;
    private final InferenceProvider inferenceProvider;
    private BlazegraphOntologyManager go_lego_repo;
    private Map<OWLNamedIndividual, Set<String>> type_roots;
    private Map<String, String> class_label;

    public static final ThreadLocal<DateFormat> AnnotationTypeDateFormat = new ThreadLocal<DateFormat>() {

        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd");
        }

    };

    public MolecularModelJsonRenderer(ModelContainer model, InferenceProvider inferenceProvider, CurieHandler curieHandler) {
        this(curieHandler.getCuri(model.getModelId()),
                model.getAboxOntology(),
                new MinervaOWLGraphWrapper(model.getAboxOntology()),
                inferenceProvider, curieHandler);
    }

    public MolecularModelJsonRenderer(String modelId, OWLOntology ontology, InferenceProvider inferenceProvider, CurieHandler curieHandler) {
        this(modelId, ontology, new MinervaOWLGraphWrapper(ontology), inferenceProvider, curieHandler);
    }

    public MolecularModelJsonRenderer(String modelId, MinervaOWLGraphWrapper graph, InferenceProvider inferenceProvider, CurieHandler curieHandler) {
        this(modelId, graph.getSourceOntology(), graph, inferenceProvider, curieHandler);
    }

    private MolecularModelJsonRenderer(String modelId, OWLOntology ont, MinervaOWLGraphWrapper graph, InferenceProvider inferenceProvider, CurieHandler curieHandler) {
        super();
        this.modelId = modelId;
        this.ont = ont;
        this.graph = graph;
        this.inferenceProvider = inferenceProvider;
        this.curieHandler = curieHandler;
    }

    public MolecularModelJsonRenderer(String modelId, OWLOntology ont, BlazegraphOntologyManager go_lego_repo,
                                      InferenceProvider inferenceProvider, CurieHandler curieHandler) {
        super();
        this.modelId = modelId;
        this.ont = ont;
        this.go_lego_repo = go_lego_repo;
        this.inferenceProvider = inferenceProvider;
        this.curieHandler = curieHandler;
    }

    /**
     * @return Map to be passed to Gson
     */
    public JsonModel renderModel() {
        JsonModel json = new JsonModel();
        json.modelId = modelId;
        // per-Individual
        //TODO this loop is the slowest part of the service response time.
        List<JsonOwlIndividual> iObjs = new ArrayList<JsonOwlIndividual>();
        Set<OWLNamedIndividual> individuals = ont.getIndividualsInSignature();

        if (go_lego_repo != null) {
            try {
                type_roots = go_lego_repo.getSuperCategoryMapForIndividuals(individuals, ont);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            //get all the labels ready for the ontology terms in the model
            Set<String> all_classes = new HashSet<String>();
            for (OWLNamedIndividual ind : individuals) {
                Collection<OWLClassExpression> ocs = EntitySearcher.getTypes(ind, ont);
                if (ocs != null) {
                    for (OWLClassExpression oc : ocs) {
                        if (!oc.isAnonymous()) {
                            all_classes.add(oc.asOWLClass().getIRI().toString());
                        }
                    }
                }
            }
            //also the root terms
            if (type_roots != null && type_roots.values() != null) {
                for (Set<String> roots : type_roots.values()) {
                    if (roots != null) {
                        all_classes.addAll(roots);
                    }
                }
            }
            if (all_classes != null) {
                try {
                    class_label = go_lego_repo.getLabels(all_classes);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        for (OWLNamedIndividual i : individuals) {
            iObjs.add(renderObject(i));
        }
        json.individuals = iObjs.toArray(new JsonOwlIndividual[iObjs.size()]);
        // per-Assertion
        Set<OWLObjectProperty> usedProps = new HashSet<OWLObjectProperty>();
        List<JsonOwlFact> aObjs = new ArrayList<JsonOwlFact>();
        for (OWLObjectPropertyAssertionAxiom opa : ont.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
            JsonOwlFact fact = renderObject(opa);
            if (fact != null) {
                aObjs.add(fact);
                usedProps.addAll(opa.getObjectPropertiesInSignature());
            }
        }
        json.facts = aObjs.toArray(new JsonOwlFact[aObjs.size()]);
        JsonAnnotation[] anObjs = renderAnnotations(ont.getAnnotations(), curieHandler);
        if (anObjs != null && anObjs.length > 0) {
            json.annotations = anObjs;
        }
        return json;

    }

    public static JsonAnnotation[] renderModelAnnotations(OWLOntology ont, CurieHandler curieHandler) {
        JsonAnnotation[] anObjs = renderAnnotations(ont.getAnnotations(), curieHandler);
        return anObjs;
    }

    private static JsonAnnotation[] renderAnnotations(Set<OWLAnnotation> annotations, CurieHandler curieHandler) {
        List<JsonAnnotation> anObjs = new ArrayList<JsonAnnotation>();
        for (OWLAnnotation annotation : annotations) {
            JsonAnnotation json = JsonTools.create(annotation.getProperty(), annotation.getValue(), null, curieHandler);
            if (json != null) {
                anObjs.add(json);
            }
        }
        return anObjs.toArray(new JsonAnnotation[anObjs.size()]);
    }

    public Pair<JsonOwlIndividual[], JsonOwlFact[]> renderIndividuals(Collection<OWLNamedIndividual> individuals) {

        //add root types in case these are new to the model
        if (go_lego_repo != null) {
            try {
                if (type_roots == null) {
                    type_roots = new HashMap<OWLNamedIndividual, Set<String>>();
                }
                Map<OWLNamedIndividual, Set<String>> t_r = go_lego_repo.getSuperCategoryMapForIndividuals(new HashSet<OWLNamedIndividual>(individuals), ont);
                if (t_r != null) {
                    type_roots.putAll(t_r);
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        List<JsonOwlIndividual> iObjs = new ArrayList<JsonOwlIndividual>();
        Set<OWLNamedIndividual> individualIds = new HashSet<OWLNamedIndividual>();
        final Set<OWLObjectPropertyAssertionAxiom> opAxioms = new HashSet<OWLObjectPropertyAssertionAxiom>();
        for (OWLIndividual i : individuals) {
            if (i instanceof OWLNamedIndividual) {
                OWLNamedIndividual named = (OWLNamedIndividual) i;
                iObjs.add(renderObject(named));
                individualIds.add(named);
            }
        }

        // filter object property axioms. Only retain axioms which use individuals from the given subset
        for (OWLNamedIndividual i : individualIds) {
            Set<OWLObjectPropertyAssertionAxiom> axioms = ont.getObjectPropertyAssertionAxioms(i);
            for (OWLObjectPropertyAssertionAxiom opa : axioms) {
                OWLIndividual object = opa.getObject();
                if (individualIds.contains(object)) {
                    opAxioms.add(opa);
                }
            }
        }
        List<JsonOwlFact> aObjs = new ArrayList<JsonOwlFact>();
        for (OWLObjectPropertyAssertionAxiom opa : opAxioms) {
            JsonOwlFact fact = renderObject(opa);
            if (fact != null) {
                aObjs.add(fact);
            }
        }

        return Pair.of(iObjs.toArray(new JsonOwlIndividual[iObjs.size()]),
                aObjs.toArray(new JsonOwlFact[aObjs.size()]));
    }

    /**
     * //TODO this is slow, speed it up.  The slowest part of the service, including reasoning and validation.
     *
     * @param i
     * @return Map to be passed to Gson
     */
    public JsonOwlIndividual renderObject(OWLNamedIndividual i) {
        JsonOwlIndividual json = new JsonOwlIndividual();
        json.id = curieHandler.getCuri(i);
        List<JsonOwlObject> typeObjs = new ArrayList<JsonOwlObject>();
        Set<OWLClassExpression> assertedTypes = OwlHelper.getTypes(i, ont);
        for (OWLClassExpression x : assertedTypes) {
            typeObjs.add(renderObject(x));
        }
        json.type = typeObjs.toArray(new JsonOwlObject[typeObjs.size()]);

        //if we have it, add the root type for the individual
        List<JsonOwlObject> rootTypes = new ArrayList<JsonOwlObject>();
        if (type_roots != null && (type_roots.get(i) != null)) {
            for (String root_type : type_roots.get(i)) {
                OWLClass root_class = ont.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(root_type));
                //this takes a lot of time...
                rootTypes.add(renderObject(root_class));
            }
        }
        json.rootType = rootTypes.toArray(new JsonOwlObject[rootTypes.size()]);

        //add direct inferred type information
        if (inferenceProvider != null && inferenceProvider.isConsistent()) {
            List<JsonOwlObject> inferredTypeObjs = new ArrayList<JsonOwlObject>();
            Set<OWLClass> inferredTypes = inferenceProvider.getTypes(i);
            // optimization, do not render inferences, if they are equal to the asserted ones
            if (assertedTypes.equals(inferredTypes) == false) {
                for (OWLClass c : inferredTypes) {
                    if (c.isBuiltIn() == false) {
                        inferredTypeObjs.add(renderObject(c));
                    }
                }
            }
            if (inferredTypeObjs.isEmpty() == false) {
                json.inferredType = inferredTypeObjs.toArray(new JsonOwlObject[inferredTypeObjs.size()]);
            }
            //testing approach to adding additional type information to response
            //this works but ends up going extremely slowly when a lot of inferences are happening
            //since its not being consumed anywhere now, leaving it out speeds things up considerably
            //			List<JsonOwlObject> inferredTypeObjsWithAll = new ArrayList<JsonOwlObject>();
            //			//TODO this is particularly slow as there can be a lot of inferred types
            //			Set<OWLClass> inferredTypesWithAll = inferenceProvider.getAllTypes(i);
            //			// optimization, do not render inferences, if they are equal to the asserted ones
            //			if (assertedTypes.equals(inferredTypesWithAll) == false) {
            //				for(OWLClass c : inferredTypesWithAll) {
            //					if (c.isBuiltIn() == false) {
            //						inferredTypeObjsWithAll.add(renderObject(c));
            //					}
            //				}
            //			}
            //			if (inferredTypeObjsWithAll.isEmpty() == false) {
            //				json.inferredTypeWithAll = inferredTypeObjsWithAll.toArray(new JsonOwlObject[inferredTypeObjsWithAll.size()]);
            //			}


        }
        final List<JsonAnnotation> anObjs = new ArrayList<JsonAnnotation>();
        Set<OWLAnnotationAssertionAxiom> annotationAxioms = ont.getAnnotationAssertionAxioms(i.getIRI());
        for (OWLAnnotationAssertionAxiom ax : annotationAxioms) {
            JsonAnnotation jsonAnn = JsonTools.create(ax.getProperty(), ax.getValue(), null, curieHandler);
            if (jsonAnn != null) {
                anObjs.add(jsonAnn);
            }
        }
        Set<OWLDataPropertyAssertionAxiom> dataPropertyAxioms = ont.getDataPropertyAssertionAxioms(i);
        for (OWLDataPropertyAssertionAxiom ax : dataPropertyAxioms) {
            OWLDataProperty property = ax.getProperty().asOWLDataProperty();
            JsonAnnotation jsonAnn = JsonTools.create(property, ax.getObject(), null, curieHandler);
            if (jsonAnn != null) {
                anObjs.add(jsonAnn);
            }
        }

        if (anObjs.isEmpty() == false) {
            json.annotations = anObjs.toArray(new JsonAnnotation[anObjs.size()]);
        }
        return json;
    }

    /**
     * @param opa
     * @return Map to be passed to Gson
     */
    public JsonOwlFact renderObject(OWLObjectPropertyAssertionAxiom opa) {
        OWLNamedIndividual subject;
        OWLObjectProperty property;
        OWLNamedIndividual object;

        JsonOwlFact fact = null;
        if (opa.getSubject().isNamed() && opa.getObject().isNamed() && opa.getProperty().isAnonymous() == false) {
            subject = opa.getSubject().asOWLNamedIndividual();
            property = opa.getProperty().asOWLObjectProperty();
            object = opa.getObject().asOWLNamedIndividual();

            fact = new JsonOwlFact();
            fact.subject = curieHandler.getCuri(subject);
            fact.property = curieHandler.getCuri(property);
            if (graph == null && go_lego_repo != null) {
                try {
                    fact.propertyLabel = go_lego_repo.getLabel(property);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } else {
                fact.propertyLabel = graph.getLabel(property);
            }
            if (fact.propertyLabel == null) {
                fact.propertyLabel = curieHandler.getCuri(property);
            }
            fact.object = curieHandler.getCuri(object);

            JsonAnnotation[] anObjs = renderAnnotations(opa.getAnnotations(), curieHandler);
            if (anObjs != null && anObjs.length > 0) {
                fact.annotations = anObjs;
            }
        }
        return fact;
    }

    public JsonOwlObject renderObject(OWLObjectProperty p) {
        String id = curieHandler.getCuri(p);
        String label = getLabel(p, id);
        JsonOwlObject json = JsonOwlObject.createProperty(id, label);
        return json;
    }

    private JsonOwlObject renderObject(OWLObjectPropertyExpression p) {
        if (p.isAnonymous()) {
            return null;
        }
        return renderObject(p.asOWLObjectProperty());
    }

    /**
     * @param x
     * @return Object to be passed to Gson
     */
    private JsonOwlObject renderObject(OWLClassExpression x) {
        if (x.isAnonymous()) {
            JsonOwlObject json = null;
            if (x instanceof OWLObjectIntersectionOf) {
                List<JsonOwlObject> expressions = new ArrayList<JsonOwlObject>();
                for (OWLClassExpression y : ((OWLObjectIntersectionOf) x).getOperands()) {
                    expressions.add(renderObject(y));
                }
                json = JsonOwlObject.createIntersection(expressions);
            } else if (x instanceof OWLObjectUnionOf) {
                List<JsonOwlObject> expressions = new ArrayList<JsonOwlObject>();
                for (OWLClassExpression y : ((OWLObjectUnionOf) x).getOperands()) {
                    expressions.add(renderObject(y));
                }
                json = JsonOwlObject.createUnion(expressions);
            } else if (x instanceof OWLObjectSomeValuesFrom) {
                OWLObjectSomeValuesFrom svf = (OWLObjectSomeValuesFrom) x;
                JsonOwlObject prop = renderObject(svf.getProperty());
                JsonOwlObject filler = renderObject(svf.getFiller());
                if (prop != null && filler != null) {
                    json = JsonOwlObject.createSvf(prop, filler);
                }
            } else if (x instanceof OWLObjectComplementOf) {
                OWLObjectComplementOf comp = (OWLObjectComplementOf) x;
                OWLClassExpression operand = comp.getOperand();
                JsonOwlObject operandJson = renderObject(operand);
                if (operandJson != null) {
                    json = JsonOwlObject.createComplement(operandJson);
                }
            } else {
                // TODO
            }
            return json;
        } else {
            return renderObject(x.asOWLClass());
        }
    }

    private JsonOwlObject renderObject(OWLClass cls) {
        String id = curieHandler.getCuri(cls);
        JsonOwlObject json = JsonOwlObject.createCls(id, getLabel(cls, id));
        return json;
    }

    protected String getLabel(OWLNamedObject i, String id) {
        String label = null;
        if (class_label != null && class_label.containsKey(i.getIRI().toString())) {
            label = class_label.get(i.getIRI().toString());
        } else if (graph != null) {
            label = graph.getLabel(i);
        } else if (go_lego_repo != null) {
            try {
                label = go_lego_repo.getLabel(i);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return label;
    }


    public static Pair<List<JsonRelationInfo>, List<JsonRelationInfo>> renderProperties(MolecularModelManager<?> mmm, Set<OWLObjectProperty> importantRelations, CurieHandler curieHandler) throws OWLOntologyCreationException {
        /* [{
         *   id: {String}
         *   label: {String}
         *   relevant: {boolean} // flag to indicate if this is a relation to be used in the model
         *   ?color: {String} // TODO in the future?
         *   ?glyph: {String} // TODO in the future?
         * }]
         */
        // retrieve (or load) all ontologies
        // put in a new wrapper
        MinervaOWLGraphWrapper wrapper = new MinervaOWLGraphWrapper(mmm.getOntology());
        Collection<IRI> imports = mmm.getImports();
        OWLOntologyManager manager = wrapper.getManager();
        for (IRI iri : imports) {
            OWLOntology ontology = manager.getOntology(iri);
            if (ontology == null) {
                // only try to load it, if it isn't already loaded
                try {
                    ontology = manager.loadOntology(iri);
                } catch (OWLOntologyDocumentAlreadyExistsException e) {
                    IRI existing = e.getOntologyDocumentIRI();
                    ontology = manager.getOntology(existing);
                } catch (OWLOntologyAlreadyExistsException e) {
                    OWLOntologyID id = e.getOntologyID();
                    ontology = manager.getOntology(id);
                }
            }
            if (ontology == null) {
                LOG.warn("Could not find an ontology for IRI: " + iri);
            } else {
                wrapper.addSupportOntology(ontology);
            }
        }

        // get all properties from all loaded ontologies
        Set<OWLObjectProperty> properties = new HashSet<OWLObjectProperty>();
        Set<OWLDataProperty> dataProperties = new HashSet<OWLDataProperty>();
        Set<OWLOntology> allOntologies = wrapper.getAllOntologies();
        for (OWLOntology o : allOntologies) {
            properties.addAll(o.getObjectPropertiesInSignature());
            dataProperties.addAll(o.getDataPropertiesInSignature());
        }

        // sort properties
        List<OWLObjectProperty> propertyList = new ArrayList<OWLObjectProperty>(properties);
        List<OWLDataProperty> dataPropertyList = new ArrayList<OWLDataProperty>(dataProperties);
        Collections.sort(propertyList);
        Collections.sort(dataPropertyList);

        // retrieve id and label for all properties
        List<JsonRelationInfo> relList = new ArrayList<JsonRelationInfo>();
        for (OWLObjectProperty p : propertyList) {
            if (p.isBuiltIn()) {
                // skip owl:topObjectProperty
                continue;
            }
            JsonRelationInfo json = new JsonRelationInfo();
            json.id = curieHandler.getCuri(p);
            json.label = wrapper.getLabel(p);
            if (importantRelations != null && (importantRelations.contains(p))) {
                json.relevant = true;
            } else {
                json.relevant = false;
            }
            relList.add(json);
        }

        // retrieve id and label for all data properties
        List<JsonRelationInfo> dataList = new ArrayList<JsonRelationInfo>();
        for (OWLDataProperty p : dataPropertyList) {
            if (p.isBuiltIn()) {
                continue;
            }
            JsonRelationInfo json = new JsonRelationInfo();
            json.id = curieHandler.getCuri(p);
            json.label = wrapper.getLabel(p);
            dataList.add(json);
        }
        IOUtils.closeQuietly(wrapper);
        return Pair.of(relList, dataList);
    }

    public static List<JsonEvidenceInfo> renderEvidences(MolecularModelManager<?> mmm, CurieHandler curieHandler) throws OWLException, IOException {
        return renderEvidences(mmm.getOntology().getOWLOntologyManager(), curieHandler);
    }

    private static final Object ecoMutex = new Object();
    private static volatile OntologyMapperPair<EcoMapper> eco = null;

    public static List<JsonEvidenceInfo> renderEvidences(OWLOntologyManager manager, CurieHandler curieHandler) throws OWLException, IOException {
        // TODO remove the hard coded ECO dependencies
        OntologyMapperPair<EcoMapper> pair;
        synchronized (ecoMutex) {
            if (eco == null) {
                eco = EcoMapperFactory.createEcoMapper(manager);
            }
            pair = eco;
        }
        final MinervaOWLGraphWrapper graph = pair.getGraph();
        final EcoMapper mapper = pair.getMapper();
        Set<OWLClass> ecoClasses = graph.getAllOWLClasses();
        Map<OWLClass, String> codesForEcoClasses = mapper.getCodesForEcoClasses();
        List<JsonEvidenceInfo> relList = new ArrayList<JsonEvidenceInfo>();
        for (OWLClass ecoClass : ecoClasses) {
            if (ecoClass.isBuiltIn()) {
                continue;
            }
            JsonEvidenceInfo json = new JsonEvidenceInfo();
            json.id = curieHandler.getCuri(ecoClass);
            json.label = graph.getLabel(ecoClass);
            String code = codesForEcoClasses.get(ecoClass);
            if (code != null) {
                json.code = code;
            }
            relList.add(json);
        }
        return relList;
    }

    public static String renderToJson(String modelId, OWLOntology ont, InferenceProvider inferenceProvider, CurieHandler curieHandler) {
        return renderToJson(modelId, ont, inferenceProvider, curieHandler, false);
    }

    public static String renderToJson(String modelId, OWLOntology ont, InferenceProvider inferenceProvider, CurieHandler curieHandler, boolean prettyPrint) {
        MolecularModelJsonRenderer r = new MolecularModelJsonRenderer(modelId, ont, inferenceProvider, curieHandler);
        JsonModel model = r.renderModel();
        return renderToJson(model, prettyPrint);
    }

    public static String renderToJson(Object model, boolean prettyPrint) {
        GsonBuilder builder = new GsonBuilder();
        if (prettyPrint) {
            builder = builder.setPrettyPrinting();
        }
        Gson gson = builder.create();
        String json = gson.toJson(model);
        return json;
    }

    public static <T> T parseFromJson(String json, Class<T> type) {
        Gson gson = new GsonBuilder().create();
        T result = gson.fromJson(json, type);
        return result;
    }

    public static <T> T[] parseFromJson(String requestString, Type requestType) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(requestString, requestType);
    }

}
