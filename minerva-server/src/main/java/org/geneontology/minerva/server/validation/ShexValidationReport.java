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

import com.google.gson.annotations.SerializedName;


/**
 * @author bgood
 *
 */

public class ShexValidationReport extends ModelValidationReport{
	@SerializedName("report-type")
	public static final String report_type_id = "SHEX_CORE_SCHEMA";
	public static final String tracker = "https://github.com/geneontology/go-shapes/issues";
	
	@SerializedName("rule-file")
	public static final String rulefile = "https://github.com/geneontology/go-shapes/blob/master/shapes/go-cam-shapes.shex";
	
	@SerializedName("node-matched-shapes")
	public Map<String, Set<String>> node_matched_shapes = new HashMap<String, Set<String>>();
	/**
	 * 
	 */
	public ShexValidationReport(String id, Model model) {
		super(id, tracker, rulefile);
	}
	
	public String getAsText() {
		String report = "report type id = "+report_type_id+"\nrulefile = "+rulefile+"\ntracker = "+tracker+"\n";
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
