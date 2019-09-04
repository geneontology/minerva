/**
 * 
 */
package org.geneontology.minerva.server.validation;

import java.util.Set;

/**
 * @author bgood
 *
 */
public class ShexConstraint {
	String target_node_uri;
	String target_node_label;
	String property_id;
	Set<String> intended_range_shapes;
	/**
	 * 
	 */


	public String getProperty_id() {
		return property_id;
	}
	public String getTarget_node_uri() {
		return target_node_uri;
	}
	public void setTarget_node_uri(String target_node_uri) {
		this.target_node_uri = target_node_uri;
	}
	public ShexConstraint(String target_node_uri, String property_id, Set<String> intended_range_shapes) {
		super();
		this.target_node_uri = target_node_uri;
		this.property_id = property_id;
		this.intended_range_shapes = intended_range_shapes;
	}
	public void setProperty_id(String property_id) {
		this.property_id = property_id;
	}
	public String getTarget_node_label() {
		return target_node_label;
	}
	public void setTarget_node_label(String target_node_label) {
		this.target_node_label = target_node_label;
	}
	public Set<String> getIntended_range_shapes() {
		return intended_range_shapes;
	}
	public void setIntended_range_shapes(Set<String> intended_range_shapes) {
		this.intended_range_shapes = intended_range_shapes;
	}

}
