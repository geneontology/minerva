package org.geneontology.minerva.server.inferences;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.apache.log4j.Logger;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.json.InferenceProvider;
import org.geneontology.minerva.server.validation.MinervaShexValidator;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

public class InferenceProviderCreatorImpl implements InferenceProviderCreator {
	
	private final static Logger LOG = Logger.getLogger(InferenceProviderCreatorImpl.class);
	
	private final OWLReasonerFactory rf;
	private final Semaphore concurrentLock;
	private final boolean useSLME;
	private final String name;
	private final MinervaShexValidator shex;

	InferenceProviderCreatorImpl(OWLReasonerFactory rf, int maxConcurrent, boolean useSLME, String name, MinervaShexValidator shex) {
		super();
		this.rf = rf;
		this.useSLME = useSLME;
		this.name = name;
		this.concurrentLock = new Semaphore(maxConcurrent);
		this.shex = shex;
	}

	public static InferenceProviderCreator createElk(boolean useSLME, MinervaShexValidator shex) {
		String name;
		if (useSLME) {
			name = "ELK-SLME";
		}
		else {
			name = "ELK";
		}
		return new InferenceProviderCreatorImpl(new ElkReasonerFactory(), 1, useSLME, name, shex);
	}
	
//	public static InferenceProviderCreator createHermiT(MinervaShexValidator shex) {
//		int maxConcurrent = Runtime.getRuntime().availableProcessors();
//		return createHermiT(maxConcurrent, shex);
//	}
	
//	public static InferenceProviderCreator createHermiT(int maxConcurrent, MinervaShexValidator shex) {
//		return new InferenceProviderCreatorImpl(new org.semanticweb.HermiT.ReasonerFactory(), maxConcurrent, true, "Hermit-SLME", shex);
//	}

	@Override
	public InferenceProvider create(ModelContainer model) throws OWLOntologyCreationException, InterruptedException {
		OWLOntology ont = model.getAboxOntology();
		final OWLOntologyManager m = ont.getOWLOntologyManager();
		OWLOntology module = null;
		OWLReasoner reasoner = null;
		try {
			InferenceProvider provider;
			synchronized (ont) {
				concurrentLock.acquire();
				try {
					if (useSLME) {
						LOG.info("Creating for module: "+model.getModelId());
						ModuleType mtype = ModuleType.BOT;
						SyntacticLocalityModuleExtractor sme = new SyntacticLocalityModuleExtractor(m, ont, mtype);
						Set<OWLEntity> seeds = new HashSet<OWLEntity>(ont.getIndividualsInSignature());
						module = ont = sme.extractAsOntology(seeds, IRI.generateDocumentIRI());
						LOG.info("Done creating module: "+model.getModelId());
					}
					reasoner = rf.createReasoner(ont);
					provider = MapInferenceProvider.create(reasoner, ont, shex);
				}
				finally {
					concurrentLock.release();
				}
			}
			return provider;
		}
		finally {
			if (reasoner != null) {
				reasoner.dispose();
			}
			if (module != null) {
				m.removeOntology(module);
			}
		}
		
	}

	@Override
	public String toString() {
		return "InferenceProviderCreator: " + name;
	}

	
}
