package org.geneontology.minerva.gorules;
/**
 * rule:
name: "example 1"
description: An Example
sparql: >
SELECT ?a ?b ?c
WHERE {
  ?a ?b ?c
} LIMIT 20
fail_mode: hard
 */
public class GoRule {
	private String rule_id; 
	private String name;
	private String description;
	private String rule_implementation_type;
	private String rule_body;
	private String rule_path;
	private String fail_mode;
	

	public String getRule_id() {
		return rule_id;
	}
	public void setRule_id(String rule_id) {
		this.rule_id = rule_id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getFail_mode() {
		return fail_mode;
	}
	public void setFail_mode(String fail_mode) {
		this.fail_mode = fail_mode;
	}
	public String getRule_implementation_type() {
		return rule_implementation_type;
	}
	public void setRule_implementation_type(String rule_implementation_type) {
		this.rule_implementation_type = rule_implementation_type;
	}
	public String getRule_body() {
		return rule_body;
	}
	public void setRule_body(String rule_body) {
		this.rule_body = rule_body;
	}
	public String getRule_path() {
		return rule_path;
	}
	public void setRule_path(String rule_path) {
		this.rule_path = rule_path;
	}
	
}
