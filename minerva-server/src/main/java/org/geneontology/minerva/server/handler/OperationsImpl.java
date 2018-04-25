package org.geneontology.minerva.server.handler;

import static org.geneontology.minerva.server.handler.OperationsTools.requireNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.geneontology.minerva.CoreMolecularModelManager.DeleteInformation;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.MolecularModelManager;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.UndoAwareMolecularModelManager.ChangeEvent;
import org.geneontology.minerva.UndoAwareMolecularModelManager.UndoMetadata;
import org.geneontology.minerva.json.JsonAnnotation;
import org.geneontology.minerva.json.JsonEvidenceInfo;
import org.geneontology.minerva.json.JsonOwlObject;
import org.geneontology.minerva.json.JsonRelationInfo;
import org.geneontology.minerva.json.JsonTools;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.legacy.GafExportTool;
import org.geneontology.minerva.legacy.sparql.ExportExplanation;
import org.geneontology.minerva.legacy.sparql.GPADSPARQLExport;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse.MetaResponse;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse.ResponseData;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3Request;
import org.geneontology.minerva.server.handler.M3BatchHandler.Operation;
import org.geneontology.minerva.server.handler.OperationsTools.MissingParameterException;
import org.geneontology.minerva.server.validation.BeforeSaveModelValidator;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException;

/**
 * Separate the actual calls to the {@link UndoAwareMolecularModelManager} from the
 * request, error and response handling.
 * 
 * @see JsonOrJsonpBatchHandler
 * @see ModelCreator
 */
abstract class OperationsImpl extends ModelCreator {

	final Set<OWLObjectProperty> importantRelations;
	final BeforeSaveModelValidator beforeSaveValidator;
	final ExternalLookupService externalLookupService;
	private final OWLAnnotationProperty contributor = OWLManager.getOWLDataFactory().getOWLAnnotationProperty(IRI.create("http://purl.org/dc/elements/1.1/contributor"));
	
	private static final Logger LOG = Logger.getLogger(OperationsImpl.class);
	
	OperationsImpl(UndoAwareMolecularModelManager models, 
			Set<OWLObjectProperty> importantRelations,
			ExternalLookupService externalLookupService,
			String defaultModelState) {
		super(models, defaultModelState);
		this.importantRelations = importantRelations;
		this.externalLookupService = externalLookupService;
		this.beforeSaveValidator = new BeforeSaveModelValidator();
	}

	abstract boolean checkLiteralIdentifiers();
	
	abstract boolean validateBeforeSave();
	
	static class BatchHandlerValues implements VariableResolver {
		
		final Set<OWLNamedIndividual> relevantIndividuals = new HashSet<>();
		boolean renderBulk = false;
		boolean nonMeta = false;
		ModelContainer model = null;
		Map<String, OWLNamedIndividual> individualVariable = new HashMap<>();
		
		@Override
		public boolean notVariable(String id) {
			return individualVariable.containsKey(id) == false;
		}
		
		@Override
		public OWLNamedIndividual getVariableValue(String id) throws UnknownIdentifierException {
			if (individualVariable.containsKey(id)) {
				OWLNamedIndividual individual = individualVariable.get(id);
				if (individual == null) {
					throw new UnknownIdentifierException("Variable "+id+" has a null value.");
				}
				return individual;
			}
			return null;
		}
		
		public void addVariableValue(String id, OWLNamedIndividual i) throws UnknownIdentifierException {
			if (id != null) {
				individualVariable.put(id, i);	
			}
		}
	}
	

	private OWLNamedIndividual getIndividual(String id, BatchHandlerValues values) throws UnknownIdentifierException {
		if (values.notVariable(id)) {
			IRI iri = curieHandler.getIRI(id); 
			OWLNamedIndividual i = m3.getIndividual(iri, values.model);
			if (i == null) {
				throw new UnknownIdentifierException("No individual found for id: '"+id+"' and IRI: "+iri+" in model: "+values.model.getModelId());
			}
			return i;
		}
		return values.getVariableValue(id);
	}
	
