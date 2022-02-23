package owltools.util;

import org.semanticweb.owlapi.model.*;

import java.util.*;

public class OwlHelper {

    private OwlHelper() {
        // no instances
    }

    public static Set<OWLAnnotation> getAnnotations(OWLEntity e, OWLAnnotationProperty property, OWLOntology ont) {
        Set<OWLAnnotation> annotations;
        if (e != null && property != null && ont != null) {
            annotations = new HashSet<>();
            for (OWLAnnotationAssertionAxiom ax : ont.getAnnotationAssertionAxioms(e.getIRI())) {
                if (property.equals(ax.getProperty())) {
                    annotations.add(ax.getAnnotation());
                }
            }
        } else {
            annotations = Collections.emptySet();
        }
        return annotations;
    }

    public static Set<OWLAnnotation> getAnnotations(OWLEntity e, OWLOntology ont) {
        Set<OWLAnnotation> annotations;
        if (e != null && ont != null) {
            Set<OWLAnnotationAssertionAxiom> axioms = ont.getAnnotationAssertionAxioms(e.getIRI());
            annotations = new HashSet<>(axioms.size());
            for (OWLAnnotationAssertionAxiom ax : axioms) {
                annotations.add(ax.getAnnotation());
            }
        } else {
            annotations = Collections.emptySet();
        }
        return annotations;
    }

    public static Set<OWLAnnotation> getAnnotations(OWLEntity e, Set<OWLOntology> ontolgies) {
        Set<OWLAnnotation> annotations;
        if (e != null && ontolgies != null && !ontolgies.isEmpty()) {
            annotations = new HashSet<>();
            for (OWLOntology ont : ontolgies) {
                annotations.addAll(getAnnotations(e, ont));
            }
        } else {
            annotations = Collections.emptySet();
        }
        return annotations;
    }

    public static Set<OWLClassExpression> getEquivalentClasses(OWLClass cls, OWLOntology ont) {
        Set<OWLClassExpression> expressions;
        if (cls != null && ont != null) {
            Set<OWLEquivalentClassesAxiom> axioms = ont.getEquivalentClassesAxioms(cls);
            expressions = new HashSet<>(axioms.size());
            for (OWLEquivalentClassesAxiom ax : axioms) {
                expressions.addAll(ax.getClassExpressions());
            }
            expressions.remove(cls); // set should not contain the query cls
        } else {
            expressions = Collections.emptySet();
        }
        return expressions;
    }

    public static Set<OWLClassExpression> getEquivalentClasses(OWLClass cls, Set<OWLOntology> ontologies) {
        Set<OWLClassExpression> expressions;
        if (cls != null && ontologies != null && ontologies.isEmpty() == false) {
            expressions = new HashSet<>();
            for (OWLOntology ont : ontologies) {
                expressions.addAll(getEquivalentClasses(cls, ont));
            }
        } else {
            expressions = Collections.emptySet();
        }
        return expressions;
    }

    public static Set<OWLClassExpression> getSuperClasses(OWLClass subCls, OWLOntology ont) {
        Set<OWLClassExpression> result;
        if (subCls != null && ont != null) {
            result = new HashSet<>();
            Set<OWLSubClassOfAxiom> axioms = ont.getSubClassAxiomsForSubClass(subCls);
            for (OWLSubClassOfAxiom axiom : axioms) {
                result.add(axiom.getSuperClass());
            }
        } else {
            result = Collections.emptySet();
        }
        return result;
    }

    public static Set<OWLClassExpression> getSuperClasses(OWLClass subCls, Set<OWLOntology> ontologies) {
        Set<OWLClassExpression> result;
        if (subCls != null && ontologies != null && ontologies.isEmpty() == false) {
            result = new HashSet<>();
            for (OWLOntology ont : ontologies) {
                result.addAll(getSuperClasses(subCls, ont));
            }
        } else {
            result = Collections.emptySet();
        }
        return result;
    }

