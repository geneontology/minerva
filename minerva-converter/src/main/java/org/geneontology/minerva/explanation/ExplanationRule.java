package org.geneontology.minerva.explanation;

import com.google.gson.annotations.SerializedName;

import java.util.Arrays;

public class ExplanationRule {

    @SerializedName("@id")
    public String id;
    public ExplanationTriple[] body;
    public ExplanationTriple[] head;

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(body);
        result = prime * result + Arrays.hashCode(head);
        result = prime * result + ((id == null) ? 0 : id.hashCode());
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
        ExplanationRule other = (ExplanationRule) obj;
        if (!Arrays.equals(body, other.body))
            return false;
        if (!Arrays.equals(head, other.head))
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }

}
