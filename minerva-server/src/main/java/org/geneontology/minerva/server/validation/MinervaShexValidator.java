/**
 * 
 */
package org.geneontology.minerva.server.validation;

import java.io.File;

import org.apache.jena.rdf.model.Resource;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.shapes.ShexValidator;
import org.semanticweb.owlapi.model.IRI;

/**
 * @author bgood
 *
 */
public class MinervaShexValidator extends ShexValidator {

	public CurieHandler curieHandler;
	
	/**
	 * @param shexpath
	 * @param goshapemappath
	 * @throws Exception
	 */
	public MinervaShexValidator(String shexpath, String goshapemappath, CurieHandler curieHandler) throws Exception {
		super(shexpath, goshapemappath);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param shex_schema_file
	 * @param shex_map_file
	 * @throws Exception
	 */
	public MinervaShexValidator(File shex_schema_file, File shex_map_file, CurieHandler curieHandler) throws Exception {
		super(shex_schema_file, shex_map_file);
		// TODO Auto-generated constructor stub
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