    public static Set<OWLClassExpression> getSubClasses(OWLClass superCls, OWLOntology ont) {
        Set<OWLClassExpression> result;
        if (superCls != null && ont != null) {
            result = new HashSet<>();
            Set<OWLSubClassOfAxiom> axioms = ont.getSubClassAxiomsForSuperClass(superCls);
            for (OWLSubClassOfAxiom axiom : axioms) {
                result.add(axiom.getSubClass());
            }
        } else {
            result = Collections.emptySet();
        }
        return result;
    }

    public static Set<OWLClassExpression> getSubClasses(OWLClass superCls, Set<OWLOntology> ontologies) {
        Set<OWLClassExpression> result;
        if (superCls != null && ontologies != null && ontologies.isEmpty() == false) {
            result = new HashSet<>();
            for (OWLOntology ont : ontologies) {
                result.addAll(getSubClasses(superCls, ont));
            }
        } else {
            result = Collections.emptySet();
        }
        return result;
    }

    public static Set<OWLClassExpression> getTypes(OWLIndividual i, OWLOntology ont) {
        Set<OWLClassExpression> types;
        if (ont != null && i != null && i.isNamed()) {
            types = getTypes(i.asOWLNamedIndividual(), ont);
        } else {
            types = Collections.emptySet();
        }
        return types;
    }

    public static Set<OWLClassExpression> getTypes(OWLNamedIndividual i, OWLOntology ont) {
        Set<OWLClassExpression> types;
        if (i != null && ont != null) {
            types = new HashSet<>();
            for (OWLClassAssertionAxiom axiom : ont.getClassAssertionAxioms(i)) {
                types.add(axiom.getClassExpression());
            }
        } else {
            types = Collections.emptySet();
        }
        return types;
    }

    public static Set<OWLClassExpression> getTypes(OWLNamedIndividual i, Set<OWLOntology> ontologies) {
        Set<OWLClassExpression> types;
        if (i != null && ontologies != null && ontologies.isEmpty() == false) {
            types = new HashSet<>();
            for (OWLOntology ont : ontologies) {
                types.addAll(getTypes(i, ont));
            }
        } else {
            types = Collections.emptySet();
        }
        return types;
    }

    public static Map<OWLObjectPropertyExpression, Set<OWLIndividual>> getObjectPropertyValues(OWLIndividual i, OWLOntology ont) {
        Set<OWLObjectPropertyAssertionAxiom> axioms = ont.getObjectPropertyAssertionAxioms(i);
        Map<OWLObjectPropertyExpression, Set<OWLIndividual>> result = new HashMap<>();
        for (OWLObjectPropertyAssertionAxiom ax : axioms) {
            Set<OWLIndividual> inds = result.get(ax.getProperty());
            if (inds == null) {
                inds = new HashSet<>();
                result.put(ax.getProperty(), inds);
            }
            inds.add(ax.getObject());
        }
        return result;
    }

    public static boolean isTransitive(OWLObjectPropertyExpression property, OWLOntology ontology) {
        return !ontology.getTransitiveObjectPropertyAxioms(property).isEmpty();
    }

    public static boolean isTransitive(OWLObjectPropertyExpression property, Set<OWLOntology> ontologies) {
        for (OWLOntology ont : ontologies) {
            if (isTransitive(property, ont)) {
                return true;
            }
        }
        return false;
    }

    public static Set<OWLAnnotationProperty> getSubProperties(OWLAnnotationProperty superProp, OWLOntology ont) {
        return getSubProperties(superProp, Collections.singleton(ont));
    }

    public static Set<OWLAnnotationProperty> getSubProperties(OWLAnnotationProperty superProp, Set<OWLOntology> ontologies) {
        Set<OWLAnnotationProperty> result = new HashSet<OWLAnnotationProperty>();
        for (OWLOntology ont : ontologies) {
            for (OWLSubAnnotationPropertyOfAxiom ax : ont.getAxioms(AxiomType.SUB_ANNOTATION_PROPERTY_OF)) {
                if (ax.getSuperProperty().equals(superProp)) {
                    result.add(ax.getSubProperty());
                }
            }
        }
        return result;
    }