	/**
	 * Handle the request for an operation regarding an individual.
	 * 
	 * @param request
	 * @param operation
	 * @param userId
	 * @param token
	 * @param values
	 * @return error or null
	 * @throws Exception 
	 */
	String handleRequestForIndividual(M3Request request, Operation operation, String userId, Set<String> providerGroups, UndoMetadata token, BatchHandlerValues values) throws Exception {
		values.nonMeta = true;
		requireNotNull(request.arguments, "request.arguments");
		values.model = checkModelId(values.model, request);

		// get info, no modification
		if (Operation.get == operation) {
			requireNotNull(request.arguments.individual, "request.arguments.individual");
			OWLNamedIndividual i = getIndividual(request.arguments.individual, values);
			values.relevantIndividuals.add(i);
		}
		// create individual (look-up variable first) and add type
		else if (Operation.add == operation) {
			// required: expression
			// optional: more expressions, values
			requireNotNull(request.arguments.expressions, "request.arguments.expressions");
			Set<OWLAnnotation> annotations = extract(request.arguments.values, userId, providerGroups, values, values.model);
			Map<OWLDataProperty, Set<OWLLiteral>> dataProperties = extractDataProperties(request.arguments.values, values.model);
			OWLNamedIndividual individual;
			List<OWLClassExpression> clsExpressions = new ArrayList<OWLClassExpression>(request.arguments.expressions.length);
			for(JsonOwlObject expression : request.arguments.expressions) {
				OWLClassExpression cls = parseM3Expression(expression, values);
				clsExpressions.add(cls);
			}
			if (values.notVariable(request.arguments.individual)) {
				// create indivdual
				if (request.arguments.individualIRI != null) {
					IRI iri = curieHandler.getIRI(request.arguments.individualIRI);
					individual = m3.createIndividualNonReasoning(values.model, iri, annotations, token);
				}
				else {
					individual = m3.createIndividualNonReasoning(values.model, annotations, token);
				}

				// add to render list and set variable
				values.relevantIndividuals.add(individual);
				values.addVariableValue(request.arguments.assignToVariable, individual);
			}
			else {
				individual = values.getVariableValue(request.arguments.individual);
			}
			if (individual != null) {
				// add types
				for (OWLClassExpression clsExpression : clsExpressions) {
					m3.addType(values.model, individual, clsExpression, token);
				}
				
				if (dataProperties.isEmpty() == false) {
					m3.addDataProperties(values.model, individual, dataProperties, token);
				}
				updateDate(values.model, individual, token, m3);
			}
			updateModelAnnotations(values.model, userId, providerGroups, token, m3);
		}
		// remove individual (and all axioms using it)
		else if (Operation.remove == operation){
			// required: modelId, individual
			requireNotNull(request.arguments.individual, "request.arguments.individual");
			OWLNamedIndividual i = getIndividual(request.arguments.individual, values);
			
			DeleteInformation dInfo = m3.deleteIndividual(values.model, i, token);
			handleRemovedAnnotationIRIs(dInfo.usedIRIs, values.model, token);
			updateAnnotationsForDelete(dInfo, values.model, userId, providerGroups, token, m3);
			updateModelAnnotations(values.model, userId, providerGroups, token, m3);
			values.renderBulk = true;
		}				
		// add type / named class assertion
		else if (Operation.addType == operation){
			// required: individual, expressions
			requireNotNull(request.arguments.individual, "request.arguments.individual");
			requireNotNull(request.arguments.expressions, "request.arguments.expressions");
			
			Set<OWLAnnotation> annotations = createGeneratedAnnotations(values.model, userId, providerGroups);
			OWLNamedIndividual i = getIndividual(request.arguments.individual, values);
			
			for(JsonOwlObject expression : request.arguments.expressions) {
				OWLClassExpression cls = parseM3Expression(expression, values);
				m3.addType(values.model, i, cls, token);
				values.relevantIndividuals.add(i);
				values.addVariableValue(request.arguments.assignToVariable, i);
				m3.addAnnotations(values.model, i, annotations, token);
			}
			updateDate(values.model, i, token, m3);
			updateModelAnnotations(values.model, userId, providerGroups, token, m3);
		}
		// remove type / named class assertion
		else if (Operation.removeType == operation){
			// required: individual, expressions
			requireNotNull(request.arguments.individual, "request.arguments.individual");
			requireNotNull(request.arguments.expressions, "request.arguments.expressions");
			
			Set<OWLAnnotation> annotations = createGeneratedAnnotations(values.model, userId, providerGroups);
			OWLNamedIndividual i = getIndividual(request.arguments.individual, values);
			
			for(JsonOwlObject expression : request.arguments.expressions) {
				OWLClassExpression cls = parseM3Expression(expression, values);
				m3.removeType(values.model, i, cls, token);
				values.relevantIndividuals.add(i);
				values.addVariableValue(request.arguments.assignToVariable, i);
				m3.addAnnotations(values.model, i, annotations, token);
			}
			updateDate(values.model, i, token, m3);
			updateModelAnnotations(values.model, userId, providerGroups, token, m3);
		}
		// add annotation
		else if (Operation.addAnnotation == operation){
			// required: individual, values
			requireNotNull(request.arguments.individual, "request.arguments.individual");
			requireNotNull(request.arguments.values, "request.arguments.values");

			Set<OWLAnnotation> annotations = extract(request.arguments.values, userId, providerGroups, values, values.model);
			Map<OWLDataProperty, Set<OWLLiteral>> dataProperties = extractDataProperties(request.arguments.values, values.model);
			OWLNamedIndividual i = getIndividual(request.arguments.individual, values);
			
			values.relevantIndividuals.add(i);
			if (annotations.isEmpty() == false) {
				m3.addAnnotations(values.model, i, annotations, token);
			}
			if (dataProperties.isEmpty() == false) {
				m3.addDataProperties(values.model, i, dataProperties, token);
			}
			values.addVariableValue(request.arguments.assignToVariable, i);
			updateDate(values.model, i, token, m3);
			updateModelAnnotations(values.model, userId, providerGroups, token, m3);
		}
		// remove annotation
		else if (Operation.removeAnnotation == operation){
			// required: individual, values
			requireNotNull(request.arguments.individual, "request.arguments.individual");
			requireNotNull(request.arguments.values, "request.arguments.values");

			Set<OWLAnnotation> annotations = extract(request.arguments.values, null, Collections.emptySet(), values, values.model);
			Map<OWLDataProperty, Set<OWLLiteral>> dataProperties = extractDataProperties(request.arguments.values, values.model);
			OWLNamedIndividual i = getIndividual(request.arguments.individual, values);
			
			Set<IRI> evidenceIRIs = MolecularModelManager.extractEvidenceIRIValues(annotations);
			
			values.relevantIndividuals.add(i);
			if (annotations.isEmpty() == false) {
				m3.removeAnnotations(values.model, i, annotations, token);
				
			}
			if (dataProperties.isEmpty() == false) {
				m3.removeDataProperties(values.model, i, dataProperties, token);
			}
			values.addVariableValue(request.arguments.assignToVariable, i);
			
			handleRemovedAnnotationIRIs(evidenceIRIs, values.model, token);
			updateDate(values.model, i, token, m3);
			updateModelAnnotations(values.model, userId, providerGroups, token, m3);
		}
		else {
			return "Unknown operation: "+operation;
		}
		return null;
	}
	
