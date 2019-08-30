/**
 * 
 */
package org.geneontology.minerva.server.validation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

/**
 * @author bgood
 *
 */

public class ShexValidationReport extends ModelValidationReport{
	public static final String report_type_id = "SHEX_CORE_SCHEMA";
	public static final String tracker = "https://github.com/geneontology/go-shapes/issues";
	public static final String rulefile = "https://github.com/geneontology/go-shapes/blob/master/shapes/go-cam-shapes.shex";
	
	public boolean model_is_valid; 
	public Map<String, Set<String>> node_matched_shapes = new HashMap<String, Set<String>>();
	public Map<String, Set<String>> node_unmatched_shapes = new HashMap<String, Set<String>>();
	public Map<String, String> node_report = new HashMap<String, String>();
	public Map<String, Boolean> node_is_valid = new HashMap<String, Boolean>();
	public String model_report = "";
	public String model_title = "";
	/**
	 * 
	 */
	public ShexValidationReport(String id, Model model) {
		super(id, tracker, rulefile);
		String q = "select ?cam ?title where {"
				+ "?cam <http://purl.org/dc/elements/1.1/title> ?title }";
		//	+ "?cam <"+DC.description.getURI()+"> ?title }";
		QueryExecution qe = QueryExecutionFactory.create(q, model);
		ResultSet results = qe.execSelect();
		if (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource model_id_resource = qs.getResource("cam");
			Literal title = qs.getLiteral("title");
			if(id==null) {
				id = model_id_resource.getURI();
			}
			model_title = title.getString();
		}
		qe.close();
		model_report = "shape id\tnode uri\tvalidation status\n";
	}

}
