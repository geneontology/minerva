package org.geneontology.minerva.server.inferences;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.geneontology.minerva.json.InferenceProvider;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class MapInferenceProvider implements InferenceProvider {

	private final boolean isConsistent;
	private final Map<OWLNamedIndividual, Set<OWLClass>> inferredTypes;

	public static InferenceProvider create(OWLReasoner r, OWLOntology ont) {
		Map<OWLNamedIndividual, Set<OWLClass>> inferredTypes = new HashMap<>();
		boolean isConsistent = r.isConsistent();
		if (isConsistent) {
			Set<OWLNamedIndividual> individuals = ont.getIndividualsInSignature();
			for (OWLNamedIndividual individual : individuals) {
				Set<OWLClass> inferred = new HashSet<>();
				Set<OWLClass> flattened = r.getTypes(individual, true).getFlattened();
				for (OWLClass cls : flattened) {
					if (cls.isBuiltIn() == false) {
						inferred.add(cls);
					}
				}
				inferredTypes.put(individual, inferred);
			}
		}
		return new MapInferenceProvider(isConsistent, inferredTypes);
	}

	MapInferenceProvider(boolean isConsistent, Map<OWLNamedIndividual, Set<OWLClass>> inferredTypes) {
		this.isConsistent = isConsistent;
		this.inferredTypes = inferredTypes;
	}

	@Override
	public boolean isConsistent() {
		return isConsistent;
	}

	@Override
	public Set<OWLClass> getTypes(OWLNamedIndividual i) {
		Set<OWLClass> result = Collections.emptySet();
		if (isConsistent && i != null) {
			Set<OWLClass> inferences = inferredTypes.get(i);
			if (inferences != null) {
				result = Collections.unmodifiableSet(inferences);
			}
		}
		return result;
	}
}
