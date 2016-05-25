package org.geneontology.minerva.server.inferences;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.ModelContainer.ModelChangeListener;
import org.geneontology.minerva.json.InferenceProvider;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

public class CachingInferenceProviderCreatorImpl extends InferenceProviderCreatorImpl {
	
	private final Map<ModelContainer, InferenceProvider> inferenceCache = new ConcurrentHashMap<>();
	
	protected CachingInferenceProviderCreatorImpl(OWLReasonerFactory rf, int maxConcurrent, boolean useSLME, String name) {
		super(rf, maxConcurrent, useSLME, name);
	}

	public static InferenceProviderCreator createElk(boolean useSLME) {
		String name;
		if (useSLME) {
			name = "Caching ELK-SLME";
		}
		else {
			name = "Caching ELK";
		}
		return new CachingInferenceProviderCreatorImpl(new ElkReasonerFactory(), 1, useSLME, name);
	}

	public static InferenceProviderCreator createHermiT() {
		int maxConcurrent = Runtime.getRuntime().availableProcessors();
		return createHermiT(maxConcurrent);
	}
	
	public static InferenceProviderCreator createHermiT(int maxConcurrent) {
		return new CachingInferenceProviderCreatorImpl(new org.semanticweb.HermiT.ReasonerFactory(),
				maxConcurrent, true, "Caching Hermit-SLME");
	}

	@Override
	public InferenceProvider create(final ModelContainer model) throws OWLOntologyCreationException, InterruptedException {
		synchronized (model.getAboxOntology()) {
			InferenceProvider inferenceProvider = inferenceCache.get(model);
			if (inferenceProvider == null) {
				addMiss();
				inferenceProvider = super.create(model);
				model.registerListener(new ModelChangeListenerImplementation(model));
				inferenceCache.put(model, inferenceProvider);
			}
			else {
				addHit();
			}
			return inferenceProvider;
		}
	}
	
	protected void addHit() {
		// do nothing, hook for debugging
	}
	
	protected void addMiss() {
		// do nothing, hook for debugging
	}
	
	protected void clear() {
		inferenceCache.clear();
	}
	
	private final class ModelChangeListenerImplementation implements ModelChangeListener {
		private final ModelContainer model;
	
		private ModelChangeListenerImplementation(ModelContainer model) {
			this.model = model;
		}
	
		@Override
		public void handleChange(List<OWLOntologyChange> changes) {
			synchronized (model.getAboxOntology()) {
				inferenceCache.remove(model);
				model.unRegisterListener(this);
			}
		}
	
		@Override
		public void dispose() {
			synchronized (model.getAboxOntology()) {
				inferenceCache.remove(model);
				model.unRegisterListener(this);
			}
		}
	}
}
