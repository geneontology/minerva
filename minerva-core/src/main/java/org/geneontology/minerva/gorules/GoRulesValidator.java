/**
 * 
 */
package org.geneontology.minerva.gorules;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.log4j.Logger;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

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
	public String executeRules(WorkingMemory wm) throws InconsistentOntologyException, IOException {
		String report = "GO Rules report\n";
		Model model = ModelFactory.createDefaultModel();
		model.add(JavaConverters.setAsJavaSetConverter(wm.facts()).asJava().stream().map(t -> model.asStatement(Bridge.jenaFromTriple(t))).collect(Collectors.toList()));
		for(GoRule rule : go_rules) {
			QueryExecution qe = QueryExecutionFactory.create(rule.getSparql(), model);
			report += "Ran rule:"+rule.getRule_id()+" named "+rule.getDescription()+" with sparql \n"+rule.getSparql()+"\n";
			ResultSet results = qe.execSelect();
			int n = 0;
			while (results.hasNext()) {
				QuerySolution qs = results.next();				
				n++;
			}
			report+= "Had "+n+" results from the query\n\n";
			qe.close();
		}
		LOG.info("ran go rules!\n"+report);
		return report;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String go_rule_yml_dir = "/Users/bgood/minerva/minerva-server/src/main/resources/go_rules/";
		GoRulesValidator grr = new GoRulesValidator(go_rule_yml_dir);
		for(GoRule rule : grr.go_rules) {
			System.out.println(rule.getName()+"\n"+rule.getDescription()+"\n"+rule.getSparql()+"\n"+rule.getFail_mode());
		}
	}

}
