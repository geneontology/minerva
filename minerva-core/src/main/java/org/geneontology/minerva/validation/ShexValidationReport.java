/**
 *
 */
package org.geneontology.minerva.validation;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * @author bgood
 *
 */

public class ShexValidationReport extends ModelValidationReport {
    @SerializedName("report-type")
    public static final String report_type_id = "SHEX_CORE_SCHEMA";
    public static final String tracker = "https://github.com/geneontology/go-shapes/issues";

    @SerializedName("rule-file")
    public static final String rulefile = "https://github.com/geneontology/go-shapes/blob/master/shapes/go-cam-shapes.shex";

    @SerializedName("node-matched-shapes")
    public Map<String, Set<String>> node_matched_shapes = new HashMap<String, Set<String>>();

    /**
     *
     */
    public ShexValidationReport() {
        super(null, tracker, rulefile);
    }

    /**
     * Remove violations that only repeat a failure already reported on the object of a constraint.
     *
     * A ShEx reference makes the referring node nonconformant whenever the referenced node is
     * nonconformant. For example, if a biological process is invalid, a molecular function that is
     * correctly part of that process also becomes nonconformant. The latter is useful validation
     * state, but it is a misleading user-facing diagnostic.
     *
     * Cascades are matched conservatively by both object node and expected shape. A failure under a
     * more-specific shape can also match an expected ancestor shape. Dependencies that participate
     * in a cycle are retained because there is no downstream-most failure to report.
     */
    void suppressCascadingViolations() {
        suppressCascadingViolations(Collections.<String, Set<String>>emptyMap());
    }

    void suppressCascadingViolations(Map<String, Set<String>> shapeAncestors) {
        if (violations == null || violations.isEmpty()) {
            return;
        }

        Map<FailureKey, Set<ShexExplanation>> failures = new HashMap<FailureKey, Set<ShexExplanation>>();
        Map<ShexExplanation, ShexViolation> explanationOwners = new IdentityHashMap<ShexExplanation, ShexViolation>();
        for (Violation violation : violations) {
            if (!(violation instanceof ShexViolation)) {
                continue;
            }
            ShexViolation shexViolation = (ShexViolation) violation;
            if (shexViolation.getExplanations() == null) {
                continue;
            }
            for (ShexExplanation explanation : shexViolation.getExplanations()) {
                FailureKey key = new FailureKey(violation.getNode(), explanation.getShape());
                Set<ShexExplanation> explanations = failures.get(key);
                if (explanations == null) {
                    explanations = Collections.newSetFromMap(new IdentityHashMap<ShexExplanation, Boolean>());
                    failures.put(key, explanations);
                }
                explanations.add(explanation);
                explanationOwners.put(explanation, shexViolation);
            }
        }

        Map<FailureKey, Set<FailureKey>> failuresByAlias = new HashMap<FailureKey, Set<FailureKey>>();
        for (FailureKey failure : failures.keySet()) {
            addToSetMap(failuresByAlias, failure, failure);
            Set<String> ancestors = shapeAncestors.get(failure.shape);
            if (ancestors != null) {
                for (String ancestor : ancestors) {
                    addToSetMap(failuresByAlias, new FailureKey(failure.node, ancestor), failure);
                }
            }
        }

        Map<FailureKey, Set<FailureKey>> dependencyGraph = new HashMap<FailureKey, Set<FailureKey>>();
        Map<ShexConstraint, Set<FailureKey>> constraintTargets = new IdentityHashMap<ShexConstraint, Set<FailureKey>>();
        for (Map.Entry<FailureKey, Set<ShexExplanation>> failure : failures.entrySet()) {
            FailureKey source = failure.getKey();
            for (ShexExplanation explanation : failure.getValue()) {
                if (explanation.getConstraints() == null) {
                    continue;
                }
                for (ShexConstraint constraint : explanation.getConstraints()) {
                    if (constraint.getObject() == null || constraint.getIntended_range_shapes() == null) {
                        continue;
                    }
                    for (String expectedShape : constraint.getIntended_range_shapes()) {
                        FailureKey targetAlias = new FailureKey(constraint.getObject(), expectedShape);
                        Set<FailureKey> targets = failuresByAlias.get(targetAlias);
                        if (targets != null) {
                            for (FailureKey target : targets) {
                                addToSetMap(dependencyGraph, source, target);
                                addToSetMap(constraintTargets, constraint, target);
                            }
                        }
                    }
                }
            }
        }

        // Java has no IdentityHashSet; these sets track the exact mutable report objects changed while filtering,
        // independently of any value-based equality the report classes may define now or in the future.
        Set<ShexExplanation> changedExplanations = Collections.newSetFromMap(new IdentityHashMap<ShexExplanation, Boolean>());
        Set<ShexViolation> changedViolations = Collections.newSetFromMap(new IdentityHashMap<ShexViolation, Boolean>());
        for (Map.Entry<FailureKey, Set<ShexExplanation>> failure : failures.entrySet()) {
            FailureKey source = failure.getKey();
            for (ShexExplanation explanation : failure.getValue()) {
                if (explanation.getConstraints() == null) {
                    continue;
                }
                for (ShexConstraint constraint : new HashSet<ShexConstraint>(explanation.getConstraints())) {
                    Set<FailureKey> targets = constraintTargets.get(constraint);
                    if (targets == null || targets.isEmpty()) {
                        continue;
                    }
                    boolean participatesInCycle = false;
                    for (FailureKey target : targets) {
                        if (hasPath(target, source, dependencyGraph)) {
                            participatesInCycle = true;
                            break;
                        }
                    }
                    if (!participatesInCycle) {
                        explanation.getConstraints().remove(constraint);
                        changedExplanations.add(explanation);
                        changedViolations.add(explanationOwners.get(explanation));
                    }
                }
            }
        }

        for (ShexExplanation explanation : changedExplanations) {
            if (explanation.getErrorMessage() == null && explanation.getConstraints().isEmpty()) {
                ShexViolation owner = explanationOwners.get(explanation);
                owner.getExplanations().remove(explanation);
            }
        }
        for (ShexViolation violation : changedViolations) {
            if (violation.getExplanations().isEmpty()) {
                violations.remove(violation);
            }
        }
    }

