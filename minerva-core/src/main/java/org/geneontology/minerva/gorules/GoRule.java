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
	private String sparql;
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
	public String getSparql() {
		return sparql;
	}
	public void setSparql(String sparql) {
		this.sparql = sparql;
	}
	public String getFail_mode() {
		return fail_mode;
	}
	public void setFail_mode(String fail_mode) {
		this.fail_mode = fail_mode;
	}

}
