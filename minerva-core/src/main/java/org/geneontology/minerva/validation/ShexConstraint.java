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
	
	@SerializedName("intended-range-shapes")
	Set<String> intended_range_shapes;
	/**
	 * 
	 */


	public ShexConstraint(String object, String property, Set<String> intended_range_shapes) {
		super();
		this.object = object;
		this.property = property;
		this.intended_range_shapes = intended_range_shapes;
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
