package org.geneontology.minerva.legacy.sparql;

import org.apache.jena.graph.Node;
import org.semanticweb.owlapi.model.IRI;

public class BasicGPADData {
    private final Node objectNode;
    private final IRI object;
    private GPADOperatorStatus operator;
    private final IRI qualifier;
    private final Node ontologyClassNode;
    private final IRI ontologyClass;

    public BasicGPADData(Node objectNode, IRI object, IRI qualifier, Node ontologyClassNode, IRI ontologyClass) {
        this.object = object;
        this.operator = GPADOperatorStatus.NONE;
        this.qualifier = qualifier;
        this.ontologyClass = ontologyClass;
        this.objectNode = objectNode;
        this.ontologyClassNode = ontologyClassNode;
    }

    public IRI getObject() {
        return this.object;
    }

    public void setOperator(GPADOperatorStatus operator) {
        this.operator = operator;
    }

    public GPADOperatorStatus getOperator() {
        return operator;
    }

    public IRI getQualifier() {
        return this.qualifier;
    }

    public IRI getOntologyClass() {
        return this.ontologyClass;
    }

    public Node getObjectNode() {
        return this.objectNode;
    }

    public Node getOntologyClassNode() {
        return this.ontologyClassNode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof BasicGPADData)) {
            return false;
        } else {
            BasicGPADData otherData = (BasicGPADData) other;
            return this.getObject().equals(otherData.getObject())
                    && this.getOperator().equals(otherData.getOperator())
                    && this.getQualifier().equals(otherData.getQualifier())
                    && this.getOntologyClass().equals(otherData.getOntologyClass())
                    && this.getObjectNode().equals(otherData.getObjectNode())
                    && this.getOntologyClassNode().equals(otherData.getOntologyClassNode());
        }
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 37 * result + this.getObject().hashCode();
        result = 37 * result + this.getOperator().hashCode();
        result = 37 * result + this.getQualifier().hashCode();
        result = 37 * result + this.getOntologyClass().hashCode();
        result = 37 * result + this.getObjectNode().hashCode();
        result = 37 * result + this.getOntologyClassNode().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return this.object.toString() + ", " + this.operator.toString() + "," + this.qualifier.toString() + ", " + this.ontologyClass.toString();
    }
}