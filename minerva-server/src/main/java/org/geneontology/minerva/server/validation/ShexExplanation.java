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
public class ShexExplanation {
	String shape_id;
	Set<ShexConstraint> constraints;
	/**
	 * 
	 */
	public ShexExplanation() {
		constraints = new HashSet<ShexConstraint>();
	}
	public String getShape_id() {
		return shape_id;
	}
	public void setShape_id(String shape_id) {
		this.shape_id = shape_id;
	}
	public Set<ShexConstraint> getConstraints() {
		return constraints;
	}
	public void setConstraints(Set<ShexConstraint> constraints) {
		this.constraints = constraints;
	}
	public void addConstraint(ShexConstraint constraint) {
		this.constraints.add(constraint);
	}
}
