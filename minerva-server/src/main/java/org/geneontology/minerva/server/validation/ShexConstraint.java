/**
 * 
 */
package org.geneontology.minerva.server.validation;

/**
 * @author bgood
 *
 */
public class ShexConstraint {
	String property_id;
	String propery_range;
	/**
	 * 
	 */
	public ShexConstraint(String property_id, String propery_range) {
		this.property_id = property_id;
		this.propery_range = propery_range;
	}

	public String getProperty_id() {
		return property_id;
	}
	public void setProperty_id(String property_id) {
		this.property_id = property_id;
	}
	public String getPropery_range() {
		return propery_range;
	}
	public void setPropery_range(String propery_range) {
		this.propery_range = propery_range;
	}

}