    public static Set<OWLAnnotationProperty> getSuperProperties(OWLAnnotationProperty subProp, OWLOntology ont) {
        return getSuperProperties(subProp, Collections.singleton(ont));
    }

    public static Set<OWLAnnotationProperty> getSuperProperties(OWLAnnotationProperty subProp, Set<OWLOntology> ontologies) {
        Set<OWLAnnotationProperty> result = new HashSet<OWLAnnotationProperty>();
        for (OWLOntology ont : ontologies) {
            for (OWLSubAnnotationPropertyOfAxiom ax : ont.getAxioms(AxiomType.SUB_ANNOTATION_PROPERTY_OF)) {
                if (ax.getSubProperty().equals(subProp)) {
                    result.add(ax.getSuperProperty());
                }
            }
        }
        return result;
    }

    public static Set<OWLObjectPropertyExpression> getSuperProperties(OWLObjectPropertyExpression prop, OWLOntology ont) {
        Set<OWLObjectPropertyExpression> result = new HashSet<>();
        Set<OWLSubObjectPropertyOfAxiom> axioms = ont.getObjectSubPropertyAxiomsForSubProperty(prop);
        for (OWLSubPropertyAxiom<OWLObjectPropertyExpression> axiom : axioms) {
            result.add(axiom.getSuperProperty());
        }
        return result;
    }

    public static Set<OWLObjectPropertyExpression> getSubProperties(OWLObjectPropertyExpression prop, OWLOntology ont) {
        Set<OWLObjectPropertyExpression> results = new HashSet<>();
        Set<OWLSubObjectPropertyOfAxiom> axioms = ont.getObjectSubPropertyAxiomsForSuperProperty(prop);
        for (OWLSubObjectPropertyOfAxiom axiom : axioms) {
            results.add(axiom.getSubProperty());
        }
        return results;
    }

    public static String getIdentifier(IRI iriId, OWLOntology baseOntology) {

        if (iriId == null)
            return null;

        String iri = iriId.toString();

		/*
		// canonical IRIs
		if (iri.startsWith("http://purl.obolibrary.org/obo/")) {
			String canonicalId = iri.replace("http://purl.obolibrary.org/obo/", "");
		}
		 */

        int indexSlash = iri.lastIndexOf("/");


        String prefixURI = null;
        String id = null;

        if (indexSlash > -1) {
            prefixURI = iri.substring(0, indexSlash + 1);
            id = iri.substring(indexSlash + 1);
        } else
            id = iri;

        String s[] = id.split("#_");

        // table 5.9.2 row 2 - NonCanonical-Prefixed-ID
        if (s.length > 1) {
            return s[0] + ":" + s[1];
        }

        // row 3 - Unprefixed-ID
        s = id.split("#");
        if (s.length > 1) {
            //			prefixURI = prefixURI + s[0] + "#";

            //			if(!(s[1].contains("#") || s[1].contains("_"))){
            String prefix = "";

            if ("owl".equals(s[0]) || "rdf".equals(s[0]) || "rdfs".equals(s[0])) {
                prefix = s[0] + ":";
            }
            // TODO: the following implements behavior in current spec, but this leads to undesirable results
			/*
			else if (baseOntology != null) {
				String oid = getOntologyId(baseOntology); // OBO-style ID
				if (oid.equals(s[0]))
					prefix = "";
				else {
					return iri;
				}
				//prefix = s[0];
			}
			*/

            return prefix + s[1];
        }

        // row 1 - Canonical-Prefixed-ID
        s = id.split("_");

        if (s.length == 2 && !id.contains("#") && !s[1].contains("_")) {
            String localId = java.net.URLDecoder.decode(s[1]);
            return s[0] + ":" + localId;
        }
        if (s.length > 2 && !id.contains("#")) {
            if (s[s.length - 1].replaceAll("[0-9]", "").length() == 0) {
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < s.length; i++) {
                    if (i > 0) {
                        if (i == s.length - 1) {
                            sb.append(":");
                        } else {
                            sb.append("_");
                        }
                    }
                    sb.append(s[i]);
                }
                return sb.toString();
            }
        }


        return iri;
    }

}
