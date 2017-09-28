package org.geneontology.minerva;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.geneontology.minerva.UndoAwareMolecularModelManager.UndoMetadata;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
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
        BlazegraphMolecularModelManager m3 = new BlazegraphMolecularModelManager<>(g, "go", "/tmp/journal.jrnl", "/tmp");
        
        String userId = "test-user-id";
        ModelContainer model = m3.generateBlankModel(null);
        // GO:0001158 ! enhancer sequence-specific DNA binding
        //OWLNamedIndividual bindingIdividual = m3.createIndividual(model.getModelId(), "GO:0001158", null, null);

    }

}
