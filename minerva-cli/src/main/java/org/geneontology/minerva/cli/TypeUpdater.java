/**
 * 
 */
package org.geneontology.minerva.cli;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
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
public class TypeUpdater {

	private OWLOntology neo;
	private OWLReasoner neo_reasoner;
	private OWLOntologyManager ontman;
	private OWLDataFactory df;
	private OWLClass infom;
	private OWLClass protein;
	/**
	 * @throws OWLOntologyCreationException 
	 * @throws IOException 
	 * 
	 */
	public TypeUpdater(String neo_file, String catalog) throws OWLOntologyCreationException, IOException {	
		ontman = OWLManager.createOWLOntologyManager();
		ontman.setIRIMappers(Sets.newHashSet(new owltools.io.CatalogXmlIRIMapper(catalog)));
		System.out.println("loading neo from "+neo_file);
		neo = ontman.loadOntologyFromOntologyDocument(new File(neo_file));
		System.out.println("making neo reasoner");
		OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
		neo_reasoner = reasonerFactory.createReasoner(neo);
		df = ontman.getOWLDataFactory();
		infom = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/CHEBI_33695"));
		protein = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/CHEBI_36080"));		
	}
	
	/**
	 * Update gene product typing for a directory
	 * @param input_dir
	 * @param output_dir
	 */
	public void runBatchUpdate(String input_dir, String output_dir) {
		File i = new File(input_dir);
		if(i.isDirectory()) {
			FileUtils.listFiles(i, null, true).forEach(file-> {
				if(file.getName().endsWith(".ttl")||file.getName().endsWith("owl")) {
					System.out.println("Updating " + file);
					try {
						OWLOntology go_cam = ontman.loadOntologyFromOntologyDocument(file);
						go_cam = addGeneProductTypes(go_cam);
						FileDocumentTarget outf = new FileDocumentTarget(new File(output_dir+file.getName()));
						ontman.setOntologyFormat(go_cam, new TurtleDocumentFormat());	
						ontman.saveOntology(go_cam,outf);
					} catch (OWLOntologyCreationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (OWLOntologyStorageException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} 
			});
		}
	}
	
	/**
	 * Add gene product types to genes in a go_cam model
	 * @param go_cam
	 * @return
	 */
	private OWLOntology addGeneProductTypes(OWLOntology go_cam) {		
		for(OWLNamedIndividual instance : go_cam.getIndividualsInSignature()) {
			for(OWLClassExpression i_type : EntitySearcher.getTypes(instance, go_cam)) {				
				NodeSet<OWLClass> types = neo_reasoner.getSuperClasses(i_type, false);			
				if(types.containsEntity(protein)) {		
					OWLClassAssertionAxiom addtype = df.getOWLClassAssertionAxiom(protein, instance);
					ontman.addAxiom(go_cam, addtype);
				}else if(types.containsEntity(infom)) {		
					OWLClassAssertionAxiom addtype = df.getOWLClassAssertionAxiom(infom, instance);
					ontman.addAxiom(go_cam, addtype);
				}
			}
		}
		return go_cam;
	}


}
