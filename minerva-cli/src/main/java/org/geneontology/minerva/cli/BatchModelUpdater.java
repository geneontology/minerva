/**
 * 
 */
package org.geneontology.minerva.cli;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.geneontology.minerva.BlazegraphMolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.MappedCurieHandler;
import org.openrdf.repository.RepositoryException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.FileDocumentTarget;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;

import com.google.common.collect.Sets;

/**
 * @author bgood
 *
 */
public class BatchModelUpdater {

	public static void main(String[] args) {
		///Users/benjamingood/noctua_config/dev-blazegraph.jnl 
		String input_journal = "/Users/benjamingood/noctua_config/blazegraph-master.jnl";
		try {
			addTaxonMetaData(input_journal);
		} catch (OWLOntologyCreationException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	private static void addTaxonMetaData(String go_cam_journal) throws OWLOntologyCreationException, IOException {
		String go_lego_journal_file = "/tmp/blazegraph.jnl";
		String modelIdPrefix = "http://model.geneontology.org/";
		OWLOntology dummy = OWLManager.createOWLOntologyManager().createOntology(IRI.create("http://example.org/dummy"));
		CurieHandler curieHandler = new MappedCurieHandler();
		BlazegraphMolecularModelManager<Void> m3 = new BlazegraphMolecularModelManager<>(dummy, curieHandler, modelIdPrefix, go_cam_journal, null, go_lego_journal_file);					
		m3.updateTaxonMetadata();
		return;
	}
	
}
