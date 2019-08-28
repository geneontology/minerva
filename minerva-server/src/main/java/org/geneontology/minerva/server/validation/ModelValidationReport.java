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
	String id;
	boolean isConformant;
	String tracker;
	String rulefile;
	Set<Violation> violations;
	
	/**
	 * 
	 */
	public ModelValidationReport(String id, String tracker, String rulefile, boolean isConformant) {
		setId(id);
		setTracker(tracker);
		setConformant(isConformant);
		setRulefile(rulefile);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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
		return isConformant;
	}

	public void setConformant(boolean isConformant) {
		this.isConformant = isConformant;
	}

	public String getTracker() {
		return tracker;
	}

	public void setTracker(String tracker) {
		this.tracker = tracker;
	}

	public String getRulefile() {
		return rulefile;
	}

	public void setRulefile(String rulefile) {
		this.rulefile = rulefile;
	}
	
}
