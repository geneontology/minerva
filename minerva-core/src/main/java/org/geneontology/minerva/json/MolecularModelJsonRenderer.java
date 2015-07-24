package org.geneontology.minerva.json;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.MolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLObjectComplementOf;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyAlreadyExistsException;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyDocumentAlreadyExistsException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import owltools.gaf.eco.EcoMapper;
import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.EcoMapperFactory.OntologyMapperPair;
import owltools.graph.OWLGraphWrapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * A Renderer that takes a MolecularModel (an OWL ABox) and generates Map objects
 * that can be translated to JSON using Gson.
 * 
 * @author cjm
 */
public class MolecularModelJsonRenderer {
	
	private static Logger LOG = Logger.getLogger(MolecularModelJsonRenderer.class);

	private final OWLOntology ont;
	private final OWLGraphWrapper graph;
	private final CurieHandler curieHandler;
	private final OWLReasoner reasoner;
	
	public static final ThreadLocal<DateFormat> AnnotationTypeDateFormat = new ThreadLocal<DateFormat>(){

		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("yyyy-MM-dd");
		}
		
	};

	public MolecularModelJsonRenderer(ModelContainer model, OWLReasoner reasoner, CurieHandler curieHandler) {
		this(model.getAboxOntology(), new OWLGraphWrapper(model.getAboxOntology()), reasoner, curieHandler);
	}
	
	public MolecularModelJsonRenderer(OWLOntology ontology, OWLReasoner reasoner, CurieHandler curieHandler) {
		this(ontology, new OWLGraphWrapper(ontology), reasoner, curieHandler);
	}
	
	public MolecularModelJsonRenderer(OWLGraphWrapper graph, OWLReasoner reasoner, CurieHandler curieHandler) {
		this(graph.getSourceOntology(), graph, reasoner, curieHandler);
	}

	private MolecularModelJsonRenderer(OWLOntology ont, OWLGraphWrapper graph, OWLReasoner reasoner, CurieHandler curieHandler) {
		super();
		this.ont = ont;
		this.graph = graph;
		this.reasoner = reasoner;
		this.curieHandler = curieHandler;
	}
	
	/**
	 * @return Map to be passed to Gson
	 */
	public JsonModel renderModel() {
		JsonModel json = new JsonModel();
		
		// per-Individual
		List<JsonOwlIndividual> iObjs = new ArrayList<JsonOwlIndividual>();
		for (OWLNamedIndividual i : ont.getIndividualsInSignature()) {
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

		// per-Property
		List<JsonOwlObject> pObjs = new ArrayList<JsonOwlObject>();
		for (OWLObjectProperty p : usedProps) {
			pObjs.add(renderObject(p));
		}
		json.properties  = pObjs.toArray(new JsonOwlObject[pObjs.size()]);

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
			JsonAnnotation json = JsonTools.create(annotation.getProperty(), annotation.getValue(), curieHandler);
			if (json != null) {
				anObjs.add(json);
			}
		}
		return anObjs.toArray(new JsonAnnotation[anObjs.size()]);
	}
	
	public Pair<JsonOwlIndividual[], JsonOwlFact[]> renderIndividuals(Collection<OWLNamedIndividual> individuals) {
		List<JsonOwlIndividual> iObjs = new ArrayList<JsonOwlIndividual>();
		Set<OWLNamedIndividual> individualIds = new HashSet<OWLNamedIndividual>();
		final Set<OWLObjectPropertyAssertionAxiom> opAxioms = new HashSet<OWLObjectPropertyAssertionAxiom>();
		for (OWLIndividual i : individuals) {
			if (i instanceof OWLNamedIndividual) {
				OWLNamedIndividual named = (OWLNamedIndividual)i;
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
	 * @param i
	 * @return Map to be passed to Gson
	 */
	public JsonOwlIndividual renderObject(OWLNamedIndividual i) {
		JsonOwlIndividual json = new JsonOwlIndividual();
		json.id = curieHandler.getCuri(i);
		
		List<JsonOwlObject> typeObjs = new ArrayList<JsonOwlObject>();
		Set<OWLClassExpression> assertedTypes = i.getTypes(ont);
		for (OWLClassExpression x : assertedTypes) {
			typeObjs.add(renderObject(x));
		}
		json.type = typeObjs.toArray(new JsonOwlObject[typeObjs.size()]);
		
		if (reasoner != null && reasoner.isConsistent()) {
			List<JsonOwlObject> inferredTypeObjs = new ArrayList<JsonOwlObject>();
			Set<OWLClass> inferredTypes = reasoner.getTypes(i, true).getFlattened();
			// optimization, do not render inferences, if they are equal to the asserted ones
			if (assertedTypes.equals(inferredTypes) == false) {
				for(OWLClass c : inferredTypes) {
					if (c.isBuiltIn() == false) {
						inferredTypeObjs.add(renderObject(c));
					}
				}
			}
			if (inferredTypeObjs.isEmpty() == false) {
				json.inferredType = inferredTypeObjs.toArray(new JsonOwlObject[inferredTypeObjs.size()]);
			}
		}
		
		final List<JsonAnnotation> anObjs = new ArrayList<JsonAnnotation>();
		Set<OWLAnnotationAssertionAxiom> annotationAxioms = ont.getAnnotationAssertionAxioms(i.getIRI());
		for (OWLAnnotationAssertionAxiom ax : annotationAxioms) {
			JsonAnnotation jsonAnn = JsonTools.create(ax.getProperty(), ax.getValue(), curieHandler);
			if (jsonAnn != null) {
				anObjs.add(jsonAnn);
			}
		}
		Set<OWLDataPropertyAssertionAxiom> dataPropertyAxioms = ont.getDataPropertyAssertionAxioms(i);
		for (OWLDataPropertyAssertionAxiom ax : dataPropertyAxioms) {
			OWLDataProperty property = ax.getProperty().asOWLDataProperty();
			JsonAnnotation jsonAnn = JsonTools.create(property, ax.getObject(), curieHandler);
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
			subject = (OWLNamedIndividual) opa.getSubject();
			property = (OWLObjectProperty) opa.getProperty();
			object = (OWLNamedIndividual) opa.getObject();
	
			fact = new JsonOwlFact();
			fact.subject = curieHandler.getCuri(subject);
			fact.property = curieHandler.getCuri(property);
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
	 * @return  Object to be passed to Gson
	 */
	private JsonOwlObject renderObject(OWLClassExpression x) {
		if (x.isAnonymous()) {
			JsonOwlObject json = null;
			if (x instanceof OWLObjectIntersectionOf) {
				List<JsonOwlObject> expressions = new ArrayList<JsonOwlObject>();
				for (OWLClassExpression y : ((OWLObjectIntersectionOf)x).getOperands()) {
					expressions.add(renderObject(y));
				}
				json = JsonOwlObject.createIntersection(expressions);
			}
			else if (x instanceof OWLObjectUnionOf) {
				List<JsonOwlObject> expressions = new ArrayList<JsonOwlObject>();
				for (OWLClassExpression y : ((OWLObjectUnionOf)x).getOperands()) {
					expressions.add(renderObject(y));
				}
				json = JsonOwlObject.createUnion(expressions);
			}
			else if (x instanceof OWLObjectSomeValuesFrom) {
				OWLObjectSomeValuesFrom svf = (OWLObjectSomeValuesFrom)x;
				JsonOwlObject prop = renderObject(svf.getProperty());
				JsonOwlObject filler = renderObject(svf.getFiller());
				if (prop != null && filler != null) {
					json = JsonOwlObject.createSvf(prop, filler);
				}
			}
			else if (x instanceof OWLObjectComplementOf) {
				OWLObjectComplementOf comp = (OWLObjectComplementOf) x;
				OWLClassExpression operand = comp.getOperand();
				JsonOwlObject operandJson = renderObject(operand);
				if (operandJson != null) {
					json = JsonOwlObject.createComplement(operandJson);
				}
			}
			else {
				// TODO
			}
			return json;
		}
		else {
			return renderObject(x.asOWLClass());
		}
	}

	private JsonOwlObject renderObject(OWLClass cls) {
		String id = curieHandler.getCuri(cls);
		JsonOwlObject json = JsonOwlObject.createCls(id, getLabel(cls, id));
		return json;
	}

	protected String getLabel(OWLNamedObject i, String id) {
		return graph.getLabel(i);
	}
	

	public static Pair<List<JsonRelationInfo>,List<JsonRelationInfo>> renderProperties(MolecularModelManager<?> mmm, Set<OWLObjectProperty> importantRelations, CurieHandler curieHandler) throws OWLOntologyCreationException {
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
		OWLGraphWrapper wrapper = new OWLGraphWrapper(mmm.getOntology());
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
				LOG.warn("Could not find an ontology for IRI: "+iri);
			}
			else {
				wrapper.addSupportOntology(ontology);
			}
		}
	
		// get all properties from all loaded ontologies
		Set<OWLObjectProperty> properties = new HashSet<OWLObjectProperty>();
		Set<OWLDataProperty> dataProperties = new HashSet<OWLDataProperty>();
		Set<OWLOntology> allOntologies = wrapper.getAllOntologies();
		for(OWLOntology o : allOntologies) {
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
			}
			else {
				json.relevant = false;
			}
			relList.add(json);
		}
		
		// retrieve id and label for all data properties
		List<JsonRelationInfo> dataList = new ArrayList<JsonRelationInfo>();
		for(OWLDataProperty p : dataPropertyList) {
			if(p.isBuiltIn()) {
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
		return renderEvidences(mmm.getGraph().getManager(), curieHandler);
	}
	
	public static List<JsonEvidenceInfo> renderEvidences(OWLOntologyManager manager, CurieHandler curieHandler) throws OWLException, IOException {
		// TODO remove the hard coded ECO dependencies
		OntologyMapperPair<EcoMapper> pair = EcoMapperFactory.createEcoMapper(manager);
		final OWLGraphWrapper graph = pair.getGraph();
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
	
	public static String renderToJson(OWLOntology ont, OWLReasoner reasoner, CurieHandler curieHandler) {
		return renderToJson(ont, reasoner, curieHandler, false);
	}
	
	public static String renderToJson(OWLOntology ont, OWLReasoner reasoner, CurieHandler curieHandler, boolean prettyPrint) {
		MolecularModelJsonRenderer r = new MolecularModelJsonRenderer(ont, reasoner, curieHandler);
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
