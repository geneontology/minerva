package org.geneontology.minerva.validation.pipeline;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

public class BatchPipelineValidationReport {
	@Expose(serialize = false) 
	final static String owl_message = "GORULE:0000019: Model is logically inconsistent";
	@Expose(serialize = false) 
	final static int owl_rule = 19;
	@Expose(serialize = false) 
	final static String owl_rule_string = "gorule-0000019";
	@Expose(serialize = false) 
	final static String shex_message = "GORULE:0000056: Model does not validate against shex schema";
	@Expose(serialize = false) 
	final static int shex_rule = 56;
	@Expose(serialize = false) 
	final static String shex_rule_string = "gorule-0000056";
	
	Set<String> taxa;
	int number_of_models;
	int number_of_models_in_error;
	int number_of_correct_models;
	Map<String, Set<ErrorMessage>> messages;
		
	
	
	public BatchPipelineValidationReport() {
		messages = new HashMap<String, Set<ErrorMessage>>();
	}

	public int getNumber_of_models() {
		return number_of_models;
	}

	public void setNumber_of_models(int number_of_models) {
		this.number_of_models = number_of_models;
	}

	public int getNumber_of_models_in_error() {
		return number_of_models_in_error;
	}

	public void setNumber_of_models_in_error(int number_of_models_in_error) {
		this.number_of_models_in_error = number_of_models_in_error;
	}

	public int getNumber_of_correct_models() {
		return number_of_correct_models;
	}

	public void setNumber_of_correct_models(int number_of_correct_models) {
		this.number_of_correct_models = number_of_correct_models;
	}

	public Map<String, Set<ErrorMessage>> getMessages() {
		return messages;
	}

	public void setMessages(Map<String, Set<ErrorMessage>> messages) {
		this.messages = messages;
	}

	public static String getOwlMessage() {
		return owl_message;
	}

	public static int getOwlRule() {
		return owl_rule;
	}

	public static String getOwlRuleString() {
		return owl_rule_string;
	}

	public static String getShexMessage() {
		return shex_message;
	}

	public static int getShexRule() {
		return shex_rule;
	}

	public static String getShexRuleString() {
		return shex_rule_string;
	}

	public void setTaxa(Set<String> taxa) {
		this.taxa = taxa;		
	}

	public Set<String> getTaxa() {
		return taxa;
	}
	
	public static void main(String[] args) {
		GsonBuilder builder = new GsonBuilder();		 
		Gson gson = builder.setPrettyPrinting().create();
		BatchPipelineValidationReport report = new BatchPipelineValidationReport();
		report.number_of_models = 1;
		report.number_of_correct_models = 0;
		report.number_of_models_in_error = 1;
		report.messages = new HashMap<String, Set<ErrorMessage>>();		
		Set<ErrorMessage> owl_errors = new HashSet<ErrorMessage>();
		String level = "ERROR";
		String model_id = "DEMO model:007";
		Set<String> taxa = new HashSet<String>();
		String taxon = "9606";
		taxa.add(taxon);
		String message = owl_message;
		int rule = owl_rule;
		ErrorMessage owl = new ErrorMessage(level, model_id, taxa, message, rule);
		owl_errors.add(owl);
		report.messages.put(owl_rule_string, owl_errors);
		
		Set<ErrorMessage> shex_errors = new HashSet<ErrorMessage>();
		level = "WARNING";
		model_id = "DEMO model:007";
		message = shex_message;
		rule = shex_rule;
		ErrorMessage shex = new ErrorMessage(level, model_id, taxa, message, rule);
		shex_errors.add(shex);
		report.messages.put(shex_rule_string, shex_errors);
		
		String json = gson.toJson(report);
		System.out.println(json);
	}

	
}
