package org.geneontology.minerva.json;

import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.util.AnnotationShorthand;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWL2Datatype;

public class JsonTools {

    private static final String VALUE_TYPE_IRI = "IRI";

    public static JsonAnnotation create(OWLAnnotationProperty p, OWLAnnotationValue value, String label, CurieHandler curieHandler) {
        AnnotationShorthand annotationShorthand = AnnotationShorthand.getShorthand(p.getIRI());
        if (annotationShorthand != null) {
            // try to shorten IRIs for shorthand annotations
            return create(annotationShorthand.getShorthand(), value, label, curieHandler);
        }
        return create(curieHandler.getCuri(p), value, label, curieHandler);
    }

    public static JsonAnnotation create(OWLDataProperty p, OWLLiteral value, String label, CurieHandler curieHandler) {
        String type = getType(value);
        return JsonAnnotation.create(curieHandler.getCuri(p), value.getLiteral(), type, label);
    }

    private static String getType(OWLLiteral literal) {
        OWLDatatype datatype = literal.getDatatype();
        String type = null;
        if (datatype.isString() || datatype.isRDFPlainLiteral()) {
            // do nothing
        } else if (datatype.isBuiltIn()) {
            type = datatype.getBuiltInDatatype().getPrefixedName();
        }
        return type;
    }

    private static JsonAnnotation create(final String key, OWLAnnotationValue value, String label, final CurieHandler curieHandler) {
        return value.accept(new OWLAnnotationValueVisitorEx<JsonAnnotation>() {

            @Override
            public JsonAnnotation visit(IRI iri) {
                String iriString = curieHandler.getCuri(iri);
                return JsonAnnotation.create(key, iriString, VALUE_TYPE_IRI, label);
            }

            @Override
            public JsonAnnotation visit(OWLAnonymousIndividual individual) {
                return null; // do nothing
            }

            @Override
            public JsonAnnotation visit(OWLLiteral literal) {
                return JsonAnnotation.create(key, literal.getLiteral(), getType(literal), label);
            }
        });
    }

    public static JsonAnnotation create(AnnotationShorthand key, String value, String label) {
        return JsonAnnotation.create(key.getShorthand(), value, null, label);
    }

    private static boolean isIRIValue(JsonAnnotation ann) {
        return VALUE_TYPE_IRI.equalsIgnoreCase(ann.valueType);
    }

    public static OWLAnnotationValue createAnnotationValue(JsonAnnotation ann, OWLDataFactory f) {
        OWLAnnotationValue annotationValue;
        if (isIRIValue(ann)) {
            annotationValue = IRI.create(ann.value);
        } else {
            annotationValue = createLiteralInternal(ann, f);
        }
        return annotationValue;
    }

    public static OWLLiteral createLiteral(JsonAnnotation ann, OWLDataFactory f) {
        OWLLiteral literal = null;
        if (isIRIValue(ann) == false) {
            literal = createLiteralInternal(ann, f);
        }
        return literal;
    }

    private static OWLLiteral createLiteralInternal(JsonAnnotation ann, OWLDataFactory f) {
        OWLLiteral literal;
        OWL2Datatype datatype = null;
        for (OWL2Datatype current : OWL2Datatype.values()) {
            if (current.getPrefixedName().equalsIgnoreCase(ann.valueType)
                    || current.getShortForm().equalsIgnoreCase(ann.valueType)) {
                datatype = current;
                break;
            }
        }
        if (datatype != null) {
            literal = f.getOWLLiteral(ann.value, datatype);
        } else {
            literal = f.getOWLLiteral(ann.value);
        }
        return literal;
    }
}
