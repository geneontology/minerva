/**
 *
 */
package org.geneontology.minerva.validation;

import com.google.gson.annotations.SerializedName;

import java.util.HashSet;
import java.util.Set;

/**
 * @author bgood
 *
 */
public class ModelValidationReport {
    final String id;

    @SerializedName("is-conformant")
    boolean conformant;
    final String tracker;

    @SerializedName("rule-file")
    final String rulefile;
    Set<Violation> violations;
    @SerializedName("error-message")
    String error_message;

    /**
     *
     */
    public ModelValidationReport(String id, String tracker, String rulefile) {
        this.id = id;
        this.tracker = tracker;
        this.rulefile = rulefile;
    }

    public String getId() {
        return id;
    }

    public Set<Violation> getViolations() {
        return violations;
    }

    public void setViolations(Set<Violation> violations) {
        this.violations = violations;
    }

    public void addViolation(Violation violation) {
        if (this.violations == null) {
            this.violations = new HashSet<Violation>();
        }
        this.violations.add(violation);
    }

    public boolean isConformant() {
        return conformant;
    }

    public void setConformant(boolean conformant) {
        this.conformant = conformant;
    }

    public String getTracker() {
        return tracker;
    }

    public String getRulefile() {
        return rulefile;
    }

    public String getError_message() {
        return error_message;
    }

    public void setError_message(String error_message) {
        this.error_message = error_message;
    }

    public void addViolations(Set<ShexViolation> violations) {
        if (this.violations == null) {
            this.violations = new HashSet<Violation>();
        }
        this.violations.addAll(violations);
    }
}
