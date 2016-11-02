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

	@Test
	public void testUpdateImports() throws Exception {
		final OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		final OWLDataFactory f = m.getOWLDataFactory();
		
		// setup other import
		final IRI other = IRI.generateDocumentIRI();
		m.createOntology(other);
		
		// setup additional
		final IRI add1 = IRI.generateDocumentIRI();
		m.createOntology(add1);
		final IRI add2 = IRI.generateDocumentIRI();
		m.createOntology(add2);
		final Set<IRI> additional = new HashSet<IRI>();
		additional.add(add1);
		additional.add(add2);
		
		// setup tbox
		final IRI tboxIRI = IRI.generateDocumentIRI();
		m.createOntology(tboxIRI);
		
		// setup abox
		final OWLOntology abox = m.createOntology(IRI.generateDocumentIRI());
		// add initial imports to abox
		m.applyChange(new AddImport(abox, f.getOWLImportsDeclaration(other)));
		
		// update imports
		CoreMolecularModelManager.updateImports(abox, tboxIRI, additional);
		
		// check the resulting imports
		Set<OWLImportsDeclaration> declarations = abox.getImportsDeclarations();
		assertEquals(4, declarations.size());
		Set<IRI> declaredImports = new HashSet<IRI>();
		for (OWLImportsDeclaration importsDeclaration : declarations) {
			declaredImports.add(importsDeclaration.getIRI());
		}
		assertEquals(4, declaredImports.size());
		assertTrue(declaredImports.contains(tboxIRI));
		assertTrue(declaredImports.contains(add1));
		assertTrue(declaredImports.contains(add1));
		assertTrue(declaredImports.contains(other));
	}

	@Test(expected=UnparsableOntologyException.class)
	public void testSyntaxErrorModel() throws Exception {
		OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		final IRI modelFile = IRI.create(new File("src/test/resources/syntax-error/5667fdd400000802").getAbsoluteFile());
		CoreMolecularModelManager.loadOntologyDocumentSource(new IRIDocumentSource(modelFile), false, m);
	}
}
