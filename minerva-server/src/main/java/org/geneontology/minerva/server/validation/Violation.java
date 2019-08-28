/**
 * 
 */
package org.geneontology.minerva.server.validation;

/**
 * @author bgood
 *
 */
public class Violation {

	String node_id;
	String commentary;
	/**
	 * 
	 */
	public Violation(String node_id) {
		setNode_id(node_id);
	}
	public String getNode_id() {
		return node_id;
	}
	public void setNode_id(String node_id) {
		this.node_id = node_id;
	}
	public String getCommentary() {
		return commentary;
	}
	public void setCommentary(String commentary) {
		this.commentary = commentary;
	}

	
}
