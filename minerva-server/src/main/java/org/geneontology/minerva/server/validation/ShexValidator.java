/**
 * 
 */
package org.geneontology.minerva.server.validation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFTerm;
import org.apache.commons.rdf.jena.JenaGraph;
import org.apache.commons.rdf.jena.JenaRDF;
import org.apache.commons.rdf.simple.SimpleRDF;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import fr.inria.lille.shexjava.schema.Label;
import fr.inria.lille.shexjava.schema.ShexSchema;
import fr.inria.lille.shexjava.schema.parsing.GenParser;
import fr.inria.lille.shexjava.util.Pair;
import fr.inria.lille.shexjava.validation.RecursiveValidation;
import fr.inria.lille.shexjava.validation.Status;
import fr.inria.lille.shexjava.validation.Typing;

/**
 * @author bgood
 *
 */
public class ShexValidator {
	public ShexSchema schema;
	public Map<String, String> GoQueryMap;
	public OWLReasoner tbox_reasoner;
	public static final String endpoint = "http://rdf.geneontology.org/blazegraph/sparql";

	/**
	 * @throws Exception 
	 * 
	 */
	public ShexValidator(String shexpath, String goshapemappath) throws Exception {
		schema = GenParser.parseSchema(new File(shexpath).toPath());
		GoQueryMap = makeGoQueryMap(goshapemappath);
	}

	public ShexValidator(File shex_schema_file, File shex_map_file) throws Exception {
		schema = GenParser.parseSchema(shex_schema_file.toPath());
		GoQueryMap = makeGoQueryMap(shex_map_file.getAbsolutePath());
	}

	public static Map<String, String> makeGoQueryMap(String shapemap_file) throws IOException{ 
		Map<String, String> shapelabel_sparql = new HashMap<String, String>();
		BufferedReader reader = new BufferedReader(new FileReader(shapemap_file));
		String line = reader.readLine();
		String all = line;
		while(line!=null) {
			all+=line;
			line = reader.readLine();			
		}
		reader.close();
		String[] maps = all.split(",");
		for(String map : maps) {
			String sparql = StringUtils.substringBetween(map, "'", "'");
			sparql = sparql.replace("a/", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type> ?c . ?c ");
			String[] shapemaprow = map.split("@");
			String shapelabel = shapemaprow[1];
			shapelabel = shapelabel.replace(">", "");
			shapelabel = shapelabel.replace("<", "");
			shapelabel = shapelabel.trim();
			shapelabel_sparql.put(shapelabel, sparql);
		}
		return shapelabel_sparql;
	}

	public ShexValidationReport runShapeMapValidation(Model test_model, boolean stream_output) throws Exception {
		ShexValidationReport r = new ShexValidationReport(null, test_model);	
		RDF rdfFactory = new SimpleRDF();
		JenaRDF jr = new JenaRDF();
		//this shex implementation likes to use the commons JenaRDF interface, nothing exciting here
		JenaGraph shexy_graph = jr.asGraph(test_model);
		//recursive only checks the focus node against the chosen shape.  
		RecursiveValidation shex_recursive_validator = new RecursiveValidation(schema, shexy_graph);
		//for each shape in the query map (e.g. MF, BP, CC, etc.)
		for(String shapelabel : GoQueryMap.keySet()) {
			Label shape_label = new Label(rdfFactory.createIRI(shapelabel));
			//get the nodes in this model that SHOULD match the shape
			Set<String> focus_nodes = getFocusNodesBySparql(test_model, GoQueryMap.get(shapelabel));
			for(String focus_node_iri : focus_nodes) {
				RDFTerm focus_node = rdfFactory.createIRI(focus_node_iri);
				//check the node against the intended shape
				shex_recursive_validator.validate(focus_node, shape_label);
				Typing typing = shex_recursive_validator.getTyping();
				//capture the result
				Status status = typing.getStatus(focus_node, shape_label);
				if(status.equals(Status.CONFORMANT)) {
					Set<String> shape_ids = r.node_matched_shapes.get(focus_node_iri);
					if(shape_ids==null) {
						shape_ids = new HashSet<String>();
					}
					shape_ids.add(shapelabel);
					r.node_matched_shapes.put(focus_node_iri, shape_ids);
				}else if(status.equals(Status.NONCONFORMANT)) {
					Set<String> shape_ids = r.node_unmatched_shapes.get(focus_node_iri);
					if(shape_ids==null) {
						shape_ids = new HashSet<String>();
					}
					shape_ids.add(shapelabel);
					r.node_unmatched_shapes.put(focus_node_iri, shape_ids);
					//all tested here should match
					r.node_is_valid.put(focus_node_iri, false);
					//if any of these tests is invalid, the model is invalid
					String error = focus_node_iri+" did not match "+shapelabel;
					r.node_report.put(focus_node_iri, error);
					r.model_is_valid = false;
					if(stream_output) {
						System.out.println("Invalid model:"+r.model_title+"\n\t"+error);
					}
					r.model_report += error+"\n";
					//implementing a start on a generic violation report structure here.. maybe replacing above
					//somewhat redundant now. 
					ShexViolation violation = new ShexViolation(focus_node_iri);
					violation.setCommentary(error);
					ShexExplanation explanation = new ShexExplanation();
					explanation.setShape_id(shapelabel);
						ShexConstraint constraint = new ShexConstraint("unmatched_property_id_1", "Range of property id_1");
						explanation.addConstraint(constraint);
						violation.addExplanation(explanation);
						
						ShexConstraint constraint2 = new ShexConstraint("unmatched_property_id_2", "Range of property id_2");
						explanation.addConstraint(constraint2);
						violation.addExplanation(explanation);
					r.addViolation(violation);
				}else if(status.equals(Status.NOTCOMPUTED)) {
					//if any of these are not computed, there is a problem
					String error = focus_node_iri+" was not tested against "+shapelabel;
					r.node_report.put(focus_node_iri, error);
					if(stream_output) {
						System.out.println("Invalid model:"+r.model_title+"\n\t"+error);
					}
					r.model_report += error+"\n";
				}
			}
		}
		return r;
	}
/**
 * 		for(String bad_node : result.node_is_valid.keySet()) {
				if(!(result.node_is_valid.get(bad_node))) {
					ShexViolation violation = new ShexViolation(bad_node);
					violation.setCommentary("Some explanatory text would go here");
					ShexExplanation explanation = new ShexExplanation();
					explanation.setShape_id("the shape id that this node should fit here");
					ShexConstraint constraint = new ShexConstraint("unmatched_property_id", "Range of property id");
					explanation.addConstraint(constraint);
					violation.addExplanation(explanation);
					validation_report.addViolation(violation);
				}
			}

 *
 */
	public static Set<String> getFocusNodesBySparql(Model model, String sparql){
		Set<String> nodes = new HashSet<String>();
		QueryExecution qe = QueryExecutionFactory.create(sparql, model);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource node = qs.getResource("x");
			nodes.add(node.getURI());
		}
		qe.close();
		return nodes;
	}

