/**
 * 
 */
package org.geneontology.minerva.server.validation;

import java.io.File;

import org.apache.jena.rdf.model.Resource;
import org.geneontology.minerva.BlazegraphOntologyManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.validation.ShexValidator;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

/**
 * @author bgood
 *
 */
public class MinervaShexValidator extends ShexValidator {

	boolean active = true;
	
	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	/**
	 * @param shexpath
	 * @param goshapemappath
	 * @throws Exception
	 */
	public MinervaShexValidator(String shexpath, String goshapemappath, CurieHandler curieHandler, BlazegraphOntologyManager go_lego) throws Exception {
		super(shexpath, goshapemappath, go_lego, curieHandler);
	}
	/**
	 * @param shex_schema_file
	 * @param shex_map_file
	 * @throws Exception
	 */
	public MinervaShexValidator(File shex_schema_file, File shex_map_file, CurieHandler curieHandler, BlazegraphOntologyManager go_lego) throws Exception {
		super(shex_schema_file, shex_map_file, go_lego, curieHandler);
	}
	

}
