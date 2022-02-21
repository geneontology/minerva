package org.geneontology.minerva.explanation;

import java.util.Arrays;

public class ModelExplanation {

    public ExplanationTerm[] terms;
    public ExplanationTriple[] assertions;
    public ExplanationTriple[] inferences;
    public ExplanationRule[] rules;

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(assertions);
        result = prime * result + Arrays.hashCode(inferences);
        result = prime * result + Arrays.hashCode(rules);
        result = prime * result + Arrays.hashCode(terms);
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
        ModelExplanation other = (ModelExplanation) obj;
        if (!Arrays.equals(assertions, other.assertions))
            return false;
        if (!Arrays.equals(inferences, other.inferences))
            return false;
        if (!Arrays.equals(rules, other.rules))
            return false;
        if (!Arrays.equals(terms, other.terms))
            return false;
        return true;
    }

}
