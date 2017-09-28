package org.geneontology.minerva;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.UndoAwareMolecularModelManager.UndoMetadata;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import owltools.OWLToolsTestBasics;
import owltools.graph.OWLGraphWrapper;
import owltools.io.CatalogXmlIRIMapper;
import owltools.io.ParserWrapper;

public class BlazegraphMolecularModelManagerTest extends OWLToolsTestBasics {

    @Test
    public void test() throws OWLOntologyCreationException, IOException {
        final ParserWrapper pw1 = new ParserWrapper();
        pw1.addIRIMapper(new CatalogXmlIRIMapper(new File("src/test/resources/mmg/catalog-v001.xml")));
        OWLGraphWrapper g = pw1.parseToOWLGraph(getResourceIRIString("mmg/basic-tbox-importer.omn"));
        BlazegraphMolecularModelManager m3 = new BlazegraphMolecularModelManager<Void>(g, "go", "/tmp/journal.jrnl", "/tmp");
        
        String userId = "test-user-id";
        ModelContainer model = m3.generateBlankModel(null);
        Object metadata;
        //m3.saveAllModels(annotations, metadata);
               // GO:0001158 ! enhancer sequence-specific DNA binding
        //OWLNamedIndividual bindingIdividual = m3.createIndividual(model.getModelId(), "GO:0001158", null, null);
        Set<OWLAnnotation> anns = new HashSet<>();
        IRI modelId = model.getModelId();
        // not sure why this cast is necessary:
        //final OWLNamedIndividual i1 = ((MolecularModelManager)m3).createIndividual(modelId, "GO:0038024", anns, null);
        //final OWLNamedIndividual i2 = ((MolecularModelManager)m3).createIndividual(modelId, "GO:0042803", anns, null);



    }
    
    // copied from MMM test
    private final CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();
    // TODO: consider putting in a separate helper util
    private void addPartOf(ModelContainer model, OWLNamedIndividual i1, OWLNamedIndividual i2, 
            MolecularModelManager<Void> m3) throws UnknownIdentifierException {
        IRI partOfIRI = curieHandler.getIRI("BFO:0000050");
        final OWLObjectProperty partOf = model.getOWLDataFactory().getOWLObjectProperty(partOfIRI);
        m3.addFact(model, partOf, i1, i2, Collections.<OWLAnnotation>emptySet(), null);
    }

}