	public Model enrichSuperClasses(Model model) {
		String getOntTerms = 
				"PREFIX owl: <http://www.w3.org/2002/07/owl#> "
						+ "SELECT DISTINCT ?term " + 
						"        WHERE { " + 
						"        ?ind a owl:NamedIndividual . " + 
						"        ?ind a ?term . " + 
						"        FILTER(?term != owl:NamedIndividual)" + 
						"        FILTER(isIRI(?term)) ." + 
						"        }";
		String terms = "";
		Set<String> term_set = new HashSet<String>();
		try{
			QueryExecution qe = QueryExecutionFactory.create(getOntTerms, model);
			ResultSet results = qe.execSelect();

			while (results.hasNext()) {
				QuerySolution qs = results.next();
				Resource term = qs.getResource("term");
				terms+=("<"+term.getURI()+"> ");
				term_set.add(term.getURI());
			}
			qe.close();
		} catch(QueryParseException e){
			e.printStackTrace();
		}
		if(tbox_reasoner!=null) {
			for(String term : term_set) {
				OWLClass c = 
						tbox_reasoner.
						getRootOntology().
						getOWLOntologyManager().
						getOWLDataFactory().getOWLClass(IRI.create(term));
				Resource child = model.createResource(term);
				Set<OWLClass> supers = tbox_reasoner.getSuperClasses(c, false).getFlattened();
				for(OWLClass parent_class : supers) {
					Resource parent = model.createResource(parent_class.getIRI().toString());
					model.add(model.createStatement(child, org.apache.jena.vocabulary.RDFS.subClassOf, parent));
					model.add(model.createStatement(child, org.apache.jena.vocabulary.RDF.type, org.apache.jena.vocabulary.OWL.Class));
				}
			}
		}else {
			String superQuery = ""
					+ "PREFIX owl: <http://www.w3.org/2002/07/owl#> "
					+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
					+ "CONSTRUCT { " + 
					"        ?term rdfs:subClassOf ?superclass ." + 
					"        ?term a owl:Class ." + 
					"        }" + 
					"        WHERE {" + 
					"        VALUES ?term { "+terms+" } " + 
					"        ?term rdfs:subClassOf* ?superclass ." + 
					"        FILTER(isIRI(?superclass)) ." + 
					"        }";

			Query query = QueryFactory.create(superQuery); 
			try ( 
					QueryExecution qexec = QueryExecutionFactory.sparqlService(endpoint, query) ) {
				qexec.execConstruct(model);
				qexec.close();
			} catch(QueryParseException e){
				e.printStackTrace();
			}
		}
		return model;
	}

	public ModelValidationReport createValidationReport(Model model) throws Exception {
		boolean stream_output_for_debug = true;
		ShexValidationReport validation_report = runShapeMapValidation(model, stream_output_for_debug);
		return validation_report;
	}
}
