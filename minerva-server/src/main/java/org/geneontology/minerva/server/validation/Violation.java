/**
 * 
 */
package org.geneontology.minerva.server.validation;

/**
 * @author bgood
 *
 */
public class Violation {

	String node;
	String commentary;
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
	public String getCommentary() {
		return commentary;
	}
	public void setCommentary(String commentary) {
		this.commentary = commentary;
	}

	
}
