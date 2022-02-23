package org.geneontology.minerva.validation.pipeline;

import com.google.gson.annotations.SerializedName;
import org.geneontology.minerva.validation.ValidationResultSet;

import java.util.Set;

public class ErrorMessage {
    String level;
    @SerializedName("model-id")
    String model_id;
    String type = "Violates GO Rule";
    String obj = "";
    Set<String> taxa;
    String message;
    int rule;
    ValidationResultSet explanations;

    public ErrorMessage(String level, String model_id, Set<String> taxa, String message, int rule) {
        this.level = level;
        this.model_id = model_id;
        this.taxa = taxa;
        this.message = message;
        this.rule = rule;
    }

    public ValidationResultSet getExplanations() {
        return explanations;
    }

    public void setExplanations(ValidationResultSet explanations) {
        this.explanations = explanations;
    }

}