	private void handleRemovedAnnotationIRIs(Set<IRI> evidenceIRIs, ModelContainer model, UndoMetadata token) {
		if (evidenceIRIs != null) {
			for (IRI evidenceIRI : evidenceIRIs) {
				OWLNamedIndividual i = m3.getIndividual(evidenceIRI, model);
				if (i != null) {
					m3.deleteIndividual(model, i, token);
				}
				// ignoring undefined IRIs
			}
		}
	}

	private OWLClassExpression parseM3Expression(JsonOwlObject expression, BatchHandlerValues values)
			throws MissingParameterException, UnknownIdentifierException, OWLException {
		M3ExpressionParser p = new M3ExpressionParser(checkLiteralIdentifiers(), curieHandler);
		return p.parse(values.model, expression, externalLookupService);
	}
	
	private OWLObjectProperty getProperty(String id, BatchHandlerValues values) throws UnknownIdentifierException {
		OWLObjectProperty p = m3.getObjectProperty(id, values.model);
		if (p == null) {
			throw new UnknownIdentifierException("Could not find a property for id: "+id);
		}
		return p;
	}
	
	/**
	 * Handle the request for an operation regarding an edge.
	 * 
	 * @param request
	 * @param operation
	 * @param userId
	 * @param token
	 * @param values
	 * @return error or null
	 * @throws Exception
	 */
	String handleRequestForEdge(M3Request request, Operation operation, String userId, Set<String> providerGroups, UndoMetadata token, BatchHandlerValues values) throws Exception {
		values.nonMeta = true;
		requireNotNull(request.arguments, "request.arguments");
		values.model = checkModelId(values.model, request);
		// required: subject, predicate, object
		requireNotNull(request.arguments.subject, "request.arguments.subject");
		requireNotNull(request.arguments.predicate, "request.arguments.predicate");
		requireNotNull(request.arguments.object, "request.arguments.object");
		// check for variables
		final OWLNamedIndividual s = getIndividual(request.arguments.subject, values);
		final OWLNamedIndividual o = getIndividual(request.arguments.object, values);
		final OWLObjectProperty p = getProperty(request.arguments.predicate, values);
		values.relevantIndividuals.addAll(Arrays.asList(s, o));
		
		// add edge
		if (Operation.add == operation){
			// optional: values
			Set<OWLAnnotation> annotations = extract(request.arguments.values, userId, providerGroups, values, values.model);
			addDateAnnotation(annotations, values.model.getOWLDataFactory());
			m3.addFact(values.model, p, s, o, annotations, token);
			updateModelAnnotations(values.model, userId, providerGroups, token, m3);
		}
		// remove edge
		else if (Operation.remove == operation){
			Set<IRI> removedIRIs = m3.removeFact(values.model, p, s, o, token);
			if (removedIRIs != null && removedIRIs.isEmpty() == false) {
				// only render bulk, iff there were additional deletes (i.e. evidence removal)
				values.renderBulk = true;
				handleRemovedAnnotationIRIs(removedIRIs, values.model, token);
			}
			updateModelAnnotations(values.model, userId, providerGroups, token, m3);
		}
		// add annotation
		else if (Operation.addAnnotation == operation){
			requireNotNull(request.arguments.values, "request.arguments.values");

			m3.addAnnotations(values.model, p, s, o,
					extract(request.arguments.values, userId, providerGroups, values, values.model), token);
			updateDate(values.model, p, s, o, token, m3);
			updateModelAnnotations(values.model, userId, providerGroups, token, m3);
		}
		// remove annotation
		else if (Operation.removeAnnotation == operation){
			requireNotNull(request.arguments.values, "request.arguments.values");

			Set<OWLAnnotation> annotations = extract(request.arguments.values, null, Collections.emptySet(), values, values.model);
			Set<IRI> evidenceIRIs = MolecularModelManager.extractEvidenceIRIValues(annotations);
			m3.removeAnnotations(values.model, p, s, o, annotations, token);
			handleRemovedAnnotationIRIs(evidenceIRIs, values.model, token);
			updateDate(values.model, p, s, o, token, m3);
			updateModelAnnotations(values.model, userId, providerGroups, token, m3);
		}
		else {
			return "Unknown operation: "+operation;
		}
		return null;
	}
	
