package org.geneontology.minerva.server.handler;

import com.google.common.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.UndoAwareMolecularModelManager.UndoMetadata;
import org.geneontology.minerva.json.*;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse.ResponseData;
import org.geneontology.minerva.server.inferences.InferenceProviderCreator;
import org.glassfish.jersey.server.JSONP;
import org.semanticweb.owlapi.model.OWLObjectProperty;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.geneontology.minerva.server.handler.OperationsTools.*;

public class JsonOrJsonpBatchHandler extends OperationsImpl implements M3BatchHandler {

	public static final String JSONP_DEFAULT_CALLBACK = "jsonp";
	public static final String JSONP_DEFAULT_OVERWRITE = "json.wrf";
	
	
	public static boolean VALIDATE_BEFORE_SAVE = true;
	public boolean CHECK_LITERAL_IDENTIFIERS = true; // TODO remove the temp work-around
	
	private static final Logger logger = Logger.getLogger(JsonOrJsonpBatchHandler.class);
	
	private final InferenceProviderCreator inferenceProviderCreator;
	
	public JsonOrJsonpBatchHandler(UndoAwareMolecularModelManager models,
			String defaultModelState,
			InferenceProviderCreator inferenceProviderCreator,
			Set<OWLObjectProperty> importantRelations,
			ExternalLookupService externalLookupService) {
		super(models, importantRelations, externalLookupService, defaultModelState);
		this.inferenceProviderCreator = inferenceProviderCreator;
	}

	private final Type requestType = new TypeToken<M3Request[]>(){

		// generated
		private static final long serialVersionUID = 5452629810143143422L;
		
	}.getType();
	
	@Override
	boolean checkLiteralIdentifiers() {
		return CHECK_LITERAL_IDENTIFIERS;
	}

	@Override
	boolean validateBeforeSave() {
		return VALIDATE_BEFORE_SAVE;
	}

	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public M3BatchResponse m3BatchGet(String intention, String packetId, String requestString, String useReasoner) {
		return m3Batch(null, Collections.emptySet(), intention, packetId, requestString, useReasoner, false);
	}
	
	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public M3BatchResponse m3BatchGetPrivileged(String uid, Set<String> providerGroups, String intention, String packetId, String requestString, String useReasoner) {
		return m3Batch(uid, providerGroups, intention, packetId, requestString, useReasoner, true);
	}

	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public M3BatchResponse m3BatchPost(String intention, String packetId, String requestString, String useReasoner) {
		return m3Batch(null, Collections.emptySet(), intention, packetId, requestString, useReasoner, false);
	}
	
	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public M3BatchResponse m3BatchPostPrivileged(String uid, Set<String> providerGroups, String intention, String packetId, String requestString, String useReasoner) {
		return m3Batch(uid, providerGroups, intention, packetId, requestString, useReasoner, true);
	}

	private static String checkPacketId(String packetId) {
		if (packetId == null) {
			packetId = PacketIdGenerator.generateId();
		}
		return packetId;
	}
	
	@Override
	public M3BatchResponse m3Batch(String uid, Set<String> providerGroups, String intention, String packetId, M3Request[] requests, boolean useReasoner, boolean isPrivileged) {
		M3BatchResponse response = new M3BatchResponse(uid, providerGroups, intention, checkPacketId(packetId));
		if (requests == null) {
			return error(response, "The batch contains no requests: null value for request array", null);
		}
		try {
			logger.info("returning m3batch server 102");
			return m3Batch(response, requests, uid, providerGroups, useReasoner, isPrivileged);
		} catch (InsufficientPermissionsException e) {
			return error(response, e.getMessage(), null);
		} catch (Exception e) {
			return error(response, "Could not successfully complete batch request.", e);
		} catch (Throwable t) {
			logger.error("A critical error occured.", t);
			return error(response, "An internal error occured at the server level.", t);
		}
	}
	
	private M3BatchResponse m3Batch(String uid, Set<String> providerGroups, String intention, String packetId, String requestString, String useReasonerString, boolean isPrivileged) {
		boolean useReasoner = false;
		if (inferenceProviderCreator != null) {
			useReasonerString = StringUtils.trimToNull(useReasonerString);
			useReasoner = "true".equalsIgnoreCase(useReasonerString);
		}
		M3BatchResponse response = new M3BatchResponse(uid, providerGroups, intention, checkPacketId(packetId));
		requestString = StringUtils.trimToNull(requestString);
		if (requestString == null) {
			return error(response, "The batch contains no requests: null value for request", null);
		}
		try {
			logger.info("server getting requests");
			M3Request[] requests = MolecularModelJsonRenderer.parseFromJson(requestString, requestType);
			logger.info("server preparing response 128 ");
			return m3Batch(response, requests, uid, providerGroups, useReasoner, isPrivileged);
		} catch (Exception e) {
			return error(response, "Could not successfully handle batch request.", e);
		} catch (Throwable t) {
			logger.error("A critical error occured.", t);
			return error(response, "An internal error occured at the server level.", t);
		}
	}
	
