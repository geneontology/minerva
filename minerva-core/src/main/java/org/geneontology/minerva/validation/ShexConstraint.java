/**
 *
 */
package org.geneontology.minerva.validation;

import com.google.gson.annotations.SerializedName;

import java.util.Set;

/**
 * @author bgood
 *
 */
public class ShexConstraint {
    String object;
    String property;
    Set<String> node_types;
    Set<String> object_types;
    String cardinality;
    int nobjects;

    @SerializedName("matched_range_shapes")
    Set<String> matched_range_shapes;


    public String getCardinality() {
        return cardinality;
    }

    public void setCardinality(String cardinality) {
        this.cardinality = cardinality;
    }

    public int getNobjects() {
        return nobjects;
    }

    public void setNobjects(int nobjects) {
        this.nobjects = nobjects;
    }

    public Set<String> getMatched_range_shapes() {
        return matched_range_shapes;
    }

    public void setMatched_range_shapes(Set<String> matched_range_shapes) {
        this.matched_range_shapes = matched_range_shapes;
    }

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

    public ShexConstraint(String property, String cardinality, int nobjects) {
        super();
        this.property = property;
        this.cardinality = cardinality;
        this.nobjects = nobjects;
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