	/**
	 * Handle the request for an operation regarding a model.
	 * 
	 * @param request
	 * @param response
	 * @param operation
	 * @param userId
	 * @param token
	 * @param values
	 * @return error or null
	 * @throws Exception
	 */
	String handleRequestForModel(M3Request request, M3BatchResponse response, Operation operation, String userId, Set<String> providerGroups, UndoMetadata token, BatchHandlerValues values) throws Exception {
		// get model
		if (Operation.get == operation){
			values.nonMeta = true;
			requireNotNull(request.arguments, "request.arguments");
			values.model = checkModelId(values.model, request);
			values.renderBulk = true;
		}
		else if (Operation.updateImports == operation){
			values.nonMeta = true;
			requireNotNull(request.arguments, "request.arguments");
			values.model = checkModelId(values.model, request);
			m3.updateImports(values.model);
			values.renderBulk = true;
		}
		// add an empty model
		else if (Operation.add == operation) {
			values.nonMeta = true;
			values.renderBulk = true;
			
			if (request.arguments != null) {
				values.model = createModel(userId, providerGroups, token, values, request.arguments.values);
			}
			else {
				values.model = createModel(userId, providerGroups, token, values, null);
			}
		}
		else if (Operation.addAnnotation == operation) {
			values.nonMeta = true;
			requireNotNull(request.arguments, "request.arguments");
			requireNotNull(request.arguments.values, "request.arguments.values");
			values.model = checkModelId(values.model, request);
			Set<OWLAnnotation> annotations = extract(request.arguments.values, userId, providerGroups, values, values.model);
			if (annotations != null) {
				m3.addModelAnnotations(values.model, annotations, token);
			}
			updateModelAnnotations(values.model, userId, providerGroups, token, m3);
		}
		else if (Operation.removeAnnotation == operation) {
			values.nonMeta = true;
			requireNotNull(request.arguments, "request.arguments");
			requireNotNull(request.arguments.values, "request.arguments.values");
			values.model = checkModelId(values.model, request);
			Set<OWLAnnotation> annotations = extract(request.arguments.values, null, Collections.emptySet(), values, values.model);
			if (annotations != null) {
				m3.removeAnnotations(values.model, annotations, token);
			}
			updateModelAnnotations(values.model, userId, providerGroups, token, m3);
			values.renderBulk = true;
		}
		else if (Operation.exportModel == operation) {
			if (values.nonMeta) {
				// can only be used with other "meta" operations in batch mode, otherwise it would lead to conflicts in the returned signal
				return "Export model can only be combined with other meta operations.";
			}
			requireNotNull(request.arguments, "request.arguments");
			values.model = checkModelId(values.model, request);
			export(response, values.model, userId, providerGroups);
		}
		else if (Operation.exportModelLegacy == operation) {
			if (values.nonMeta) {
				// can only be used with other "meta" operations in batch mode, otherwise it would lead to conflicts in the returned signal
				return "Export legacy model can only be combined with other meta operations.";
			}
			requireNotNull(request.arguments, "request.arguments");
			values.model = checkModelId(values.model, request);
			exportLegacy(response, values.model, request.arguments.format, userId);
		}
		else if (Operation.importModel == operation) {
			values.nonMeta = true;
			requireNotNull(request.arguments, "request.arguments");
			requireNotNull(request.arguments.importModel, "request.arguments.importModel");
			values.model = m3.importModel(request.arguments.importModel);
			
			Set<OWLAnnotation> annotations = extract(request.arguments.values, userId, providerGroups, values, values.model);
			if (annotations != null) {
				m3.addModelAnnotations(values.model, annotations, token);
			}
			updateModelAnnotations(values.model, userId, providerGroups, token, m3);
			values.renderBulk = true;
		}
		else if (Operation.storeModel == operation) {
			values.nonMeta = true;
			requireNotNull(request.arguments, "request.arguments");
			values.model = checkModelId(values.model, request);
			Set<OWLAnnotation> annotations = extract(request.arguments.values, userId, providerGroups, values, values.model);
			if (validateBeforeSave()) {
				List<String> issues = beforeSaveValidator.validateBeforeSave(values.model);
				if (issues != null && !issues.isEmpty()) {
					StringBuilder commentary = new StringBuilder();
					for (Iterator<String> it = issues.iterator(); it.hasNext();) {
						String issue = it.next();
						commentary.append(issue);
						if (it.hasNext()) {
							commentary.append('\n');
						}
					}
					response.commentary = commentary.toString();
					return "Save model failed: validation error(s) before save";
				}
			}
			m3.saveModel(values.model, annotations, token);
			values.renderBulk = true;
		}
		else if (Operation.undo == operation) {
			values.nonMeta = true;
			requireNotNull(request.arguments, "request.arguments");
			values.model = checkModelId(values.model, request);
			m3.undo(values.model, userId);
			values.renderBulk = true;
		}
		else if (Operation.redo == operation) {
			values.nonMeta = true;
			requireNotNull(request.arguments, "request.arguments");
			values.model = checkModelId(values.model, request);
			m3.redo(values.model, userId);
			values.renderBulk = true;
		}
		else if (Operation.getUndoRedo == operation) {
			if (values.nonMeta) {
				// can only be used with other "meta" operations in batch mode, otherwise it would lead to conflicts in the returned signal
				return operation+" cannot be combined with other operations.";
			}
			requireNotNull(request.arguments, "request.arguments");
			values.model = checkModelId(values.model, request);
			getCurrentUndoRedoForModel(response, values.model.getModelId(), userId);
		}
		else {
			return "Unknown operation: "+operation;
		}
		return null;
	}

