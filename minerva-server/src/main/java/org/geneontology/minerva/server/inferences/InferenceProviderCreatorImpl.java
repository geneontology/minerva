package org.geneontology.minerva.server.inferences;

import org.apache.log4j.Logger;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.json.InferenceProvider;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.geneontology.minerva.server.validation.MinervaShexValidator;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;

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
        } else {
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
    public InferenceProvider create(ModelContainer model) throws OWLOntologyCreationException, InterruptedException, IOException {
        OWLOntology ont = model.getAboxOntology();
        final OWLOntologyManager m = ont.getOWLOntologyManager();
        OWLOntology module = null;
        OWLReasoner reasoner = null;
        OWLOntology temp_ont = null;
        try {
            InferenceProvider provider;
            synchronized (ont) {
                concurrentLock.acquire();
                try {
                    if (useSLME) {
                        LOG.info("Creating for module: " + model.getModelId());
                        ModuleType mtype = ModuleType.BOT;
                        SyntacticLocalityModuleExtractor sme = new SyntacticLocalityModuleExtractor(m, ont, mtype);
                        Set<OWLEntity> seeds = new HashSet<OWLEntity>(ont.getIndividualsInSignature());
                        module = ont = sme.extractAsOntology(seeds, IRI.generateDocumentIRI());
                        LOG.info("Done creating module: " + model.getModelId());
                    }
                    //add root types for gene products.
                    //TODO investigate performance impact
                    //tradefoff these queries versus loading all possible genes into tbox
                    //temp_ont = addRootTypesToCopy(ont, shex.externalLookupService);
                    temp_ont = addAllInferredTypesToCopyLocalOntoBlazegraph(ont);
                    //do reasoning and validation on the enhanced model
                    reasoner = rf.createReasoner(temp_ont);
                    provider = MapInferenceProvider.create(reasoner, temp_ont, shex);
                } finally {
                    concurrentLock.release();
                }
            }
            return provider;
        } finally {
            if (reasoner != null) {
                reasoner.dispose();
            }
            if (module != null) {
                m.removeOntology(module);
            }
            if (temp_ont != null) {
                temp_ont.getOWLOntologyManager().removeOntology(temp_ont);
            }
        }

    }


    public OWLOntology addAllInferredTypesToCopyLocalOntoBlazegraph(OWLOntology asserted_ont) throws OWLOntologyCreationException, IOException {
        OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = ontman.getOWLDataFactory();
        OWLOntology temp_ont = ontman.copyOntology(asserted_ont, OntologyCopy.SHALLOW);
        for (OWLAnnotation a : asserted_ont.getAnnotations()) {
            OWLAxiom annoaxiom = df.getOWLAnnotationAssertionAxiom(temp_ont.getOntologyID().getOntologyIRI().get(), a);
            ontman.addAxiom(temp_ont, annoaxiom);
        }
        Set<OWLNamedIndividual> individuals = temp_ont.getIndividualsInSignature();
        Map<String, Set<String>> sub_supers = new HashMap<String, Set<String>>();
        Set<String> uris = new HashSet<String>();
        Map<OWLNamedIndividual, Collection<OWLClassExpression>> individual_asserted_types = new HashMap<OWLNamedIndividual, Collection<OWLClassExpression>>();
        for (OWLNamedIndividual individual : individuals) {
            Collection<OWLClassExpression> asserted_types = EntitySearcher.getTypes(individual, asserted_ont);
            for (OWLClassExpression cls : asserted_types) {
                if (cls.isAnonymous()) {
                    continue;
                }
                IRI class_iri = cls.asOWLClass().getIRI();
                if (class_iri.toString().contains("ECO")) {
                    continue; //this only deals with genes, chemicals, proteins, and complexes.
                }
                uris.add(class_iri.toString());
            }
            individual_asserted_types.put(individual, asserted_types);
        }
        sub_supers = shex.getGo_lego_repo().getNeoRoots(uris);
        Set<OWLAxiom> new_parent_types = new HashSet<OWLAxiom>();
        //for all individuals
        for (OWLNamedIndividual i : individual_asserted_types.keySet()) {
            //for all asserted types
            for (OWLClassExpression asserted : individual_asserted_types.get(i)) {
                //add types for all their parents
                if (asserted.isAnonymous()) {
                    continue;
                }
                OWLClass sub = asserted.asOWLClass();
                Set<String> supers = sub_supers.get(sub.getIRI().toString());
                if (supers != null) {
                    for (String s : supers) {
                        OWLClass parent_class = ontman.getOWLDataFactory().getOWLClass(IRI.create(s));
                        if (!parent_class.isBuiltIn() && (!parent_class.isAnonymous())) {
                            OWLClassAssertionAxiom add_parent_type = df.getOWLClassAssertionAxiom(parent_class, i);
                            new_parent_types.add(add_parent_type);
                        }
                        //add everything into the tbox - (for use later in shex validator)
                        //OWLSubClassOfAxiom subSuper = df.getOWLSubClassOfAxiom(sub, parent_class);
                        //new_parent_types.add(subSuper);
                        //make sure the parent is a subclass of itself.. needed for shex to find it.
                        //OWLSubClassOfAxiom superSuper = df.getOWLSubClassOfAxiom(parent_class, parent_class);
                        //new_parent_types.add(superSuper);
                    }
                }
            }
        }
        if (!new_parent_types.isEmpty()) {
            ontman.addAxioms(temp_ont, new_parent_types);
        }

        return temp_ont;
    }


    public static OWLOntology addRootTypesToCopyViaGolr(OWLOntology asserted_ont, ExternalLookupService externalLookupService) throws OWLOntologyCreationException {
        if (externalLookupService == null) {
            return asserted_ont; //should probably throw some kind of exception here..
        }
        OWLOntology temp_ont = asserted_ont.getOWLOntologyManager().createOntology();
        temp_ont.getOWLOntologyManager().addAxioms(temp_ont, asserted_ont.getAxioms());
        Set<OWLNamedIndividual> individuals = temp_ont.getIndividualsInSignature();
        Set<IRI> to_look_up = new HashSet<IRI>();
        Map<OWLNamedIndividual, Set<IRI>> individual_types = new HashMap<OWLNamedIndividual, Set<IRI>>();
        for (OWLNamedIndividual individual : individuals) {
            Collection<OWLClassExpression> asserted_types = EntitySearcher.getTypes(individual, asserted_ont);
            Set<IRI> ind_types = new HashSet<IRI>();
            for (OWLClassExpression cls : asserted_types) {
                if (cls.isAnonymous()) {
                    continue;
                }
                IRI class_iri = cls.asOWLClass().getIRI();
                if (class_iri.toString().contains("ECO")) {
                    continue; //this only deals with genes, chemicals, proteins, and complexes.
                }
                to_look_up.add(class_iri);
                ind_types.add(class_iri);
            }
            individual_types.put(individual, ind_types);
        }
        //look up all at once
        Map<IRI, List<LookupEntry>> iri_lookup = externalLookupService.lookupBatch(to_look_up);

        if (iri_lookup != null) {
            //add the identified root types on to the individuals in the model
            for (OWLNamedIndividual i : individual_types.keySet()) {
                for (IRI asserted_type : individual_types.get(i)) {
                    if (asserted_type == null) {
                        continue;
                    }
                    List<LookupEntry> lookup = iri_lookup.get(asserted_type);
                    if (lookup != null && !lookup.isEmpty() && lookup.get(0).direct_parent_iri != null) {
                        OWLClass parent_class = temp_ont.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create(lookup.get(0).direct_parent_iri));
                        OWLClassAssertionAxiom add_root = temp_ont.getOWLOntologyManager().getOWLDataFactory().getOWLClassAssertionAxiom(parent_class, i);
                        temp_ont.getOWLOntologyManager().addAxiom(temp_ont, add_root);
                    }
                }
            }
        } else {
            LOG.error("external lookup at failed for batch: " + to_look_up);
        }
        return temp_ont;
    }

    @Override
    public String toString() {
        return "InferenceProviderCreator: " + name;
    }


}
