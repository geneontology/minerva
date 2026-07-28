package org.geneontology.minerva.validation;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ShexValidationReportTest {

    private static final String BIOLOGICAL_PROCESS_SHAPE = "obo:go/shapes/BiologicalProcess";
    private static final String CELL_DIFFERENTIATION_SHAPE = "obo:go/shapes/CellDifferentiation";
    private static final String MOLECULAR_FUNCTION_SHAPE = "obo:go/shapes/MolecularFunction";
    private static final String CELLULAR_COMPONENT_SHAPE = "obo:go/shapes/CellularComponent";
    private static final String PART_OF = "http://purl.obolibrary.org/obo/BFO_0000050";
    private static final String MF = "http://example.org/mf";
    private static final String BP = "http://example.org/bp";
    private static final String CC = "http://example.org/cc";

    private static ShexValidator validator;

    @BeforeClass
    public static void createValidator() throws Exception {
        validator = new ShexValidator(
                "src/test/resources/validation/go-cam-shapes.shex",
                "src/test/resources/validation/go-cam-shapes.shapeMap",
                null,
                DefaultCurieHandler.getDefaultHandler());
    }

    @Test
    public void suppressesCascadingPartOfFailure() {
        Model model = createBaseModel();
        model.getResource(MF).addProperty(model.getProperty(PART_OF), model.getResource(BP));
        model.getResource(BP).addProperty(model.getProperty(PART_OF), model.getResource(CC));

        ShexValidationReport report = validator.runShapeMapValidation(model);

        assertFalse(report.isConformant());
        assertEquals(1, report.getViolations().size());
        ShexViolation violation = (ShexViolation) report.getViolations().iterator().next();
        assertEquals(BP, violation.getNode());
        ShexExplanation explanation = violation.getExplanations().iterator().next();
        assertEquals(BIOLOGICAL_PROCESS_SHAPE, explanation.getShape());
        ShexConstraint constraint = explanation.getConstraints().iterator().next();
        assertEquals(CC, constraint.getObject());
        assertEquals("BFO:0000050", constraint.getProperty());
    }

    @Test
    public void suppressesCascadeThroughMoreSpecificShape() {
        Model model = createBaseModel();
        Resource bpClass = model.getResource("http://example.org/BPClass");
        bpClass.addProperty(RDFS.subClassOf,
                model.createResource("http://purl.obolibrary.org/obo/GO_0030154"));

        Resource genericBpClass = model.createResource("http://example.org/GenericBPClass");
        genericBpClass.addProperty(RDF.type, OWL.Class)
                .addProperty(RDFS.subClassOf,
                        model.createResource("http://purl.obolibrary.org/obo/GO_0008150"));
        addEntityProperties(
                model.createResource("http://example.org/generic-bp"),
                genericBpClass,
                model.getProperty("http://purl.org/dc/elements/1.1/contributor"),
                model.getProperty("http://purl.org/dc/elements/1.1/date"),
                model.createTypedLiteral("test", XSDDatatype.XSDstring));

        model.getResource(MF).addProperty(model.getProperty(PART_OF), model.getResource(BP));
        model.getResource(BP).addProperty(model.getProperty(PART_OF), model.getResource(CC));

        ShexValidationReport report = validator.runShapeMapValidation(model);

        assertFalse(report.isConformant());
        assertEquals(1, report.getViolations().size());
        ShexViolation violation = (ShexViolation) report.getViolations().iterator().next();
        assertEquals(BP, violation.getNode());
        assertEquals(CELL_DIFFERENTIATION_SHAPE,
                violation.getExplanations().iterator().next().getShape());
    }

    @Test
    public void retainsDirectWrongRangeFailure() {
        Model model = createBaseModel();
        model.getResource(MF).addProperty(model.getProperty(PART_OF), model.getResource(CC));

        ShexValidationReport report = validator.runShapeMapValidation(model);

        assertFalse(report.isConformant());
        assertEquals(1, report.getViolations().size());
        ShexViolation violation = (ShexViolation) report.getViolations().iterator().next();
        assertEquals(MF, violation.getNode());
        assertEquals(CC, violation.getExplanations().iterator().next().getConstraints().iterator().next().getObject());
    }

    @Test
    public void retainsFailureWhenObjectFailsUnrelatedShape() {
        ShexValidationReport report = new ShexValidationReport();
        report.addViolation(violation(MF, MOLECULAR_FUNCTION_SHAPE,
                rangeConstraint(BP, BIOLOGICAL_PROCESS_SHAPE)));
        report.addViolation(violation(BP, CELLULAR_COMPONENT_SHAPE,
                rangeConstraint(CC, CELLULAR_COMPONENT_SHAPE)));

        report.suppressCascadingViolations();

        assertEquals(2, report.getViolations().size());
    }

    @Test
    public void retainsCyclicFailures() {
        ShexValidationReport report = new ShexValidationReport();
        report.addViolation(violation(MF, MOLECULAR_FUNCTION_SHAPE,
                rangeConstraint(BP, BIOLOGICAL_PROCESS_SHAPE)));
        report.addViolation(violation(BP, BIOLOGICAL_PROCESS_SHAPE,
                rangeConstraint(MF, MOLECULAR_FUNCTION_SHAPE)));

        report.suppressCascadingViolations();

        assertEquals(2, report.getViolations().size());
    }

    private static Model createBaseModel() {
        Model model = ModelFactory.createDefaultModel();
        Resource mf = model.createResource(MF);
        Resource bp = model.createResource(BP);
        Resource cc = model.createResource(CC);
        Resource mfClass = model.createResource("http://example.org/MFClass");
        Resource bpClass = model.createResource("http://example.org/BPClass");
        Resource ccClass = model.createResource("http://example.org/CCClass");
        Property contributor = model.createProperty("http://purl.org/dc/elements/1.1/contributor");
        Property date = model.createProperty("http://purl.org/dc/elements/1.1/date");
        RDFNode text = model.createTypedLiteral("test", XSDDatatype.XSDstring);

        mfClass.addProperty(RDF.type, OWL.Class)
                .addProperty(RDFS.subClassOf, model.createResource("http://purl.obolibrary.org/obo/GO_0003674"));
        bpClass.addProperty(RDF.type, OWL.Class)
                .addProperty(RDFS.subClassOf, model.createResource("http://purl.obolibrary.org/obo/GO_0008150"));
        ccClass.addProperty(RDF.type, OWL.Class)
                .addProperty(RDFS.subClassOf, model.createResource("http://purl.obolibrary.org/obo/GO_0005575"));

        addEntityProperties(mf, mfClass, contributor, date, text);
        addEntityProperties(bp, bpClass, contributor, date, text);
        addEntityProperties(cc, ccClass, contributor, date, text);
        return model;
    }

    private static void addEntityProperties(Resource entity, Resource entityClass, Property contributor,
                                            Property date, RDFNode text) {
        entity.addProperty(RDF.type, OWL2.NamedIndividual)
                .addProperty(RDF.type, entityClass)
                .addProperty(contributor, text)
                .addProperty(date, text);
    }

    private static ShexViolation violation(String node, String shape, ShexConstraint constraint) {
        ShexExplanation explanation = new ShexExplanation();
        explanation.setShape(shape);
        explanation.addConstraint(constraint);
        ShexViolation violation = new ShexViolation(node);
        violation.addExplanation(explanation);
        return violation;
    }

    private static ShexConstraint rangeConstraint(String object, String expectedShape) {
        Set<String> expectedShapes = new HashSet<String>(Collections.singleton(expectedShape));
        return new ShexConstraint(object, "example:property", expectedShapes,
                Collections.<String>emptySet(), Collections.<String>emptySet());
    }
}
