/**
 *
 */
package org.geneontology.minerva.validation;

import java.util.HashSet;
import java.util.Set;

/**
 * @author bgood
 *
 */
public class ShexExplanation {
    String shape;
    Set<ShexConstraint> constraints;
    String errorMessage;

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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String error) {
        this.errorMessage = error;
    }
}
