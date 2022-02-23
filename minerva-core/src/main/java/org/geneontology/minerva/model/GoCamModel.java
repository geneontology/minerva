package org.geneontology.minerva.model;

import org.apache.log4j.Logger;
import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.geneontology.minerva.BlazegraphOntologyManager;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryException;
import org.semanticweb.owlapi.model.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GoCamModel extends ProvenanceAnnotated {
    private static Logger LOG = Logger.getLogger(GoCamModel.class);
    BlazegraphOntologyManager go_lego;
    String modelstate;
    Set<String> in_taxon;
    String title;
    Set<String> imports;
    String oboinowlid;
    String iri;
    //the whole thing
    OWLOntology ont;
    //the discretized bits of activity flow
    Set<ActivityUnit> activities;
    Map<OWLNamedIndividual, Set<String>> ind_types;
    Map<OWLNamedIndividual, GoCamEntity> ind_entity;
    OWLClass mf;
    OWLClass bp;
    OWLClass cc;
    OWLClass me;
    GoCamModelStats stats;
    Map<OWLObjectProperty, Integer> causal_count;

    public GoCamModel(OWLOntology abox, BlazegraphMolecularModelManager m3) throws IOException, MalformedQueryException, QueryEvaluationException, RepositoryException {
        ont = abox;
        me = ont.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/go/extensions/reacto.owl#molecular_event"));
        mf = ont.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0003674"));
        bp = ont.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0008150"));
        cc = ont.getOWLOntologyManager().getOWLDataFactory().getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0005575"));
        causal_count = new HashMap<OWLObjectProperty, Integer>();
        go_lego = m3.getGolego_repo();
        iri = abox.getOntologyID().getOntologyIRI().get().toString();
        ind_entity = new HashMap<OWLNamedIndividual, GoCamEntity>();
        addAnnotations();
        //setIndTypesWithOwl();
        setIndTypesWithSparql(m3, iri);
        addActivities();
        this.setGoCamModelStats();
    }

    private void setIndTypesWithSparql(BlazegraphMolecularModelManager m3, String graph_id) throws MalformedQueryException, QueryEvaluationException, RepositoryException, IOException {
        Map<OWLNamedIndividual, Set<String>> iTypesAndComplementTypes = new HashMap<OWLNamedIndividual, Set<String>>();
        Set<String> all_types = new HashSet<String>();
        TupleQueryResult r = (TupleQueryResult) m3.executeSPARQLQuery(""
                + "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
                + "select ?instance ?type where {"
                + "GRAPH <" + graph_id + "> { "
                + "?instance rdf:type <http://www.w3.org/2002/07/owl#NamedIndividual> ."
                + "{ ?instance rdf:type ?type . } UNION { ?instance rdf:type ?complement . ?complement owl:complementOf ?type . }"
                + "FILTER (isIRI(?type)) "
                + "FILTER (?type != <http://www.w3.org/2002/07/owl#NamedIndividual> ) "
                + "}}", 100);
        while (r.hasNext()) {
            BindingSet bs = r.next();
            String instance = bs.getBinding("instance").getValue().stringValue();
            String type = bs.getBinding("type").getValue().stringValue();
            OWLNamedIndividual i = ont.getOWLOntologyManager().getOWLDataFactory().getOWLNamedIndividual(IRI.create(instance));
            if (!iTypesAndComplementTypes.containsKey(i)) {
                iTypesAndComplementTypes.put(i, new HashSet<String>());
            }
            Set<String> types = iTypesAndComplementTypes.get(i);
            types.add(type);
            all_types.add(type);
        }
        r.close();
        Map<String, String> old_new = go_lego.mapDeprecated(all_types);
        Set<String> corrected_types = go_lego.replaceDeprecated(all_types, old_new);
        Map<String, Set<String>> type_roots = go_lego.getSuperCategoryMap(corrected_types);
        //set global
        ind_types = new HashMap<OWLNamedIndividual, Set<String>>();
        for (OWLNamedIndividual ind : iTypesAndComplementTypes.keySet()) {
            //fix deprecated
            Set<String> types = go_lego.replaceDeprecated(iTypesAndComplementTypes.get(ind), old_new);
            //convert to root types
            Set<String> roots = new HashSet<String>();
            for (String type : types) {
                if (type_roots.get(type) != null) {
                    roots.addAll(type_roots.get(type));
                }
            }
            ind_types.put(ind, roots);
        }
    }

    private void setIndTypesWithOwl() throws IOException {
        boolean fix_deprecated = true;
        Set<OWLNamedIndividual> inds = ont.getIndividualsInSignature();
        ind_types = go_lego.getSuperCategoryMapForIndividuals(inds, ont, fix_deprecated);
    }

    private void addActivities() throws IOException {
        activities = new HashSet<ActivityUnit>();
        for (OWLNamedIndividual ind : ind_types.keySet()) {
            Set<String> types = ind_types.get(ind);
            if (types != null) {
                if (types.contains(mf.getIRI().toString()) || types.contains(me.getIRI().toString())) {
                    ActivityUnit unit = new ActivityUnit(ind, ont, this);

                    boolean skip = false;
                    for (String comment : unit.getComments()) {
                        if (comment.contains("reaction from external pathway")) {
                            skip = true;
                            break;
                        }
                    }
                    if (!skip) {
                        activities.add(unit);
                        ind_entity.put(ind, unit);
                        for (OWLObjectProperty prop : unit.causal_out.keySet()) {
                            Integer np = causal_count.get(prop);
                            if (np == null) {
                                np = 0;
                            }
                            np++;
                            causal_count.put(prop, np);
                        }
                    }
                }
            }
        }
    }

    private void addAnnotations() {
        Set<OWLAnnotation> annos = ont.getAnnotations();
        in_taxon = new HashSet<String>();
        comments = new HashSet<String>();
        notes = new HashSet<String>();
        contributors = new HashSet<String>();
        ;
        provided_by = new HashSet<String>();
        ;
        for (OWLAnnotation anno : annos) {
            if (anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/title")) {
                title = anno.getValue().asLiteral().get().getLiteral();
            }
            if (anno.getProperty().getIRI().toString().equals("http://geneontology.org/lego/modelstate")) {
                modelstate = anno.getValue().asLiteral().get().getLiteral();
            }
            if (anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/contributor")) {
                contributors.add(anno.getValue().asLiteral().get().getLiteral());
            }
            if (anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/date")) {
                date = anno.getValue().asLiteral().get().getLiteral();
            }
            if (anno.getProperty().getIRI().toString().equals("http://purl.org/pav/providedBy")) {
                provided_by.add(anno.getValue().asLiteral().get().getLiteral());
            }
            if (anno.getProperty().getIRI().toString().equals("https://w3id.org/biolink/vocab/in_taxon")) {
                if (anno.getValue().asIRI().isPresent()) {
                    String taxon = anno.getValue().toString();
                    in_taxon.add(taxon);
                }
            }
            if (anno.getProperty().getIRI().toString().equals("http://www.w3.org/2000/01/rdf-schema#comment")) {
                String comment = anno.getValue().asLiteral().get().toString();
                comments.add(comment);
            }
            if (anno.getProperty().getIRI().toString().equals("http://www.w3.org/2004/02/skos/core#note")) {
                String note = anno.getValue().asLiteral().get().toString();
                notes.add(note);
            }
        }

    }

    public String toString() {
        String g = title + "\n" + iri + "\n" + modelstate + "\n" + contributors + "\n" + date + "\n" + provided_by + "\n" + in_taxon + "\n";
        return g;
    }

    public void setGoCamModelStats() {
        this.stats = new GoCamModelStats(this);
    }

    public GoCamModelStats getGoCamModelStats() {
        return this.stats;
    }

    public BlazegraphOntologyManager getGo_lego() {
        return go_lego;
    }

    public void setGo_lego(BlazegraphOntologyManager go_lego) {
        this.go_lego = go_lego;
    }

    public String getModelstate() {
        return modelstate;
    }

    public void setModelstate(String modelstate) {
        this.modelstate = modelstate;
    }

    public Set<String> getIn_taxon() {
        return in_taxon;
    }

    public void setIn_taxon(Set<String> in_taxon) {
        this.in_taxon = in_taxon;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Set<String> getImports() {
        return imports;
    }

    public void setImports(Set<String> imports) {
        this.imports = imports;
    }

    public String getOboinowlid() {
        return oboinowlid;
    }

    public void setOboinowlid(String oboinowlid) {
        this.oboinowlid = oboinowlid;
    }

    public String getIri() {
        return iri;
    }

    public void setIri(String iri) {
        this.iri = iri;
    }

    public OWLOntology getOnt() {
        return ont;
    }

    public void setOnt(OWLOntology ont) {
        this.ont = ont;
    }

    public Set<ActivityUnit> getActivities() {
        return activities;
    }

    public void setActivities(Set<ActivityUnit> activities) {
        this.activities = activities;
    }

    public Map<OWLNamedIndividual, Set<String>> getInd_types() {
        return ind_types;
    }

    public void setInd_types(Map<OWLNamedIndividual, Set<String>> ind_types) {
        this.ind_types = ind_types;
    }

    public Map<OWLNamedIndividual, GoCamEntity> getInd_entity() {
        return ind_entity;
    }

    public void setInd_entity(Map<OWLNamedIndividual, GoCamEntity> ind_entity) {
        this.ind_entity = ind_entity;
    }

    public OWLClass getMf() {
        return mf;
    }

    public void setMf(OWLClass mf) {
        this.mf = mf;
    }

    public OWLClass getBp() {
        return bp;
    }

    public void setBp(OWLClass bp) {
        this.bp = bp;
    }

    public OWLClass getCc() {
        return cc;
    }

    public void setCc(OWLClass cc) {
        this.cc = cc;
    }

    public GoCamModelStats getStats() {
        return stats;
    }

    public void setStats(GoCamModelStats stats) {
        this.stats = stats;
    }

    public Map<OWLObjectProperty, Integer> getCausal_count() {
        return causal_count;
    }

    public void setCausal_count(Map<OWLObjectProperty, Integer> causal_count) {
        this.causal_count = causal_count;
    }

}
