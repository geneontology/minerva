package org.geneontology.minerva.legacy.sparql;

import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.legacy.sparql.GPADData.ConjunctiveExpression;
import org.semanticweb.owlapi.model.IRI;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GPADRenderer {

    private final CurieHandler curieHandler;
    private final Clock clock;

    public static final String HEADER = "!gpad-version: 2.0";
    public static final String GENERATED_BY = "GO_Noctua";
    public static final String ATTRIBUTE = "!DB_Object_ID\tNegation\tRelation\tOntology_Class_ID\tReference\tEvidence_Type\tWith_Or_From\tInteracting_Taxon_ID\tAnnotation_Date\tAssigned_By\tAnnotation_Extensions\tAnnotation_Properties";

    public GPADRenderer(CurieHandler handler, Map<IRI, String> shorthandIndex) {
        this(handler, shorthandIndex, Clock.systemUTC());
    }

    GPADRenderer(CurieHandler handler, Map<IRI, String> shorthandIndex, Clock clock) {
        // The shorthand parameter is retained for source compatibility. GPAD 2.0 requires relation IDs.
        this.curieHandler = handler;
        this.clock = clock;
    }

    public String renderAll(Collection<GPADData> data) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER);
        sb.append("\n");
        sb.append("!generated-by: ");
        sb.append(GENERATED_BY);
        sb.append("\n");
        sb.append("!date-generated: ");
        sb.append(LocalDate.now(clock));
        sb.append("\n");

        for (GPADData annotation : data) {
            sb.append(render(annotation));
            sb.append("\n");
        }
        return sb.toString();
    }

    public String render(GPADData data) {
        List<String> columns = new ArrayList<>();
        columns.add(clean(curieHandler.getCuri(data.getObject())));
        columns.add(data.getOperator() == GPADOperatorStatus.NOT ? GPADOperatorStatus.NOT.name() : "");
        columns.add(clean(curieHandler.getCuri(data.getQualifier())));
        columns.add(clean(curieHandler.getCuri(data.getOntologyClass())));
        columns.add(clean(data.getReference()));
        columns.add(clean(curieHandler.getCuri(data.getEvidence())));
        columns.add(clean(data.getWithOrFrom().orElse("")));
        columns.add(data.getInteractingTaxonID()
                .map(taxonIRI -> clean(curieHandler.getCuri(taxonIRI)))
                .orElse(""));
        columns.add(formatDate(data.getModificationDate()));
        columns.add(clean(data.getAssignedBy()));
        columns.add(formatAnnotationExtensions(data.getAnnotationExtensions()));
        columns.add(data.getAnnotations().stream()
                .map(this::formatAnnotationProperty)
                .sorted()
                .collect(Collectors.joining("|")));
        return String.join("\t", columns);
    }

    private String formatDate(String date) {
        if (date.matches("\\d{8}")) {
            return date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8);
        }
        return date;
    }

    private String formatAnnotationExtensions(Set<ConjunctiveExpression> extensions) {
        return extensions.stream()
                .sorted(extensionComparator)
                .map(this::renderConjunctiveExpression)
                .collect(Collectors.joining(","));
    }

    private static final Comparator<ConjunctiveExpression> extensionComparator = new Comparator<ConjunctiveExpression>() {
        @Override
        public int compare(ConjunctiveExpression a, ConjunctiveExpression b) {
            return (a.getRelation().toString() + a.getFiller().toString()).compareTo(b.getRelation().toString() + b.getFiller().toString());
        }
    };

    private String renderConjunctiveExpression(ConjunctiveExpression ce) {
        String relation = clean(curieHandler.getCuri(ce.getRelation()));
        String filler = clean(curieHandler.getCuri(ce.getFiller()));
        return relation + "(" + filler + ")";
    }

    private String formatAnnotationProperty(Pair<String, String> annotation) {
        String property = annotation.getLeft();
        String value = annotation.getRight();
        if ("contributor".equals(property)) {
            property = "contributor-id";
        }
        if ("contributor-id".equals(property) || "reviewer-id".equals(property)) {
            value = formatPersonID(value);
        }
        return property + "=" + value;
    }

    private String formatPersonID(String id) {
        return id.replaceFirst("(?i)^https?://orcid\\.org/", "orcid:")
                .replaceFirst("(?i)^orcid:", "orcid:")
                .replaceFirst("(?i)^goc:", "goc:");
    }

    private String clean(String text) {
        return text.replaceAll("\\s", "");
    }

}
