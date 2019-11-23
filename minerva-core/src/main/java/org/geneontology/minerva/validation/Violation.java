/**
 * 
 */
package org.geneontology.minerva.validation;

/**
 * @author bgood
 *
 */
public class Violation {

	String node;
	/**
	 * 
	 */
	public Violation(String node) {
		setNode(node);
	}
	public String getNode() {
		return node;
	}
	public void setNode(String node) {
		this.node = node;
	}

	
}
