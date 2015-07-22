package org.geneontology.minerva.json;

import org.apache.commons.lang3.tuple.Pair;
import org.geneontology.minerva.util.IdStringManager.AnnotationShorthand;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAnnotationValueVisitorEx;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.vocab.OWL2Datatype;

public class JsonTools {

	private static final String VALUE_TYPE_IRI = "IRI";
	
	public static JsonAnnotation create(OWLAnnotationProperty p, OWLAnnotationValue value) {
		AnnotationShorthand annotationShorthand = AnnotationShorthand.getShorthand(p.getIRI());
		if (annotationShorthand != null) {
			// try to shorten IRIs for shorthand annotations
			return create(annotationShorthand.getShorthand(), value);
		}
		// use full IRI strings for non-shorthand annotations
		return create(p.getIRI().toString(), value);
	}
	
	public static JsonAnnotation create(OWLDataProperty p, OWLLiteral value) {
		String type = getType(value);
		return JsonAnnotation.create(p.getIRI().toString(), value.getLiteral(), type);
	}
	
	private static String getType(OWLLiteral literal) {
		OWLDatatype datatype = literal.getDatatype();
		String type = null;
		if (datatype.isString() || datatype.isRDFPlainLiteral()) {
			// do nothing
		}
		else if (datatype.isBuiltIn()) {
			type = datatype.getBuiltInDatatype().getPrefixedName();
		}
		return type;
	}
	
	public static Pair<String, String> createSimplePair(OWLAnnotation an) {
		Pair<String, String> result = null;
		// only render shorthand annotations in simple pairs
		AnnotationShorthand shorthand = AnnotationShorthand.getShorthand(an.getProperty().getIRI());
		if (shorthand != null) {
			String value = an.getValue().accept(new OWLAnnotationValueVisitorEx<String>() {

				@Override
				public String visit(IRI iri) {
					return iri.toString();
				}

				@Override
				public String visit(OWLAnonymousIndividual individual) {
					return null;
				}

				@Override
				public String visit(OWLLiteral literal) {
					return literal.getLiteral();
				}
			});
			if (value != null) {
				result = Pair.of(shorthand.getShorthand(), value);
			}
		}
		return result;
	}
	
	private static JsonAnnotation create(final String key, OWLAnnotationValue value) {
		return value.accept(new OWLAnnotationValueVisitorEx<JsonAnnotation>() {

			@Override
			public JsonAnnotation visit(IRI iri) {
				String iriString = iri.toString();
				return JsonAnnotation.create(key, iriString, VALUE_TYPE_IRI);
			}

			@Override
			public JsonAnnotation visit(OWLAnonymousIndividual individual) {
				return null; // do nothing
			}

			@Override
			public JsonAnnotation visit(OWLLiteral literal) {
				return JsonAnnotation.create(key, literal.getLiteral(), getType(literal));
			}
		});
	}
	
	public static JsonAnnotation create(AnnotationShorthand key, String value) {
		return JsonAnnotation.create(key.getShorthand(), value, null);
	}
	
	private static boolean isIRIValue(JsonAnnotation ann) {
		return VALUE_TYPE_IRI.equalsIgnoreCase(ann.valueType);
	}
	
	public static OWLAnnotationValue createAnnotationValue(JsonAnnotation ann, OWLDataFactory f) {
		OWLAnnotationValue annotationValue;
		if (isIRIValue(ann)) {
			annotationValue = IRI.create(ann.value);
		}
		else {
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
		for(OWL2Datatype current : OWL2Datatype.values()) {
			if (current.getPrefixedName().equalsIgnoreCase(ann.valueType)
					|| current.getShortForm().equalsIgnoreCase(ann.valueType)) {
				datatype = current;
				break;
			}
		}
		if (datatype != null) {
			literal = f.getOWLLiteral(ann.value, datatype);
		}
		else {
			literal = f.getOWLLiteral(ann.value);
		}
		return literal;
	}
}
