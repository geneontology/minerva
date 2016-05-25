package org.geneontology.minerva;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.CurieMappings;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.geneontology.minerva.lookup.TableLookupService;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.ManchesterSyntaxDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;

/**
 * Tests for {@link ModelReaderHelper} and {@link ModelWriterHelper}.
 */
public class ModelDecorationTest {

	@Test
	public void testDecorate1() throws Exception {
		OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		OWLDataFactory f = m.getOWLDataFactory();
		
		CurieMappings defaultMappings = DefaultCurieHandler.getMappings();
		CurieMappings localMappings = new CurieMappings.SimpleCurieMappings(Collections.singletonMap("http://testmodel.geneontology.org/","testmodel"));
		CurieHandler curieHandler = new MappedCurieHandler(defaultMappings, localMappings);
		
		OWLOntology model = m.createOntology(IRI.create("http://testmodel.geneontology.org/0001"));
		
		// add class without a label or id
		IRI cIRI = IRI.create("http://purl.obolibrary.org/obo/GO_0001");
		OWLClass c = f.getOWLClass(cIRI);
		m.addAxiom(model, f.getOWLDeclarationAxiom(c));
		
		// add individual using the class as type
		OWLNamedIndividual i = f.getOWLNamedIndividual(IRI.create("http://testmodel.geneontology.org/0001/0001"));
		m.addAxiom(model, f.getOWLDeclarationAxiom(i));
		m.addAxiom(model, f.getOWLClassAssertionAxiom(c, i));
		
		
		List<LookupEntry> testEntries = new ArrayList<LookupEntry>();
		testEntries.add(new LookupEntry(cIRI, "TEST CLASS 1", "Class", null));
		ExternalLookupService lookup = new TableLookupService(testEntries);
		
		int originalAxiomCount = model.getAxiomCount();
		int originalAnnotationCount = model.getAnnotations().size();
		
		ModelWriterHelper w = new ModelWriterHelper(curieHandler, lookup);
		List<OWLOntologyChange> changes = w.handle(model);
		
		// model annotations
		// id, label, and json-model for cls
		assertEquals(8, changes.size()); // 4 + 4 declarations
		
		assertEquals(6+originalAxiomCount, model.getAxiomCount());
		final Set<OWLAnnotation> modelAnnotationsAfter = model.getAnnotations();
		assertEquals(2+originalAnnotationCount, modelAnnotationsAfter.size());
		
		
		//System.out.println(render(model));
		
		ModelReaderHelper.INSTANCE.filter(model);
		
		assertEquals(originalAxiomCount+4, model.getAxiomCount()); // declarations are fine
		assertEquals(originalAnnotationCount, model.getAnnotations().size());
		
		//System.out.println(render(model));
	}
	
	static String render(OWLOntology o) throws Exception {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		o.getOWLOntologyManager().saveOntology(o, new ManchesterSyntaxDocumentFormat(), outputStream);
		outputStream.flush();
		outputStream.close();
		String s = outputStream.toString();
		return s;
	}
}
