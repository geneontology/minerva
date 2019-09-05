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
	String shape;
	Set<ShexConstraint> constraints;
	/**
	 * 
	 */
	public ShexExplanation() {
		constraints = new HashSet<ShexConstraint>();
	}
	public String getShape() {
		return shape;
	}
	public void setShape(String shape) {
		this.shape = shape;
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
