package org.geneontology.minerva.server.handler;

import org.apache.commons.lang3.StringUtils;
import org.geneontology.minerva.MinervaOWLGraphWrapper;
import org.geneontology.minerva.ModelContainer;
import org.geneontology.minerva.MolecularModelManager.UnknownIdentifierException;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.json.JsonOwlObject;
import org.geneontology.minerva.json.JsonOwlObject.JsonOwlObjectType;
import org.geneontology.minerva.lookup.ExternalLookupService;
import org.geneontology.minerva.lookup.ExternalLookupService.LookupEntry;
import org.geneontology.minerva.server.handler.OperationsTools.MissingParameterException;
import org.semanticweb.owlapi.model.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class M3ExpressionParser {

    private final boolean checkLiteralIds;
    private final CurieHandler curieHandler;

    M3ExpressionParser(boolean checkLiteralIds, CurieHandler curieHandler) {
        this.checkLiteralIds = checkLiteralIds;
        this.curieHandler = curieHandler;
    }

    M3ExpressionParser(CurieHandler curieHandler) {
        this(false, curieHandler);
    }

    OWLClassExpression parse(ModelContainer model, JsonOwlObject expression,
                             ExternalLookupService externalLookupService)
            throws MissingParameterException, UnknownIdentifierException, OWLException {
        MinervaOWLGraphWrapper g = new MinervaOWLGraphWrapper(model.getAboxOntology());
        return parse(g, expression, externalLookupService);
    }

    OWLClassExpression parse(MinervaOWLGraphWrapper g, JsonOwlObject expression,
                             ExternalLookupService externalLookupService)
            throws MissingParameterException, UnknownIdentifierException, OWLException {
        if (expression == null) {
            throw new MissingParameterException("Missing expression: null is not a valid expression.");
        }
        if (expression.type == null) {
            throw new MissingParameterException("An expression type is required.");
        }
        if (JsonOwlObjectType.Class == expression.type) {
            if (expression.id == null) {
                throw new MissingParameterException("Missing literal for expression of type 'class'");
            }
            if (StringUtils.containsWhitespace(expression.id)) {
                throw new UnknownIdentifierException("Identifiers may not contain whitespaces: '" + expression.id + "'");
            }
            IRI clsIRI = curieHandler.getIRI(expression.id);
            OWLClass cls;
            if (checkLiteralIds) {
                cls = g.getOWLClass(clsIRI);
                if (cls == null && externalLookupService != null) {
                    List<LookupEntry> lookup = externalLookupService.lookup(clsIRI);
                    if (lookup == null || lookup.isEmpty()) {
                        throw new UnknownIdentifierException("Could not validate the id: " + expression.id);
                    }
                    cls = createClass(clsIRI, g);
                }
                if (cls == null) {
                    throw new UnknownIdentifierException("Could not retrieve a class for id: " + expression.id);
                }
            } else {
                cls = createClass(clsIRI, g);
            }
            return cls;
        } else if (JsonOwlObjectType.SomeValueFrom == expression.type) {
            if (expression.property == null) {
                throw new MissingParameterException("Missing property for expression of type 'svf'");
            }
            if (expression.property.id == null) {
                throw new MissingParameterException("Missing property id for expression of type 'svf'");
            }
            if (expression.property.type != JsonOwlObjectType.ObjectProperty) {
                throw new MissingParameterException("Unexpected type for property in 'svf': " + expression.property.type);
            }
            IRI propIRI = curieHandler.getIRI(expression.property.id);
            OWLObjectProperty p = g.getOWLObjectProperty(propIRI);
            if (p == null) {
                throw new UnknownIdentifierException("Could not find a property for: " + expression.property);
            }
            if (expression.filler != null) {
                OWLClassExpression ce = parse(g, expression.filler, externalLookupService);
                return g.getDataFactory().getOWLObjectSomeValuesFrom(p, ce);
            } else {
                throw new MissingParameterException("Missing literal or expression for expression of type 'svf'.");
            }
        } else if (JsonOwlObjectType.IntersectionOf == expression.type) {
            return parse(g, expression.expressions, externalLookupService, JsonOwlObjectType.IntersectionOf);
        } else if (JsonOwlObjectType.UnionOf == expression.type) {
            return parse(g, expression.expressions, externalLookupService, JsonOwlObjectType.UnionOf);
        } else if (JsonOwlObjectType.ComplementOf == expression.type) {
            if (expression.filler == null) {
                throw new MissingParameterException("Missing filler for expression of type 'complement'");
            }
            OWLClassExpression filler = parse(g, expression.filler, externalLookupService);
            return g.getDataFactory().getOWLObjectComplementOf(filler);
        } else {
            throw new UnknownIdentifierException("Unknown expression type: " + expression.type);
        }
    }

    private OWLClassExpression parse(MinervaOWLGraphWrapper g, JsonOwlObject[] expressions,
                                     ExternalLookupService externalLookupService, JsonOwlObjectType type)
            throws MissingParameterException, UnknownIdentifierException, OWLException {
        if (expressions.length == 0) {
            throw new MissingParameterException("Missing expressions: empty expression list is not allowed.");
        }
        if (expressions.length == 1) {
            return parse(g, expressions[0], externalLookupService);
        }
        Set<OWLClassExpression> clsExpressions = new HashSet<OWLClassExpression>();
        for (JsonOwlObject m3Expression : expressions) {
            OWLClassExpression ce = parse(g, m3Expression, externalLookupService);
            clsExpressions.add(ce);
        }
        if (type == JsonOwlObjectType.UnionOf) {
            return g.getDataFactory().getOWLObjectUnionOf(clsExpressions);
        } else if (type == JsonOwlObjectType.IntersectionOf) {
            return g.getDataFactory().getOWLObjectIntersectionOf(clsExpressions);
        } else {
            throw new UnknownIdentifierException("Unsupported expression type: " + type);
        }
    }

    private OWLClass createClass(IRI iri, MinervaOWLGraphWrapper g) {
        return g.getDataFactory().getOWLClass(iri);
    }

}
