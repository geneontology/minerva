/**
 * 
 */
package org.geneontology.minerva.gorules;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFTerm;
import org.apache.commons.rdf.rdf4j.RDF4J;
import org.apache.commons.rdf.simple.SimpleRDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

import org.apache.commons.io.IOUtils;
import org.apache.commons.rdf.api.Triple;
import org.apache.commons.rdf.jena.JenaRDF;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.log4j.Logger;
import org.eclipse.rdf4j.model.Graph;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.apache.commons.rdf.jena.JenaGraph;
//import fr.inria.lille.shexjava.graph.RDFGraph;
import fr.inria.lille.shexjava.schema.Label;
import fr.inria.lille.shexjava.schema.ShexSchema;
import fr.inria.lille.shexjava.schema.parsing.GenParser;
import fr.inria.lille.shexjava.util.Pair;
import fr.inria.lille.shexjava.validation.RecursiveValidation;
import fr.inria.lille.shexjava.validation.RefineValidation;
import fr.inria.lille.shexjava.validation.Status;
import fr.inria.lille.shexjava.validation.Typing;
import fr.inria.lille.shexjava.validation.ValidationAlgorithm;
import scala.collection.JavaConverters;

/**
 * @author bgood
 *
 */
public class GoRulesValidator {
	private static final Logger LOG = Logger.getLogger(GoRulesValidator.class);
	List<GoRule> go_rules;

	/**
	 * 
	 */
	public GoRulesValidator(String go_rule_yml_dir) {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		go_rules = new ArrayList<GoRule>(); 
		File rules_dir = new File(go_rule_yml_dir);
		for(File rule_file : rules_dir.listFiles()) {
			if(rule_file.getName().endsWith(".yml")) {
				try {
					GoRule rule = mapper.readValue(rule_file, GoRule.class);
					go_rules.add(rule);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	/*
	 * Run through all the rules given a WorkingMemory object (almost certainly the result of running Arachne) 
	 * Return a string for the moment that shows it did something.  
	 */
	public String executeRules(WorkingMemory wm) throws Exception {		

		LOG.info("Running go rules!\n");
		String report = "GO Rules report\n";
		Model model = ModelFactory.createDefaultModel();
		model.add(JavaConverters.setAsJavaSetConverter(wm.facts()).asJava().stream().map(t -> model.asStatement(Bridge.jenaFromTriple(t))).collect(Collectors.toList()));
		for(GoRule rule : go_rules) {
			if(rule.getRule_implementation_type().equals("sparql")) {
				QueryExecution qe = QueryExecutionFactory.create(rule.getRule_body(), model);
				report += "Running "+rule.getRule_implementation_type()+"  rule:"+rule.getRule_id()+" named "+rule.getDescription()+" with body \n"+rule.getRule_body()+"\n";
				ResultSet results = qe.execSelect();
				int n = 0;
				while (results.hasNext()) {
					QuerySolution qs = results.next();				
					n++;
				}
				report+= "Had "+n+" results from the query\n\n";
				qe.close();
			}else if(rule.getRule_implementation_type().equals("shex")) {
				report += "Running "+rule.getRule_implementation_type()+" rule:"+rule.getRule_id()+" named "+rule.getDescription()+" with body \n"+rule.getRule_body()+"\n";
				String shexpath = rule.getRule_path();
				ShexSchema schema = GenParser.parseSchema(new File(shexpath).toPath());
				String focus_node_iri = null;//e.g. "http://model.geneontology.org/R-HSA-140342/R-HSA-211196_R-HSA-211207";
				String shape_id = null;//e.g. "http://purl.org/pav/providedBy/S-integer";
				Typing results = validateShex(schema, model, focus_node_iri, shape_id);
				report += shexTypingToString(results); 
			}	

		}

		LOG.info("ran go rules!\n"+report);
		return report;
	}

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		String go_rule_yml_dir = "/Users/bgood/minerva/minerva-core/src/main/resources/org/geneontology/minerva/gorules/";
		String test_model_file = "/Users/bgood/Desktop/test/go_cams/reactome/reactome-homosapiens-Apoptosis_induced_DNA_fragmentation.ttl";
		GoRulesValidator grr = new GoRulesValidator(go_rule_yml_dir);
		
		Model test_model = ModelFactory.createDefaultModel() ;
		test_model.read(test_model_file) ;
		String report = "rules report\n";
		for(GoRule rule : grr.go_rules) {
			if(rule.getRule_implementation_type().equals("sparql")) {
				QueryExecution qe = QueryExecutionFactory.create(rule.getRule_body(), test_model);
				report += "Running "+rule.getRule_implementation_type()+"  rule:"+rule.getRule_id()+" named "+rule.getDescription()+" with body \n"+rule.getRule_body()+"\n";
				ResultSet results = qe.execSelect();
				int n = 0;
				while (results.hasNext()) {
					QuerySolution qs = results.next();				
					n++;
				}
				report+= "Had "+n+" results from the query\n\n";
				qe.close();
			}else if(rule.getRule_implementation_type().equals("shex")) {
				report += "Running "+rule.getRule_implementation_type()+" rule:"+rule.getRule_id()+" named "+rule.getDescription()+" with body \n"+rule.getRule_body()+"\n";
				String shexpath = rule.getRule_path();
				ShexSchema schema = GenParser.parseSchema(new File(shexpath).toPath());
				String focus_node_iri = null;//"http://model.geneontology.org/R-HSA-140342/R-HSA-211196_R-HSA-211207";
				String shape_id = null;//"http://purl.org/pav/providedBy/S-integer";
				Typing results = grr.validateShex(schema, test_model, focus_node_iri, shape_id);
				report += grr.shexTypingToString(results);
			}		
		}
		System.out.println(report);
	}

	public Typing validateShex(ShexSchema schema, Model jena_model, String focus_node_iri, String shape_id) throws Exception {
		Typing result = null;
		RDF rdfFactory = new SimpleRDF();
		JenaRDF jr = new JenaRDF();
		JenaGraph shexy_graph = jr.asGraph(jena_model);
		if(focus_node_iri!=null) {
			Label shape_label = new Label(rdfFactory.createIRI(shape_id));
			RDFTerm focus_node = rdfFactory.createIRI(focus_node_iri);
			//recursive only checks the focus node against the chosen shape.  
			RecursiveValidation shex_recursive_validator = new RecursiveValidation(schema, shexy_graph);
			shex_recursive_validator.validate(focus_node, shape_label);
			result = shex_recursive_validator.getTyping();
		}else {
			RefineValidation shex_refine_validator = new RefineValidation(schema, shexy_graph);
			//refine checks all nodes in the graph against all shapes in schema 
			shex_refine_validator.validate();	
			result = shex_refine_validator.getTyping();
		}
		return result;
	}
	
	public String shexTypingToString(Typing result) {
		String s = "";
		for(Pair<RDFTerm, Label> p : result.getStatusMap().keySet()) {
			Status r = result.getStatusMap().get(p);
			s=s+"node: "+p.one+"\tshape id: "+p.two+"\tresult: "+r.toString()+"\n";
		}
		return s;
	}
	
	
}