	private void getCurrentUndoRedoForModel(M3BatchResponse response, IRI modelId, String userId) {
		Pair<List<ChangeEvent>,List<ChangeEvent>> undoRedoEvents = m3.getUndoRedoEvents(modelId);
		initMetaResponse(response);
		List<Map<Object, Object>> undos = new ArrayList<Map<Object,Object>>();
		List<Map<Object, Object>> redos = new ArrayList<Map<Object,Object>>();
		final long currentTime = System.currentTimeMillis();
		for(ChangeEvent undo : undoRedoEvents.getLeft()) {
			Map<Object, Object> data = new HashMap<Object, Object>(3);
			data.put("user-id", undo.getUserId());
			data.put("time", Long.valueOf(currentTime-undo.getTime()));
			// TODO add a summary of the change? axiom count?
			undos.add(data);
		}
		for(ChangeEvent redo : undoRedoEvents.getRight()) {
			Map<Object, Object> data = new HashMap<Object, Object>(3);
			data.put("user-id", redo.getUserId());
			data.put("time", Long.valueOf(currentTime-redo.getTime()));
			// TODO add a summary of the change? axiom count?
			redos.add(data);
		}
		response.data.undo = undos;
		response.data.redo = redos;
	}
	
	private void initMetaResponse(M3BatchResponse response) {
		if (response.data == null) {
			response.data = new ResponseData();
			response.messageType = M3BatchResponse.MESSAGE_TYPE_SUCCESS;
			response.message = "success: 0";
			response.signal = M3BatchResponse.SIGNAL_META;
		}
	}
	
