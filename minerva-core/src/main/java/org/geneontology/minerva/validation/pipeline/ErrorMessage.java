package org.geneontology.minerva.validation.pipeline;

import com.google.gson.annotations.SerializedName;

public class ErrorMessage {
	String level;
	@SerializedName("model-id")
	String model_id;
	String type = "Violates GO Rule";
	String obj = "";
	String taxon = "";
	String message;
	int rule;
	public ErrorMessage(String level, String model_id, String taxon, String message, int rule) {
		this.level = level;
		this.model_id = model_id;
		this.taxon = taxon;
		this.message = message;
		this.rule = rule;
	}
}
