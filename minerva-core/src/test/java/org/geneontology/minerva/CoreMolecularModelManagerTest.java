package org.geneontology.minerva;

import static org.junit.Assert.*;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.geneontology.minerva.CoreMolecularModelManager;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.IRIDocumentSource;
import org.semanticweb.owlapi.io.UnparsableOntologyException;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

public class CoreMolecularModelManagerTest {


	@Test(expected=UnparsableOntologyException.class)
	public void testSyntaxErrorModel() throws Exception {
		OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		final IRI modelFile = IRI.create(new File("src/test/resources/syntax-error/5667fdd400000802").getAbsoluteFile());
		CoreMolecularModelManager.loadOntologyDocumentSource(new IRIDocumentSource(modelFile), false, m);
	}
}