	/**
	 * Handle the request for the meta properties.
	 * 
	 * @param response
	 * @param userId
	 * @throws IOException 
	 * @throws OWLException 
	 */
	void getMeta(M3BatchResponse response, String userId, Set<String> providerGroups) throws IOException, OWLException {
		// init
		initMetaResponse(response);
		if (response.data.meta == null) {
			response.data.meta = new MetaResponse();
		}
		
		// relations
		Pair<List<JsonRelationInfo>, List<JsonRelationInfo>> propPair = MolecularModelJsonRenderer.renderProperties(m3, importantRelations, curieHandler);
		final List<JsonRelationInfo> relList = propPair.getLeft();
		if (relList != null) {
			response.data.meta.relations = relList.toArray(new JsonRelationInfo[relList.size()]);
		}
		
		// data properties
		final List<JsonRelationInfo> propList = propPair.getRight();
		if (propList != null) {
			response.data.meta.dataProperties = propList.toArray(new JsonRelationInfo[propList.size()]);
		}
		
		// evidence
		final List<JsonEvidenceInfo> evidencesList = MolecularModelJsonRenderer.renderEvidences(m3, curieHandler);
		if (evidencesList != null) {
			response.data.meta.evidence = evidencesList.toArray(new JsonEvidenceInfo[evidencesList.size()]);	
		}
		
		// model ids
		// and model annotations
		final Set<IRI> allModelIds = m3.getAvailableModelIds();
		final Map<String,List<JsonAnnotation>> allModelAnnotations = new HashMap<>();
		final Map<String,Map<String,Object>> allModelAnnotationsReadOnly = new HashMap<>();
		final Map<IRI, Set<OWLAnnotation>> annotationsForAllModels = m3.getAllModelAnnotations();
		for (IRI modelId : allModelIds) {
			String curie = curieHandler.getCuri(modelId);
			List<JsonAnnotation> modelAnnotations = new ArrayList<>();
			allModelAnnotations.put(curie, modelAnnotations);
			// Iterate through the model's a.
			Set<OWLAnnotation> annotations = annotationsForAllModels.get(modelId);
			if (annotations != null) {
				for (OWLAnnotation an : annotations) {
					final String label;
					if (an.getProperty().equals(contributor)) {
						final IRI iri;
						if (an.getValue() instanceof IRI) {
							iri = an.getValue().asIRI().get();
						} else if (an.getValue() instanceof OWLLiteral) {
							iri = IRI.create(an.getValue().asLiteral().get().getLiteral());
						} else { iri = null; }
						if (iri != null) { label = m3.getTboxLabelIndex().getOrDefault(iri, null); }
						else { label = null; }
					} else {
						label = null;
					}
					JsonAnnotation json = JsonTools.create(an.getProperty(), an.getValue(), label, curieHandler);
					if (json != null) {
						modelAnnotations.add(json);
					}
				}
			} else {
				LOG.error("No annotations found for model: " + modelId);
			}
			// handle read-only information, currently only the modification flag
			// check modification status
			boolean modified = m3.isModelModified(modelId);
			Map<String,Object> readOnly = Collections.<String, Object>singletonMap("modified-p", Boolean.valueOf(modified));
			allModelAnnotationsReadOnly.put(curie, readOnly);
		}
		response.data.meta.modelsMeta = allModelAnnotations;
		response.data.meta.modelsReadOnly = allModelAnnotationsReadOnly;
	}
	
