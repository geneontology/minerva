/**
 * 
 */
package org.geneontology.minerva.util;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

/**
 * @author bgood
 *
 */
public class JenaOwlTool {

	/**
	 * 
	 */
	public JenaOwlTool() {
		// TODO Auto-generated constructor stub
	}

	public static Model getJenaModel(OWLOntology ontology) {
		Model model = ModelFactory.createDefaultModel();

		try (PipedInputStream is = new PipedInputStream(); PipedOutputStream os = new PipedOutputStream(is)) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						ontology.getOWLOntologyManager().saveOntology(ontology, new TurtleDocumentFormat(), os);
						os.close();
					} catch (OWLOntologyStorageException | IOException e) {
						e.printStackTrace();
					}
				}
			}).start();
			model.read(is, null, "TURTLE");
			return model;
		} catch (Exception e) {
			throw new RuntimeException("Could not convert OWL API ontology to JENA API model.", e);
		}
	}

}
