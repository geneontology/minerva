package org.geneontology.minerva.evidence;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.curie.CurieHandler;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.util.OWLClassExpressionVisitorAdapter;

import owltools.gaf.eco.EcoMapperFactory;
import owltools.gaf.eco.SimpleEcoMapper;

public class FindGoCodes {

	private final SimpleEcoMapper mapper;
	private final CurieHandler curieHandler;

	public FindGoCodes(CurieHandler curieHandler) throws IOException {
		this(EcoMapperFactory.createSimple(), curieHandler);
	}

	public FindGoCodes(SimpleEcoMapper mapper, CurieHandler curieHandler) {
		this.mapper = mapper;
		this.curieHandler = curieHandler;
	}

	public Pair<String, String> findShortEvidence(OWLClass eco, String ecoId, OWLOntology model) {
		Pair<String, String> pair =  mapper.getGoCode(ecoId);
		if (pair == null) {
			// try to find a GO-Code mapping in the named super classes
			// mini walker code, with cycle detection
			final Set<OWLClass> done = new HashSet<>();
			final Queue<OWLClass> queue = new LinkedList<>();
			queue.addAll(getNamedDirectSuperClasses(eco, model));
			done.add(eco);
			while (queue.isEmpty() == false && pair == null) {
				OWLClass current = queue.poll();
				pair = mapper.getGoCode(curieHandler.getCuri(current));
				if (done.add(current) && pair == null) {
					queue.addAll(getNamedDirectSuperClasses(current, model)) ;
				}
			}
		}
		return pair;
	}

	private Set<OWLClass> getNamedDirectSuperClasses(OWLClass current, OWLOntology model) {
		final Set<OWLClass> dedup = new HashSet<OWLClass>();
		Set<OWLOntology> closure = model.getImportsClosure();
		for (OWLOntology ont : closure) {
			for(OWLSubClassOfAxiom ax : ont.getSubClassAxiomsForSubClass(current)) {
				ax.getSuperClass().accept(new OWLClassExpressionVisitorAdapter(){

					@Override
					public void visit(OWLClass cls) {
						if (cls.isBuiltIn() == false) {
							dedup.add(cls);
						}
					}
				});
			}
		}
		return dedup;
	}
}
