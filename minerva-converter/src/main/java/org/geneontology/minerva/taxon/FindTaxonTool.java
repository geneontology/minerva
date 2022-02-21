package org.geneontology.minerva.taxon;

import org.apache.commons.io.IOUtils;
import org.geneontology.minerva.MinervaOWLGraphWrapper;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.obolibrary.obo2owl.Obo2OWLConstants;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLClassExpressionVisitorExAdapter;

import java.util.HashSet;
import java.util.Set;

public class FindTaxonTool {

    public static final IRI IN_TAXON_IRI = IRI.create(Obo2OWLConstants.DEFAULT_IRI_PREFIX + "RO_0002162");

    private final OWLObjectProperty inTaxon;
    private final CurieHandler curieHandler;

    public FindTaxonTool(CurieHandler curieHandler, OWLDataFactory df) {
        this.curieHandler = curieHandler;
        inTaxon = df.getOWLObjectProperty(IN_TAXON_IRI);
    }

    public String getEntityTaxon(String curie, OWLOntology model) throws UnknownIdentifierException {
        if (curie == null || curie.isEmpty()) {
            return null;
        }
        OWLDataFactory df = model.getOWLOntologyManager().getOWLDataFactory();
        OWLClass cls = df.getOWLClass(curieHandler.getIRI(curie));
        String taxon = getEntityTaxon(cls, model);
        if (taxon == null) {
            MinervaOWLGraphWrapper g = new MinervaOWLGraphWrapper(model);
            cls = g.getOWLClassByIdentifier(curie);
            if (cls != null) {
                taxon = getEntityTaxon(cls, model);
            }
            IOUtils.closeQuietly(g);
        }
        return taxon;
    }

    String getEntityTaxon(OWLClass entity, OWLOntology model) {
        Set<OWLSubClassOfAxiom> axioms = new HashSet<OWLSubClassOfAxiom>();
        for (OWLOntology ont : model.getImportsClosure()) {
            axioms.addAll(ont.getSubClassAxiomsForSubClass(entity));
        }
        for (OWLSubClassOfAxiom axiom : axioms) {
            OWLClassExpression ce = axiom.getSuperClass();
            if (ce instanceof OWLObjectSomeValuesFrom) {
                OWLObjectSomeValuesFrom svf = (OWLObjectSomeValuesFrom) ce;
                if (inTaxon.equals(svf.getProperty())) {
                    OWLClassExpression filler = svf.getFiller();
                    OWLClass c = filler.accept(new OWLClassExpressionVisitorExAdapter<OWLClass>(null) {

                        @Override
                        public OWLClass visit(OWLClass c) {
                            return c;
                        }
                    });
                    if (c != null) {
                        return curieHandler.getCuri(c);
                    }
                }
            }
        }
        return null;
    }

    public OWLAxiom createTaxonAxiom(OWLClass entity, String taxon, OWLOntology model, Set<OWLAnnotation> tags) throws UnknownIdentifierException {
        OWLDataFactory df = model.getOWLOntologyManager().getOWLDataFactory();
        OWLClass taxonCls = df.getOWLClass(curieHandler.getIRI(taxon));
        OWLAxiom axiom = df.getOWLSubClassOfAxiom(entity, df.getOWLObjectSomeValuesFrom(inTaxon, taxonCls), tags);

        return axiom;
    }
}
