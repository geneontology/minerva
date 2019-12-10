/**
 * 
 */
package org.geneontology.minerva.server.validation;

import java.io.File;

import org.apache.jena.rdf.model.Resource;
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

	public CurieHandler curieHandler;
	public final ExternalLookupService externalLookupService;
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
	public MinervaShexValidator(String shexpath, String goshapemappath, CurieHandler curieHandler, OWLReasoner tbox_reasoner, ExternalLookupService externalLookupService) throws Exception {
		super(shexpath, goshapemappath, tbox_reasoner);
		this.externalLookupService = externalLookupService;
	}

	/**
	 * @param shex_schema_file
	 * @param shex_map_file
	 * @throws Exception
	 */
	public MinervaShexValidator(File shex_schema_file, File shex_map_file, CurieHandler curieHandler, OWLReasoner tbox_reasoner, ExternalLookupService externalLookupService) throws Exception {
		super(shex_schema_file, shex_map_file, tbox_reasoner);
		this.externalLookupService = externalLookupService;
	}
	
	@Override
	public String getPreferredId(String node, Resource focus_node_resource) {
		if(curieHandler!=null) {
			node = curieHandler.getCuri(IRI.create(focus_node_resource.getURI()));
		}
		return node;
	}
	
	@Override
	public String getPreferredId(String node, IRI iri) {
		if(curieHandler!=null) {
			node = curieHandler.getCuri(iri);
		}
		return node;
	}

}
