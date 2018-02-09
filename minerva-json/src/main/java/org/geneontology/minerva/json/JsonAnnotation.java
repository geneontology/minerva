package org.geneontology.minerva.json;

import com.google.gson.annotations.SerializedName;

public class JsonAnnotation {
	
	public String key;
	public String value;
	@SerializedName("value-type")
	public String valueType; // optional, defaults to OWL string literal for null
	public String label; // optional, a label for the value, which may be a String-form IRI
	
	static JsonAnnotation create(String key, String value, String type, String label) {
		JsonAnnotation a = new JsonAnnotation();
		a.key = key;
		a.value = value;
		a.valueType = type;
		a.label = label;
		return a;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		result = prime * result + ((valueType == null) ? 0 : valueType.hashCode());
		result = prime * result + ((label == null) ? 0 : label.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		JsonAnnotation other = (JsonAnnotation) obj;
		if (key == null) {
			if (other.key != null) {
				return false;
			}
		} else if (!key.equals(other.key)) {
			return false;
		}
		if (value == null) {
			if (other.value != null) {
				return false;
			}
		} else if (!value.equals(other.value)) {
			return false;
		}
		if (valueType == null) {
			if (other.valueType != null) {
				return false;
			}
		} else if (!valueType.equals(other.valueType)) {
			return false;
		}
		if (label == null) {
			if (other.label != null) {
				return false;
			}
		} else if (!label.equals(other.label)) {
			return false;
		}
		return true;
	}

}