    private static <K, V> void addToSetMap(Map<K, Set<V>> map, K key, V value) {
        Set<V> values = map.get(key);
        if (values == null) {
            values = new HashSet<V>();
            map.put(key, values);
        }
        values.add(value);
    }

    private static boolean hasPath(FailureKey start, FailureKey target, Map<FailureKey, Set<FailureKey>> graph) {
        Deque<FailureKey> pending = new ArrayDeque<FailureKey>();
        Set<FailureKey> visited = new HashSet<FailureKey>();
        pending.push(start);
        while (!pending.isEmpty()) {
            FailureKey current = pending.pop();
            if (current.equals(target)) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            Set<FailureKey> next = graph.get(current);
            if (next != null) {
                pending.addAll(next);
            }
        }
        return false;
    }

    private static final class FailureKey {
        private final String node;
        private final String shape;

        private FailureKey(String node, String shape) {
            this.node = node;
            this.shape = shape;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof FailureKey)) {
                return false;
            }
            FailureKey other = (FailureKey) obj;
            return Objects.equals(node, other.node) && Objects.equals(shape, other.shape);
        }

        @Override
        public int hashCode() {
            return Objects.hash(node, shape);
        }
    }

    public String getAsText() {
        String report = "report type id = " + report_type_id + "\nrulefile = " + rulefile + "\ntracker = " + tracker + "\n";
        if (conformant) {
            report += "No errors detected";
            return report;
        }
        report += getViolations().size() + " noncomformant nodes detected:\n";
        for (Violation violation : getViolations()) {
            report += "node: " + violation.getNode() + " ";
            ShexViolation sv = (ShexViolation) violation;
            for (ShexExplanation e : sv.getExplanations()) {
                report += "was expected to match shape: " + e.shape;
                report += " but did not fit the following constraints:";
                for (ShexConstraint c : e.getConstraints()) {
                    report += "\n\tthe objects of assertions made with " + c.getProperty() + " should be nodes that fit the one of these shapes: ";
                    report += "\n\t\t" + c.getIntended_range_shapes();
                    report += "\n\t\tbut, sadly, the object " + c.getObject() + " of one such assertion emanating from the failing node here did not.\n";
                }
            }
        }
        return report;
    }

    public String getAsTab(String prefix) {
        if (conformant) {
            return "conformant\n";
        }
        String report = "";
        if (getViolations() == null) {
            return "noncomformant (no explanation)\n";
        }
        for (Violation violation : getViolations()) {
            ShexViolation sv = (ShexViolation) violation;
            for (ShexExplanation e : sv.getExplanations()) {
                String error = e.getErrorMessage();
                if (error != null) {
                    report += prefix + "\t" + violation.getNode() + "\t" + error + "\t\t\t\t\t\n";

                } else {
                    for (ShexConstraint c : e.getConstraints()) {
                        report += prefix + "\t" + violation.getNode() + "\t" + c.getNode_types() + "\t" + c.getProperty() + "\t" + c.getIntended_range_shapes() + "\t" + c.getObject() + "\t" + c.getObject_types() + "\t" + c.getMatched_range_shapes() + "\n";
                    }
                }
            }
        }
        return report;
    }

}