	private M3BatchResponse m3Batch(M3BatchResponse response, M3Request[] requests, String userId, Set<String> providerGroups, boolean useReasoner, boolean isPrivileged) throws InsufficientPermissionsException, Exception {
		userId = normalizeUserId(userId);
		UndoMetadata token = new UndoMetadata(userId);
		
		final BatchHandlerValues values = new BatchHandlerValues();
		for (M3Request request : requests) {
			requireNotNull(request, "request");
			requireNotNull(request.entity, "entity");
			requireNotNull(request.operation, "operation");
			final Entity entity = request.entity;
			final Operation operation = request.operation;
			checkPermissions(entity, operation, isPrivileged);

			// individual
			if (Entity.individual == entity) {
				String error = handleRequestForIndividual(request, operation, userId, providerGroups, token, values);
				if (error != null) {
					return error(response, error, null);
				}
			}
			// edge
			else if (Entity.edge == entity) {
				String error = handleRequestForEdge(request, operation, userId, providerGroups, token, values);
				if (error != null) {
					return error(response, error, null);
				}
			}
			//model
			else if (Entity.model == entity) {
				String error = handleRequestForModel(request, response, operation, userId, providerGroups, token, values);
				if (error != null) {
					return error(response, error, null);
				}
			}
			// meta (e.g. relations, model ids, evidence)
			else if (Entity.meta == entity) {
				if (Operation.get == operation){
					if (values.nonMeta) {
						// can only be used with other "meta" operations in batch mode, otherwise it would lead to conflicts in the returned signal
						return error(response, "Get meta entity can only be combined with other meta operations.", null);
					}
					getMeta(response, userId, providerGroups);
				} else if (Operation.exportAll == operation) {
					exportAllModels();
					response.messageType = "success";
					response.signal = "meta";
					response.message = "Dumped all models to folder";
					return response;
				} else if (Operation.sparql == operation) {
					handleSPARQLRequest(request, response);
				} else {
					return error(response, "Unknown operation: "+operation, null);
				}
			}
			else {
				return error(response, "Unknown entity: "+entity, null);
			}
		}
		if (M3BatchResponse.SIGNAL_META.equals(response.signal)) {
			return response;
		}
		if (values.model == null) {
			return error(response, "Empty batch calls are not supported, at least one request is required.", null);
		}
		// update reasoner
		// report state
		InferenceProvider inferenceProvider = null;
		boolean isConsistent = true;
		boolean isConformant = true;
		if (inferenceProviderCreator != null && useReasoner) {
			logger.info("server building inference provider");
			inferenceProvider = inferenceProviderCreator.create(values.model);
			logger.info("server done building inference provider");
			isConsistent = inferenceProvider.isConsistent();
			response.setReasoned(true);
			values.renderBulk = true; // to ensure that all individuals are in the response
			logger.info("assembling validation results");
			org.geneontology.minerva.validation.ValidationResultSet validations = inferenceProvider.getValidation_results();
			logger.info("done assembling validation results");
			isConformant = validations.allConformant();	
		}

		// create response.data
		response.data = new ResponseData();
		logger.info("server setting up json renderer");
		final MolecularModelJsonRenderer renderer = createModelRenderer(values.model, externalLookupService, inferenceProvider, curieHandler);
		logger.info("server done setting up json renderer");
		if (values.renderBulk) {
			// render complete model
			logger.info("server render bulk");
			JsonModel jsonModel = renderer.renderModel();
			logger.info("server render bulk ini response data");
			initResponseData(jsonModel, response.data);
			response.signal = M3BatchResponse.SIGNAL_REBUILD;
		}
		else {
			response.signal = M3BatchResponse.SIGNAL_MERGE;
			// render individuals
			logger.info("server render individuals");
			if (values.relevantIndividuals.isEmpty() == false) {
				Pair<JsonOwlIndividual[],JsonOwlFact[]> pair = renderer.renderIndividuals(values.relevantIndividuals);
				response.data.individuals = pair.getLeft();
				response.data.facts = pair.getRight();
			}
			// add model annotations
			response.data.annotations = MolecularModelJsonRenderer.renderModelAnnotations(values.model.getAboxOntology(), curieHandler);
			response.data.modelId = curieHandler.getCuri(values.model.getModelId());
		}
		logger.info("server adding other information");
		// add other infos to data
		if (!isConsistent) {
			response.data.inconsistentFlag =  Boolean.TRUE;
		}
		if(!isConformant) {
			response.data.validation_results = inferenceProvider.getValidation_results();
		}
		response.data.modifiedFlag = Boolean.valueOf(values.model.isModified());
		// These are required for an "okay" response.
		response.messageType = M3BatchResponse.MESSAGE_TYPE_SUCCESS;
		if( response.message == null ){
			response.message = "success";
		}
		logger.info("server sending response 260 \n"+response);
		return response;
	}

	public static void initResponseData(JsonModel jsonModel, ResponseData data) {
		data.modelId = jsonModel.modelId;
		data.individuals = jsonModel.individuals;
		data.facts = jsonModel.facts;
		data.annotations = jsonModel.annotations;
	}
	
	/*
	 * commentary is now to be a string, not an unknown multi-leveled object.
	 */
	private M3BatchResponse error(M3BatchResponse state, String msg, Throwable e) {
		state.messageType = "error";
		state.message = msg;
		if (e != null) {

			// Add in the exception name if possible.
			String ename = e.getClass().getName();
			if( ename != null ){
				state.message = state.message + " Exception: " + ename + ".";
			}
			
			// And the exception message.
			String emsg = e.getMessage();
			if( emsg != null ){
				state.message = state.message + " " + emsg;
			}
			
			// Add the stack trace as commentary.
			StringWriter stacktrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stacktrace));
			state.commentary = stacktrace.toString();
		}
		return state;
	}

	protected void checkPermissions(Entity entity, Operation operation, boolean isPrivileged) throws InsufficientPermissionsException {
		// TODO make this configurable
		if (isPrivileged == false) {
			switch (operation) {
			case get:
			case sparql:
			case exportModel:
			case exportModelLegacy:
			case exportAll:
				// positive list, all other operation require a privileged call
				break;
			default :
				throw new InsufficientPermissionsException("Insufficient permissions for the operation "+operation+" on entity: "+entity);
			}
		}
	}
	
	static class InsufficientPermissionsException extends Exception {
		
		private static final long serialVersionUID = -3751573576960618428L;

		InsufficientPermissionsException(String msg) {
			super(msg);
		}
	}
}
