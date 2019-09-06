/**
 * 
 */
package org.geneontology.minerva.server.validation;

import com.google.gson.annotations.SerializedName;

/**
 * @author bgood
 *
 */
public class ValidationResultSet {
	
	@SerializedName("is-conformant")
	boolean all_conformant;
	
	@SerializedName("owl-validation")
	OWLValidationReport owl_validation; 
	
	@SerializedName("shex-validation")
	ShexValidationReport shex_validation;
	//Aim to support multiple validation regimes - e.g. reasoner, shex, gorules
	//add them here
	
	public ValidationResultSet(OWLValidationReport owlvalidation, ShexValidationReport shexvalidation) {
		super();
		this.owl_validation = owlvalidation;
		this.shex_validation = shexvalidation;
		if(owlvalidation.conformant&&shex_validation.conformant) {
			all_conformant = true;
		}else {
			all_conformant = false;
		}
	}

	public OWLValidationReport getOwlvalidation() {
		return owl_validation;
	}

	public void setOwlvalidation(OWLValidationReport owlvalidation) {
		this.owl_validation = owlvalidation;
	}

	public ShexValidationReport getShexvalidation() {
		return shex_validation;
	}

	public void setShexvalidation(ShexValidationReport shexvalidation) {
		this.shex_validation = shexvalidation;
	}

	public boolean allConformant() {
		if(owl_validation.conformant&&shex_validation.conformant) {
			return true;
		}else {
			return false;
		}
	}
	
}
