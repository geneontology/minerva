package org.geneontology.minerva.server.handler;

import static org.geneontology.minerva.server.handler.OperationsTools.normalizeUserId;
import static org.geneontology.minerva.server.handler.OperationsTools.requireNotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.UndoAwareMolecularModelManager;
import org.geneontology.minerva.UndoAwareMolecularModelManager.UndoMetadata;
import org.geneontology.minerva.generate.GolrSeedingDataProvider;
import org.geneontology.minerva.generate.ModelSeeding;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.server.handler.M3SeedHandler.SeedResponse.SeedResponseData;
import org.geneontology.reasoner.ExpressionMaterializingReasoner;
import org.geneontology.reasoner.ExpressionMaterializingReasonerFactory;
import org.geneontology.reasoner.OWLExtendedReasonerFactory;
import org.glassfish.jersey.server.JSONP;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.OWLClass;

import owltools.graph.OWLGraphWrapper;

public class JsonOrJsonpSeedHandler extends ModelCreator implements M3SeedHandler {

	public static final String JSONP_DEFAULT_CALLBACK = "jsonp";
	public static final String JSONP_DEFAULT_OVERWRITE = "json.wrf";
	
	private static final Logger logger = Logger.getLogger(JsonOrJsonpSeedHandler.class);
	
	private final String golrUrl;
	private final OWLExtendedReasonerFactory<ExpressionMaterializingReasoner> factory;
	
	public JsonOrJsonpSeedHandler(UndoAwareMolecularModelManager m3, String defaultModelState, String golr) {
		super(m3, defaultModelState);
		this.golrUrl = golr;
		factory = new ExpressionMaterializingReasonerFactory(new ElkReasonerFactory());
	}

	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public SeedResponse fromProcessPost(String intention, String packetId, String requestString) {
		// only privileged calls are allowed
		SeedResponse response = new SeedResponse(null, intention, packetId);
		return error(response, "Insufficient permissions for seed operation.", null);
	}

	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public SeedResponse fromProcessPostPrivileged(String uid, String intention, String packetId, String requestString) {
		return fromProcess(uid, intention, checkPacketId(packetId), requestString);
	}

	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public SeedResponse fromProcessGet(String intention, String packetId, String requestString) {
		// only privileged calls are allowed
		SeedResponse response = new SeedResponse(null, intention, packetId);
		return error(response, "Insufficient permissions for seed operation.", null);
	}

	@Override
	@JSONP(callback = JSONP_DEFAULT_CALLBACK, queryParam = JSONP_DEFAULT_OVERWRITE)
	public SeedResponse fromProcessGetPrivileged(String uid, String intention, String packetId, String requestString) {
		return fromProcess(uid, intention, checkPacketId(packetId), requestString);
	}

	private static String checkPacketId(String packetId) {
		if (packetId == null) {
			packetId = PacketIdGenerator.generateId();
		}
		return packetId;
	}
	
	private SeedResponse fromProcess(String uid, String intention, String packetId, String requestString) {
		SeedResponse response = new SeedResponse(uid, intention, packetId);
		try {
			requestString = StringUtils.trimToNull(requestString);
			requireNotNull(requestString, "The request may not be null.");
			SeedRequest request = MolecularModelJsonRenderer.parseFromJson(requestString, SeedRequest.class);
			uid = normalizeUserId(uid);
			UndoMetadata token = new UndoMetadata(uid);
			ModelContainer model = createModel(uid, token, VariableResolver.EMPTY, null);
			return seedFromProcess(request, model, response, token);
		} catch (Exception e) {
			return error(response, "Could not successfully handle batch request.", e);
		} catch (Throwable t) {
			logger.error("A critical error occured.", t);
			return error(response, "An internal error occured at the server level.", t);
		}
	}
	
	private SeedResponse seedFromProcess(SeedRequest request, ModelContainer model, SeedResponse response, UndoMetadata token) throws Exception {
		// check required fields
		requireNotNull(request.process, "A process id is required for seeding");
		requireNotNull(request.taxon, "A taxon id is required for seeding");
		
		// prepare seeder
		OWLGraphWrapper graph = new OWLGraphWrapper(model.getAboxOntology());
		Set<OWLClass> locationRoots = new HashSet<OWLClass>();
		if (request.locationRoots != null) {
			for(String loc : request.locationRoots) {
				OWLClass cls = graph.getOWLClassByIdentifier(loc);
				if (cls != null) {
					locationRoots.add(cls);
				}
			}
		}
		Set<String> evidenceRestriction = request.evidenceRestriction != null ? new HashSet<>(Arrays.asList(request.evidenceRestriction)) : null;
		Set<String> blackList = request.ignoreList != null ? new HashSet<>(Arrays.asList(request.ignoreList)) : null;
		Set<String> taxonRestriction = Collections.singleton(request.taxon);
		ExpressionMaterializingReasoner reasoner = null;
		try {
			reasoner = factory.createReasoner(model.getAboxOntology());
			reasoner.setIncludeImports(true);
			GolrSeedingDataProvider provider = new GolrSeedingDataProvider(golrUrl, graph, 
					reasoner, locationRoots, evidenceRestriction, taxonRestriction, blackList);
			ModelSeeding<UndoMetadata> seeder = new ModelSeeding<UndoMetadata>(reasoner, provider, curieHandler);

			// seed
			seeder.seedModel(model, m3, request.process, token);

			// render result
			// create response.data
			response.messageType = SeedResponse.MESSAGE_TYPE_SUCCESS;
			response.data = new SeedResponseData();

			// model id
			response.data.id = curieHandler.getCuri(model.getModelId());
			return response;
		}
		finally {
			if (reasoner != null) {
				reasoner.dispose();
			}
		}
	}
	
	/*
	 * commentary is now to be a string, not an unknown multi-leveled object.
	 */
	private SeedResponse error(SeedResponse state, String msg, Throwable e) {
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
}
