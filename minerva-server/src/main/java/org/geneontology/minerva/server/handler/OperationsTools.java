package org.geneontology.minerva.server.handler;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.geneontology.minerva.BlazegraphOntologyManager;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.json.InferenceProvider;
import org.geneontology.minerva.json.MolecularModelJsonRenderer;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLOntology;

public class OperationsTools {

	static void requireNotNull(Object value, String msg) throws MissingParameterException {
		if (value == null) {
			throw new MissingParameterException("Expected non-null value for: "+msg);
		}
	}
	
	
	static class MissingParameterException extends Exception {

		private static final long serialVersionUID = 4362299465121954598L;

		/**
		 * @param message
		 */
		MissingParameterException(String message) {
			super(message);
		}
		
	}

	/**
	 * Normalize the userId.
	 * 
	 * @param userId
	 * @return normalized id or null
	 */
	static String normalizeUserId(String userId) {
		if (userId != null) {
			userId = StringUtils.trimToNull(userId);
			// quick hack, may be removed once all users are required to have a user id.
			if ("anonymous".equalsIgnoreCase(userId)) {
				return null;
			}
		}
		return userId;
	}
	
	/**
	 * @param modelId
	 * @param model
	 * @param externalLookupService
	 * @param inferenceProvider
	 * @param curieHandler
	 * @return renderer
	 */
	public static MolecularModelJsonRenderer createModelRenderer(
			final String modelId,
			final OWLOntology model, 
			final ExternalLookupService externalLookupService,
			final InferenceProvider inferenceProvider,
			final CurieHandler curieHandler) {
		
		MolecularModelJsonRenderer renderer;
		if (externalLookupService != null) {
			renderer = new MolecularModelJsonRenderer(modelId, model, inferenceProvider, curieHandler) {

				@Override
				protected String getLabel(OWLNamedObject i, String id) {
					String label = super.getLabel(i, id);
					if (label == null ) {
						List<LookupEntry> lookup = externalLookupService.lookup(i.getIRI());
						if (lookup != null && !lookup.isEmpty()) {
							LookupEntry entry = lookup.iterator().next();
							label = entry.label;
						}
					}
					return label;
				}
			};
		}
		else {
			renderer = new MolecularModelJsonRenderer(modelId, model, inferenceProvider, curieHandler);
		}
		return renderer;
	}
	
	/**
	 * @param model
	 * @param externalLookupService
	 * @param inferenceProvider
	 * @param curieHandler
	 * @return renderer
	 */
	static MolecularModelJsonRenderer createModelRenderer(
			final ModelContainer model, 
			final ExternalLookupService externalLookupService,
			final InferenceProvider inferenceProvider,
			final CurieHandler curieHandler) {
		return createModelRenderer(curieHandler.getCuri(model.getModelId()), model.getAboxOntology(),
				externalLookupService, inferenceProvider, curieHandler);
	}
	
	//BlazegraphOntologyManager
	static MolecularModelJsonRenderer createModelRenderer(
			final ModelContainer model, 
			final BlazegraphOntologyManager go_lego_repo,
			final InferenceProvider inferenceProvider,
			final CurieHandler curieHandler) {
		
		MolecularModelJsonRenderer renderer = new MolecularModelJsonRenderer(curieHandler.getCuri(model.getModelId()), model.getAboxOntology(),
				go_lego_repo, inferenceProvider, curieHandler);
		
		return renderer;
	}
	
	
}
