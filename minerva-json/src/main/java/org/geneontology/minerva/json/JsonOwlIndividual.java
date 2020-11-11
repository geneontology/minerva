package org.geneontology.minerva.json;

import java.util.Arrays;

import com.google.gson.annotations.SerializedName;


public class JsonOwlIndividual extends JsonAnnotatedObject {
	public String id;
	public JsonOwlObject[] type;

	@SerializedName("inferred-type")
	public JsonOwlObject[] inferredType;

	@SerializedName("root-type")
	public JsonOwlObject[] rootType;
	
	@SerializedName("inferred-type-with-all")
	public JsonOwlObject[] inferredTypeWithAll;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + Arrays.hashCode(inferredType);
		result = prime * result + Arrays.hashCode(type);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		JsonOwlIndividual other = (JsonOwlIndividual) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (!Arrays.equals(inferredType, other.inferredType))
			return false;
		if (!Arrays.equals(type, other.type))
			return false;
		return true;
	}

}