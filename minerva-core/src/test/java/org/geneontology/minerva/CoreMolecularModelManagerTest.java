package org.geneontology.minerva;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.geneontology.minerva.CoreMolecularModelManager;
import org.geneontology.minerva.validation.ShexValidationReport;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.IRIDocumentSource;
import org.semanticweb.owlapi.io.UnparsableOntologyException;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.RemoveImport;
import org.semanticweb.owlapi.model.RemoveOntologyAnnotation;

public class CoreMolecularModelManagerTest {


//	@Test(expected=UnparsableOntologyException.class)
	public void testSyntaxErrorModel() throws Exception {
		OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		final IRI modelFile = IRI.create(new File("src/test/resources/syntax-error/5667fdd400000802").getAbsoluteFile());
		CoreMolecularModelManager.loadOntologyDocumentSource(new IRIDocumentSource(modelFile), false, m);
	}

	@Test
	public void testCleanOntology() throws OWLOntologyCreationException {
		OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		File directory = new File("src/test/resources/broken-ontologies/");
		boolean ignore_imports = true;
		if(directory.isDirectory()) {
			for(File file : directory.listFiles()) {
				if(file.getName().endsWith("ttl")) {
					System.out.println("fixing "+file.getAbsolutePath());
					final IRI modelFile = IRI.create(file.getAbsoluteFile());
					OWLOntology o;
					try {
						o = CoreMolecularModelManager.loadOntologyDocumentSource(new IRIDocumentSource(modelFile), ignore_imports, m);
						//in case the reader was confused by the missing import, fix declarations
						o = CoreMolecularModelManager.fixBrokenObjectPropertiesAndAxioms(o);
						//check on what came in
						int obj_prop_assertions_in = o.getAxiomCount(AxiomType.OBJECT_PROPERTY_ASSERTION);
						int anno_prop_assertions_in = o.getAxiomCount(AxiomType.ANNOTATION_ASSERTION);
						String title_in = getTitle(o);
						//clean the model
						OWLOntology cleaned_ont = CoreMolecularModelManager.removeDeadAnnotationsAndImports(o);						
						//saved the blessed ontology
						OWLDocumentFormat owlFormat = new TurtleDocumentFormat();
						m.setOntologyFormat(cleaned_ont, owlFormat);
						String cleaned_ont_file = "src/test/resources/broken-ontologies/fixed/fixed_"+file.getName();
						System.out.println("Saving "+title_in+" from file "+cleaned_ont_file);
						try {
							m.saveOntology(cleaned_ont, new FileOutputStream(cleaned_ont_file));
						} catch (OWLOntologyStorageException | FileNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						//read the ontology back in and check that it makes sense
						File newfile = new File(cleaned_ont_file);
						final IRI cleaned_iri = IRI.create(newfile.getAbsoluteFile());
						OWLOntology cleaned = CoreMolecularModelManager.loadOntologyDocumentSource(new IRIDocumentSource(cleaned_iri), false, m);
						//no imports
						Set<OWLImportsDeclaration> cleaned_imports = cleaned.getImportsDeclarations();
						assertTrue("found an import where we shouldn't in "+cleaned_ont_file, cleaned_imports.size()==0);
						//same number of object prop and annotation assertions
						int obj_prop_assertions_out = cleaned.getAxiomCount(AxiomType.OBJECT_PROPERTY_ASSERTION);
						int anno_prop_assertions_out = cleaned.getAxiomCount(AxiomType.ANNOTATION_ASSERTION);
						assertTrue("lost some object property assertions in "+cleaned_ont_file, obj_prop_assertions_in==obj_prop_assertions_out);
						assertTrue("lost some annotation property assertions in "+cleaned_ont_file, anno_prop_assertions_in==anno_prop_assertions_out);
						//check on ontology annotatins
						String title_out = getTitle(cleaned);
						assertTrue("lost some ontology annotations in "+cleaned_ont_file, title_in.equals(title_out));
					} catch (OWLOntologyCreationException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
		}
	}
	
	private String getTitle(OWLOntology ont) {
		String title = "";
		for(OWLAnnotation anno : ont.getAnnotations()) {
			if(anno.getProperty().getIRI().toString().equals("http://purl.org/dc/elements/1.1/title")) {
				title = anno.getValue().asLiteral().get().getLiteral();
				break;
			}
		}
		return title;
	}

}
