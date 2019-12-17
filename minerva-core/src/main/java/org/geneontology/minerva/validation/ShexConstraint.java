/**
 * 
 */
package org.geneontology.minerva.validation;

import java.util.Set;

import com.google.gson.annotations.SerializedName;

/**
 * @author bgood
 *
 */
public class ShexConstraint {
	String object;
	String property;
	Set<String> node_types;
	Set<String> object_types;
	
	public Set<String> getObject_types() {
		return object_types;
	}

	public void setObject_types(Set<String> object_types) {
		this.object_types = object_types;
	}

	public Set<String> getNode_types() {
		return node_types;
	}

	public void setNode_types(Set<String> node_types) {
		this.node_types = node_types;
	}

	@SerializedName("intended-range-shapes")
	Set<String> intended_range_shapes;
	/**
	 * @param node_types 
	 * @param object_types 
	 * 
	 */


	public ShexConstraint(String object, String property, Set<String> intended_range_shapes, Set<String> node_types, Set<String> object_types) {
		super();
		this.object = object;
		this.property = property;
		this.intended_range_shapes = intended_range_shapes;
		this.node_types = node_types;
		this.object_types = object_types;
	}
	
	public Set<String> getIntended_range_shapes() {
		return intended_range_shapes;
	}
	public void setIntended_range_shapes(Set<String> intended_range_shapes) {
		this.intended_range_shapes = intended_range_shapes;
	}

	public String getObject() {
		return object;
	}

	public void setObject(String object) {
		this.object = object;
	}

	public String getProperty() {
		return property;
	}

	public void setProperty(String property) {
		this.property = property;
	}

}
