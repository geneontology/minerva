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
	public Violation(String node_id) {
		setNode_id(node_id);
	}
	public String getNode() {
		return node;
	}
	public void setNode_id(String node) {
		this.node = node;
	}
	public String getCommentary() {
		return commentary;
	}
	public void setCommentary(String commentary) {
		this.commentary = commentary;
	}

	
}
