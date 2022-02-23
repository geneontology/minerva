/**
 *
 */
package org.geneontology.minerva.validation;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

import java.util.Map;
import java.util.Set;

/**
 * @author bgood
 *
 */
public class ModelValidationResult {
    boolean model_is_valid;
    boolean model_is_consistent;
    Map<String, Set<String>> node_shapes;
    Map<String, Set<String>> node_types;
    Map<String, String> node_report;
    Map<String, Boolean> node_is_valid;
    Map<String, Boolean> node_is_consistent;
    String model_report;
    String model_id;
    String model_title;

    /**
     *
     */
    public ModelValidationResult(Model model) {
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
