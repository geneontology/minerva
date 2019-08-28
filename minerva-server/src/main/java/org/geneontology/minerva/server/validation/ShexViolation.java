/**
 * 
 */
package org.geneontology.minerva.server.validation;

import java.util.Set;

/**
 * @author bgood
 *
 */
public class ShexViolation extends Violation {

	Set<ShexExplanation> explanations;
	/**
	 * 
	 */
	public ShexViolation(String node_id) {
		super(node_id);
	}

}
