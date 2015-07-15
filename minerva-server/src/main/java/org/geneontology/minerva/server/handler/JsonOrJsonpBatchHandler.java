package org.geneontology.minerva.server.handler;

import static org.geneontology.minerva.server.handler.OperationsTools.createModelRenderer;
import static org.geneontology.minerva.server.handler.OperationsTools.normalizeUserId;
import static org.geneontology.minerva.server.handler.OperationsTools.requireNotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.UndoAwareMolecularModelManager.UndoMetadata;
import org.geneontology.minerva.json.JsonModel;
import org.geneontology.minerva.json.JsonOwlFact;
import org.geneontology.minerva.json.JsonOwlIndividual;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.server.external.ExternalLookupService;
import org.geneontology.minerva.server.handler.M3BatchHandler.M3BatchResponse.ResponseData;
import org.glassfish.jersey.server.JSONP;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import com.google.common.reflect.TypeToken;

public class JsonOrJsonpBatchHandler extends OperationsImpl implements M3BatchHandler {

	public static final String JSONP_DEFAULT_CALLBACK = "jsonp";
	public static final String JSONP_DEFAULT_OVERWRITE = "json.wrf";
	
	
	public static boolean USE_USER_ID = true;
	public static boolean ADD_INFERENCES = true;
	public static boolean VALIDATE_BEFORE_SAVE = true;
	public static boolean ENFORCE_EXTERNAL_VALIDATE = false;
	public boolean CHECK_LITERAL_IDENTIFIERS = true; // TODO remove the temp work-around
	
	private static final Logger logger = Logger.getLogger(JsonOrJsonpBatchHandler.class);
	
	private final boolean useReasoner;
	
	public JsonOrJsonpBatchHandler(UndoAwareMolecularModelManager models,
			boolean useReasoner, boolean useModuleReasoner,
			Set<OWLObjectProperty> importantRelations,
			ExternalLookupService externalLookupService) {
		super(models, importantRelations, externalLookupService, useModuleReasoner);
		this.useReasoner = useReasoner;
	}

	private final Type requestType = new TypeToken<M3Request[]>(){

		// generated
		private static final long serialVersionUID = 5452629810143143422L;
		
	}.getType();
	
	@Override
	boolean enforceExternalValidate() {
		return ENFORCE_EXTERNAL_VALIDATE;
	}

	@Override
	boolean checkLiteralIdentifiers() {
		return CHECK_LITERAL_IDENTIFIERS;
	}

	@Override
	boolean validateBeforeSave() {
		return VALIDATE_BEFORE_SAVE;
	}

	@Override
	boolean useUserId() {
		return USE_USER_ID;
	}

	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public M3BatchResponse m3BatchGet(String intention, String packetId, String requestString) {
		return m3Batch(null, intention, packetId, requestString, false);
	}
	
	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public M3BatchResponse m3BatchGetPrivileged(String uid, String intention, String packetId, String requestString) {
		return m3Batch(uid, intention, packetId, requestString, true);
	}

	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public M3BatchResponse m3BatchPost(String intention, String packetId, String requestString) {
		return m3Batch(null, intention, packetId, requestString, false);
	}
	
	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public M3BatchResponse m3BatchPostPrivileged(String uid, String intention, String packetId, String requestString) {
		return m3Batch(uid, intention, packetId, requestString, true);
	}

	private static String checkPacketId(String packetId) {
		if (packetId == null) {
			packetId = PacketIdGenerator.generateId();
		}
		return packetId;
	}
	
	@Override
	public M3BatchResponse m3Batch(String uid, String intention, String packetId, M3Request[] requests, boolean isPrivileged) {
		M3BatchResponse response = new M3BatchResponse(uid, intention, checkPacketId(packetId));
		try {
			return m3Batch(response, requests, uid, isPrivileged);
		} catch (InsufficientPermissionsException e) {
			return error(response, e.getMessage(), null);
		} catch (Exception e) {
			return error(response, "Could not successfully complete batch request.", e);
		} catch (Throwable t) {
			logger.error("A critical error occured.", t);
			return error(response, "An internal error occured at the server level.", t);
		}
	}
	
