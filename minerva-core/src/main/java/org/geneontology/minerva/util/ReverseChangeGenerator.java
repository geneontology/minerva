package org.geneontology.minerva.util;

import org.semanticweb.owlapi.model.*;

import java.util.LinkedList;
import java.util.List;

/**
 * Create the reverse of an {@link OWLOntologyChange}.
 */
public class ReverseChangeGenerator implements OWLOntologyChangeVisitorEx<OWLOntologyChange> {

    public static final ReverseChangeGenerator INSTANCE = new ReverseChangeGenerator();

    private ReverseChangeGenerator() {
        // only one instance
    }

    public OWLOntologyChange visit(AddAxiom change) {
        return new RemoveAxiom(change.getOntology(), change.getAxiom());
    }


    public OWLOntologyChange visit(RemoveAxiom change) {
        return new AddAxiom(change.getOntology(), change.getAxiom());
    }


    public OWLOntologyChange visit(SetOntologyID change) {
        return new SetOntologyID(change.getOntology(), change.getOriginalOntologyID());
    }


    public OWLOntologyChange visit(AddImport addImport) {
        return new RemoveImport(addImport.getOntology(), addImport.getImportDeclaration());
    }


    public OWLOntologyChange visit(RemoveImport removeImport) {
        return new AddImport(removeImport.getOntology(), removeImport.getImportDeclaration());
    }


    public OWLOntologyChange visit(AddOntologyAnnotation addOntologyAnnotation) {
        return new RemoveOntologyAnnotation(addOntologyAnnotation.getOntology(), addOntologyAnnotation.getAnnotation());
    }


    public OWLOntologyChange visit(RemoveOntologyAnnotation removeOntologyAnnotation) {
        return new AddOntologyAnnotation(removeOntologyAnnotation.getOntology(), removeOntologyAnnotation.getAnnotation());
    }

    public static List<OWLOntologyChange> invertChanges(List<OWLOntologyChange> originalChanges) {
        final LinkedList<OWLOntologyChange> invertedChanges = new LinkedList<OWLOntologyChange>();
        for (OWLOntologyChange originalChange : originalChanges) {
            OWLOntologyChange invertedChange = originalChange.accept(ReverseChangeGenerator.INSTANCE);
            invertedChanges.push(invertedChange);
        }
        if (invertedChanges.isEmpty()) {
            return null;
        }
        return invertedChanges;
    }
}
