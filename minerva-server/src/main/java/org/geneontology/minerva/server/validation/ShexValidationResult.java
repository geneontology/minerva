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

public class ShexValidationResult {
	public boolean model_is_valid; 
	public boolean model_is_consistent;
	public Map<String, Set<String>> node_shapes;
	public Map<String, Set<String>> node_types;
	public Map<String, String> node_report;
	public Map<String, Boolean> node_is_valid = new HashMap<String, Boolean>();
	public Map<String, Boolean> node_is_consistent;
	public String model_report;
	public String model_id;
	public String model_title;
	/**
	 * 
	 */
	public ShexValidationResult(Model model) {
		String q = "select ?cam ?title where {"
				+ "?cam <http://purl.org/dc/elements/1.1/title> ?title }";
		//	+ "?cam <"+DC.description.getURI()+"> ?title }";
		QueryExecution qe = QueryExecutionFactory.create(q, model);
		ResultSet results = qe.execSelect();
		if (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource id = qs.getResource("cam");
			Literal title = qs.getLiteral("title");
			model_id = id.getURI();
			model_title = title.getString();
		}
		qe.close();
		model_report = "shape id\tnode uri\tvalidation status\n";
	}

}
