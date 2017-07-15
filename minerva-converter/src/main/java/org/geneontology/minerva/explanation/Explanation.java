package org.geneontology.minerva.explanation;

import java.util.Arrays;

public class Explanation {
	
	public String[] triples;
	public String[] rules;
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(rules);
		result = prime * result + Arrays.hashCode(triples);
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Explanation other = (Explanation) obj;
		if (!Arrays.equals(rules, other.rules))
			return false;
		if (!Arrays.equals(triples, other.triples))
			return false;
		return true;
	}

}
