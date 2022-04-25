package org.geneontology.minerva.cli;

import com.bigdata.rdf.sail.BigdataSail;
import com.bigdata.rdf.sail.BigdataSailRepository;
import org.apache.log4j.Logger;
import org.geneontology.minerva.MolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.util.BlazegraphMutationCounter;
import org.obolibrary.robot.CatalogXmlIRIMapper;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.UpdateExecutionException;
import org.openrdf.repository.RepositoryException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.geneontology.minerva.cli.ReplaceTermsCommand.classReplacementUpdateTemplate;
import static org.geneontology.minerva.cli.ReplaceTermsCommand.complementsUpdateTemplate;

public class ReplaceObsoleteReferencesCommand {

    private static final Logger LOGGER = Logger.getLogger(ReplaceObsoleteReferencesCommand.class);

    private static final OWLAnnotationProperty termReplacedBy = OWLManager.getOWLDataFactory()
            .getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0100001"));

    private static final OWLAnnotationProperty owlDeprecated = OWLManager.getOWLDataFactory().getOWLDeprecated();
    private static final OWLLiteral literalTrue = OWLManager.getOWLDataFactory().getOWLLiteral(true);
    private static final OWLLiteral literalFalse = OWLManager.getOWLDataFactory().getOWLLiteral(false);

    private static final CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();

    public static void run(String ontologyIRI, String catalogPath, String journalFilePath) throws FatalReplaceObsoleteReferencesError {
        if (journalFilePath == null) {
            throw new FatalReplaceObsoleteReferencesError("No journal file was configured.");
        }
        if (ontologyIRI == null) {
            throw new FatalReplaceObsoleteReferencesError("No ontology IRI was configured.");
        }
        final OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        if (catalogPath != null) {
            try {
                manager.getIRIMappers().set(new CatalogXmlIRIMapper(catalogPath));
            } catch (IOException e) {
                throw new FatalReplaceObsoleteReferencesError("Could not load catalog file from " + catalogPath, e);
            }
        }
        final OWLOntology tbox;
        try {
            tbox = manager.loadOntology(IRI.create(ontologyIRI));
        } catch (OWLOntologyCreationException e) {
            throw new FatalReplaceObsoleteReferencesError("Could not load tbox ontology from " + ontologyIRI, e);
        }
        Properties properties = new Properties();
        try {
            properties.load(CommandLineInterface.class.getResourceAsStream("/org/geneontology/minerva/blazegraph.properties"));
        } catch (IOException e) {
            throw new FatalReplaceObsoleteReferencesError("Could not read blazegraph properties resource from jar file.");
        }
        properties.setProperty(com.bigdata.journal.Options.FILE, journalFilePath);
        BigdataSail sail = new BigdataSail(properties);
        BigdataSailRepository repository = new BigdataSailRepository(sail);
        try {
            repository.initialize();
        } catch (RepositoryException e) {
            throw new FatalReplaceObsoleteReferencesError("Could not initialize SAIL repository for database.", e);
        }
        BlazegraphMutationCounter counter = new BlazegraphMutationCounter();
        String replacements = createReplacementsValuesList(tbox);
        String sparqlUpdate = classReplacementUpdateTemplate.replace("%%%values%%%", replacements);
        String complementsSparqlUpdate = complementsUpdateTemplate.replace("%%%values%%%", replacements);
        try {
            LOGGER.debug("Will apply SPARQL update:\n" + sparqlUpdate);
            ReplaceTermsCommand.applySPARQLUpdate(repository, sparqlUpdate, Optional.of(counter));
            LOGGER.debug("Will apply SPARQL update:\n" + complementsSparqlUpdate);
            ReplaceTermsCommand.applySPARQLUpdate(repository, complementsSparqlUpdate, Optional.of(counter));
            int changes = counter.mutationCount();
            LOGGER.info("Successfully applied database updates to replace obsolete terms: " + changes + " changes");
        } catch (RepositoryException | UpdateExecutionException | MalformedQueryException e) {
            throw new FatalReplaceObsoleteReferencesError("Failed to apply SPARQL update.", e);
        }
    }

    public static class FatalReplaceObsoleteReferencesError extends Exception {

        public FatalReplaceObsoleteReferencesError(String message) {
            super(message);
        }

        public FatalReplaceObsoleteReferencesError(String message, Exception cause) {
            super(message, cause);
        }

    }

    private static String createReplacementsValuesList(OWLOntology ontology) {
        Set<OWLAnnotationSubject> deprecatedEntities = ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION, Imports.INCLUDED).stream()
                .filter(ax -> ax.getProperty().equals(owlDeprecated))
                .filter(ax -> ax.getValue().isLiteral() && ax.getValue().asLiteral().or(literalFalse).equals(literalTrue))
                .map(OWLAnnotationAssertionAxiom::getSubject)
                .collect(Collectors.toSet());
        String replacements = ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION, Imports.INCLUDED).stream()
                .filter(ax -> ax.getProperty().equals(termReplacedBy))
                .filter(ax -> ax.getSubject().isIRI())
                .filter(ax -> deprecatedEntities.contains(ax.getSubject()))
                .map(ReplaceObsoleteReferencesCommand::annotationToSPARQLValuesPair)
                .collect(Collectors.joining(" "));
        return replacements;
    }

    private static String annotationToSPARQLValuesPair(OWLAnnotationAssertionAxiom axiom) {
        String subjectIRI = subjectToIRI(axiom.getSubject());
        String subjectCURIE = curieHandler.getCuri(IRI.create(subjectIRI));
        Optional<String> valueIRI = valueToIRI(axiom.getValue());
        return valueIRI.map(v -> "(<" + subjectIRI + "> " + "\"" + subjectCURIE + "\"" + " <" + v + "> " + "\"" + curieHandler.getCuri(IRI.create(v)) + "\"" + ")").orElse("");
    }

    private static String subjectToIRI(OWLAnnotationSubject subject) {
        return ((IRI) subject).toString();
    }

    private static Optional<String> valueToIRI(OWLAnnotationValue value) {
        if (value.isIRI()) {
            return convertOpt(value.asIRI()).map(IRI::toString);
        } else if (value.isLiteral()) {
            Optional<String> maybeCurie = convertOpt(value.asLiteral()).map(OWLLiteral::getLiteral);
            if (maybeCurie.isPresent()) {
                try {
                    return Optional.of(curieHandler.getIRI(maybeCurie.get()).toString());
                } catch (MolecularModelManager.UnknownIdentifierException e) {
                    LOGGER.warn("Unable to expand replaced_by value found in ontology into an IRI: " + value);
                    return Optional.empty();
                }
            } else {
                LOGGER.warn("Unable to expand replaced_by value found in ontology into an IRI: " + value);
                return Optional.empty();
            }
        } else {
            LOGGER.warn("Unable to expand replaced_by value found in ontology into an IRI: " + value);
            return Optional.empty();
        }
    }

    private static <T> Optional<T> convertOpt(com.google.common.base.Optional<T> googleOpt) {
        if (googleOpt.isPresent()) {
            return Optional.of(googleOpt.get());
        } else {
            return Optional.empty();
        }
    }

}
