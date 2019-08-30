/**
 * 
 */
package org.geneontology.minerva.server.validation;

import java.util.HashSet;
import java.util.Set;

/**
 * @author bgood
 *
 */
public class ModelValidationReport {
	final String id;
	boolean conformant;
	final String tracker;
	final String rulefile;
	Set<Violation> violations;
	
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
		if(this.violations==null) {
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
	
}