	private M3BatchResponse m3Batch(String uid, String intention, String packetId, String requestString, boolean isPrivileged) {
		M3BatchResponse response = new M3BatchResponse(uid, intention, checkPacketId(packetId));
		try {
			M3Request[] requests = MolecularModelJsonRenderer.parseFromJson(requestString, requestType);
			return m3Batch(response, requests, uid, isPrivileged);
		} catch (Exception e) {
			return error(response, "Could not successfully handle batch request.", e);
		} catch (Throwable t) {
			logger.error("A critical error occured.", t);
			return error(response, "An internal error occured at the server level.", t);
		}
	}
	
	private void checkShortFormModelId(M3Request request) {
		if (request.arguments != null) {
			request.arguments.modelId = m3.getLongFormModelId(request.arguments.modelId);
		}
	}
	
	private M3BatchResponse m3Batch(M3BatchResponse response, M3Request[] requests, String userId, boolean isPrivileged) throws InsufficientPermissionsException, Exception {
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
			checkShortFormModelId(request);

			// individual
			if (Entity.individual == entity) {
				String error = handleRequestForIndividual(request, operation, userId, token, values);
				if (error != null) {
					return error(response, error, null);
				}
			}
			// edge
			else if (Entity.edge == entity) {
				String error = handleRequestForEdge(request, operation, userId, token, values);
				if (error != null) {
					return error(response, error, null);
				}
			}
			//model
			else if (Entity.model == entity) {
				String error = handleRequestForModel(request, response, operation, userId, token, values);
				if (error != null) {
					return error(response, error, null);
				}
			}
			// meta (e.g. relations, model ids, evidence)
			else if (Entity.meta == entity) {
				if (Operation.get == operation){
					if (values.nonMeta) {
						// can only be used with other "meta" operations in batch mode, otherwise it would lead to conflicts in the returned signal
						return error(response, "Get Relations can only be combined with other meta operations.", null);
					}
					getMeta(response, userId);
				}
				else {
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
		if (values.modelId == null) {
			return error(response, "Empty batch calls are not supported, at least one request is required.", null);
		}
		// get model
		final ModelContainer model = m3.getModel(values.modelId);
		if (model == null) {
			throw new UnknownIdentifierException("Could not retrieve a model for id: "+values.modelId);
		}
		// update reasoner
		// report state
		final OWLReasoner reasoner;
		final boolean isConsistent;
		if (useReasoner) {
			if (useModuleReasoner) {
				reasoner = model.getModuleReasoner();
			}
			else {
				reasoner = model.getReasoner();
				reasoner.flush();
			}
			isConsistent = reasoner.isConsistent();
		}
		else {
			reasoner = null;
			isConsistent = true;
		}

		// create response.data
		response.data = new ResponseData();
		final MolecularModelJsonRenderer renderer;
		if (useReasoner && ADD_INFERENCES && isConsistent) {
			renderer = createModelRenderer(model, externalLookupService, reasoner);
		}
		else {
			renderer = createModelRenderer(model, externalLookupService, null);
		}
		if (values.renderBulk) {
			// render complete model
			JsonModel jsonModel = renderer.renderModel();
			initResponseData(jsonModel, response.data);
			response.signal = M3BatchResponse.SIGNAL_REBUILD;
		}
		else {
			response.signal = M3BatchResponse.SIGNAL_MERGE;
			// render individuals
			if (values.relevantIndividuals.isEmpty() == false) {
				Pair<JsonOwlIndividual[],JsonOwlFact[]> pair = renderer.renderIndividuals(values.relevantIndividuals);
				response.data.individuals = pair.getLeft();
				response.data.facts = pair.getRight();
			}
			// add model annotations
			response.data.annotations = MolecularModelJsonRenderer.renderModelAnnotations(model.getAboxOntology());
		}
		
		// add other infos to data
		response.data.id = m3.getShortFormModelId(values.modelId);
		if (!isConsistent) {
			response.data.inconsistentFlag =  Boolean.TRUE;
		}
		// These are required for an "okay" response.
		response.messageType = M3BatchResponse.MESSAGE_TYPE_SUCCESS;
		if( response.message == null ){
			response.message = "success";
		}
		return response;
	}

	public static void initResponseData(JsonModel jsonModel, ResponseData data) {
		data.individuals = jsonModel.individuals;
		data.facts = jsonModel.facts;
		data.properties = jsonModel.properties;
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
			case exportModel:
			case exportModelLegacy:
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