	void exportAllModels() throws OWLOntologyStorageException, OWLOntologyCreationException, IOException {
		m3.dumpAllStoredModels();
	}
	
	private void export(M3BatchResponse response, ModelContainer model, String userId, Set<String> providerGroups) throws OWLOntologyStorageException, UnknownIdentifierException {
		String exportModel = m3.exportModel(model);
		initMetaResponse(response);
		response.data.exportModel = exportModel;
	}
	
	private void exportLegacy(M3BatchResponse response, ModelContainer model, String format, String userId) throws IOException, OWLOntologyCreationException, UnknownIdentifierException {
		if ("gpad".equals(format)) {
			initMetaResponse(response);
			try {
				response.data.exportModel = new GPADSPARQLExport(curieHandler, m3.getLegacyRelationShorthandIndex(), m3.getTboxShorthandIndex(), m3.getDoNotAnnotateSubset()).exportGPAD(m3.createInferredModel(model.getModelId()));
			} catch (InconsistentOntologyException e) {
				response.messageType = MinervaResponse.MESSAGE_TYPE_ERROR;
				response.message = "The model is inconsistent; a GPAD cannot be created.";
			}
		} else if ("explanations".equals(format)) {
			initMetaResponse(response);
			response.data.exportModel = ExportExplanation.exportExplanation(m3.createInferredModel(model.getModelId()), externalLookupService, m3.getLegacyRelationShorthandIndex());
		} else {
			final GafExportTool exportTool = GafExportTool.getInstance();
			if (format == null) {
				format = "gaf"; // set a default format, if necessary
			}
			Map<String, String> allExported = exportTool.exportModelLegacy(model, curieHandler, externalLookupService, Collections.singleton(format));
			String exported = allExported.get(format);
			if (exported == null) {
				throw new IOException("Unknown export format: "+format);
			}
			initMetaResponse(response);
			response.data.exportModel = exported;
		}
	}
	

	/**
	 * @param model
	 * @param request
	 * @return modelId
	 * @throws MissingParameterException
	 * @throws MultipleModelIdsParameterException
	 * @throws UnknownIdentifierException 
	 */
	public ModelContainer checkModelId(ModelContainer model, M3Request request) 
			throws MissingParameterException, MultipleModelIdsParameterException, UnknownIdentifierException {
		
		if (model == null) {
			final String currentModelId = request.arguments.modelId;
			requireNotNull(currentModelId, "request.arguments.modelId");
			model = m3.checkModelId(curieHandler.getIRI(currentModelId));
		}
		else {
			final String currentModelId = request.arguments.modelId;
			if (currentModelId != null) {
				IRI modelId = curieHandler.getIRI(currentModelId);
				if (model.getModelId().equals(modelId) == false) {
					throw new MultipleModelIdsParameterException("Using multiple modelIds in one batch call is not supported.");
				}
			}
		}
		return model;
	}
	
	private void updateAnnotationsForDelete(DeleteInformation info, ModelContainer model, String userId, Set<String> providerGroups, UndoMetadata token, UndoAwareMolecularModelManager m3) throws UnknownIdentifierException {
		final OWLDataFactory f = model.getOWLDataFactory();
		final OWLAnnotation annotation = createDateAnnotation(f);
		final Set<OWLAnnotation> generated = new HashSet<OWLAnnotation>();
		addGeneratedAnnotations(userId, providerGroups, generated, f);
		for(IRI subject : info.touched) {
			m3.updateAnnotation(model, subject, annotation, token);
			m3.addAnnotations(model, subject, generated, token);
		}
		if (info.updated.isEmpty() == false) {
			Set<OWLObjectPropertyAssertionAxiom> newAxioms = 
					m3.updateAnnotation(model, info.updated, annotation, token);
			m3.addAnnotations(model, newAxioms, generated, token);
		}
	}
	
	static class MultipleModelIdsParameterException extends Exception {

		private static final long serialVersionUID = 4362299465121954598L;

		/**
		 * @param message
		 */
		MultipleModelIdsParameterException(String message) {
			super(message);
		}
		
	}
}
