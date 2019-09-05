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
	
	public Map<String, Set<String>> node_matched_shapes = new HashMap<String, Set<String>>();
	public Map<String, String> node_report = new HashMap<String, String>();
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
	}
	
	public String getAsText() {
		String report = "report type id = "+report_type_id+"\nrulefile = "+rulefile+"\ntracker = "+tracker+"\n";
		report+="Model tested name = "+model_title+"\n";
		if(conformant) {
			report+="No errors detected";
			return report;
		}
		report+=getViolations().size()+" noncomformant nodes detected:\n";
		for(Violation violation : getViolations()) {
			report+="node: "+violation.getNode()+" ";
			ShexViolation sv = (ShexViolation) violation;
			for(ShexExplanation e : sv.getExplanations()) {
				report+="was expected to match shape: "+e.shape;
				report+=" but did not fit the following constraints:";
				for(ShexConstraint c : e.getConstraints()) {
					report+="\n\tthe objects of assertions made with "+c.getProperty()+" should be nodes that fit the one of these shapes: ";
					report+="\n\t\t"+c.getIntended_range_shapes(); 
					report+="\n\t\tbut, sadly, the object "+c.getObject()+" of one such assertion emanating from the failing node here did not.\n";
				}
			}  
		}
		return report;
	}
}
