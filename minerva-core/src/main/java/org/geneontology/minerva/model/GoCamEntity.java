package org.geneontology.minerva.model;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class GoCamEntity extends ProvenanceAnnotated {
    String xref;
    String label;
    String exact_match;
    OWLNamedIndividual individual;
    Set<OWLClass> direct_types;
    Set<OWLClass> indirect_types;
    String derived_from;
    GoCamModel in_model;

    public GoCamEntity(OWLNamedIndividual ind, OWLOntology ont, GoCamModel model) {
        in_model = model;
        addAnnotations(ind, ont);
        individual = ind;
        direct_types = new HashSet<OWLClass>();
        for (OWLClassExpression ce : EntitySearcher.getTypes(ind, ont)) {
            if (!ce.isAnonymous()) {
                direct_types.add(ce.asOWLClass());
            }
        }
        model.ind_entity.put(ind, this);
    }

    private void addAnnotations(OWLNamedIndividual ind, OWLOntology ont) {

        Collection<OWLAnnotation> annos = EntitySearcher.getAnnotations(ind, ont);
        comments = new HashSet<String>();
        notes = new HashSet<String>();
        contributors = new HashSet<String>();
        provided_by = new HashSet<String>();
        ;
        for (OWLAnnotation anno : annos) {
            if (anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/contributor")) {
                contributors.add(anno.getValue().asLiteral().get().getLiteral());
            } else if (anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/date")) {
                date = anno.getValue().asLiteral().get().getLiteral();
            } else if (anno.getProperty().getIRI().toString().equals("http://purl.org/pav/providedBy")) {
                provided_by.add(anno.getValue().asLiteral().get().getLiteral());
            } else if (anno.getProperty().getIRI().toString().equals("http://www.w3.org/2000/01/rdf-schema#comment")) {
                String comment = anno.getValue().asLiteral().get().toString();
                comments.add(comment);
            } else if (anno.getProperty().getIRI().toString().equals("http://www.w3.org/2004/02/skos/core#note")) {
                String note = anno.getValue().asLiteral().get().toString();
                notes.add(note);
            } else if (anno.getProperty().getIRI().toString().equals("http://www.geneontology.org/formats/oboInOwl#hasDbXref")) {
                xref = anno.getValue().asLiteral().get().toString();
            } else if (anno.getProperty().getIRI().toString().equals("http://www.w3.org/2000/01/rdf-schema#label")) {
                label = anno.getValue().asLiteral().get().toString();
            } else if (anno.getProperty().getIRI().toString().equals("http://www.w3.org/2004/02/skos/core#exactMatch")) {
                exact_match = anno.getValue().asIRI().get().toString();
            }
        }

    }

    public String stringForClasses(Set<OWLClass> types) {
        if (types == null) {
            return "";
        }
        String c = "";
        for (OWLClass type : types) {
            try {
                String label = in_model.go_lego.getLabel(type);
                if (label != null) {
                    c += label + " ";
                } else {
                    c += type.getIRI().toString() + " ";
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return c;
    }

    public String toString() {
        String g = "";
        if (label != null) {
            g += "label:" + label;
        }
        g += "\nIRI:" + individual.toString() + "\ntypes: " + this.stringForClasses(this.direct_types);
        if (comments != null) {
            g += "\ncomments: " + comments + "\n";
        }
        if (notes != null) {
            g += "\nnotes:" + notes;
        }

        return g;
    }

    public String getXref() {
        return xref;
    }

    public void setXref(String xref) {
        this.xref = xref;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getExact_match() {
        return exact_match;
    }

    public void setExact_match(String exact_match) {
        this.exact_match = exact_match;
    }

    public OWLNamedIndividual getIndividual() {
        return individual;
    }

    public void setIndividual(OWLNamedIndividual individual) {
        this.individual = individual;
    }

    public Set<OWLClass> getDirect_types() {
        return direct_types;
    }

    public void setDirect_types(Set<OWLClass> direct_types) {
        this.direct_types = direct_types;
    }

    public Set<OWLClass> getIndirect_types() {
        return indirect_types;
    }

    public void setIndirect_types(Set<OWLClass> indirect_types) {
        this.indirect_types = indirect_types;
    }

    public String getDerived_from() {
        return derived_from;
    }

    public void setDerived_from(String derived_from) {
        this.derived_from = derived_from;
    }

    public GoCamModel getIn_model() {
        return in_model;
    }

    public void setIn_model(GoCamModel in_model) {
        this.in_model = in_model;
    }

}
