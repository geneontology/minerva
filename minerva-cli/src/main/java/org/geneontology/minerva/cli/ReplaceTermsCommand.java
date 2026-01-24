package org.geneontology.minerva.cli;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sail.NotifyingSail;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.nativerdf.NativeStore;
import org.geneontology.minerva.MolecularModelManager;
import org.geneontology.minerva.curie.CurieHandler;
import org.geneontology.minerva.curie.DefaultCurieHandler;
import org.geneontology.minerva.util.SailMutationCounter;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.UpdateExecutionException;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.semanticweb.owlapi.model.IRI;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class ReplaceTermsCommand {

    private static final Logger LOGGER = Logger.getLogger(ReplaceTermsCommand.class);

    protected final static String classReplacementUpdateTemplate;

    static {
        try {
            classReplacementUpdateTemplate = IOUtils.toString(Objects.requireNonNull(ReplaceObsoleteReferencesCommand.class.getResourceAsStream("class-replacement.ru")), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(new FatalTermReplacementError("Could not load SPARQL update from jar", e));
        }
    }

    protected final static String complementsUpdateTemplate;

    static {
        try {
            complementsUpdateTemplate = IOUtils.toString(Objects.requireNonNull(ReplaceObsoleteReferencesCommand.class.getResourceAsStream("class-replacement-complements.ru")), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(new FatalTermReplacementError("Could not load SPARQL update from jar", e));
        }
    }

    protected final static String objectPropertiesUpdateTemplate;

    static {
        try {
            objectPropertiesUpdateTemplate = IOUtils.toString(Objects.requireNonNull(ReplaceObsoleteReferencesCommand.class.getResourceAsStream("object-property-replacement.ru")), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(new FatalTermReplacementError("Could not load SPARQL update from jar", e));
        }
    }

    private static final CurieHandler curieHandler = DefaultCurieHandler.getDefaultHandler();

    public static void run(String journalFilePath, String replacementClassesPath, String replacementPropertiesPath) throws FatalTermReplacementError {
        if (journalFilePath == null) {
            throw new FatalTermReplacementError("No journal file was configured.");
        }
        String indexes = "spoc,posc,cosp"; //FIXME review for appropriate indexes
        SailRepository repository;
        try {
            repository = new SailRepository(new NativeStore(new File(journalFilePath), indexes));
        } catch (RepositoryException e) {
            throw new FatalTermReplacementError("Could not initialize SAIL repository for database.", e);
        }
        SailMutationCounter counter = new SailMutationCounter();
        String classReplacements = formatAsSPARQLValuesList(loadTermReplacementFromFile(replacementClassesPath));
        String objectPropertyReplacements = formatAsSPARQLValuesList(loadTermReplacementFromFile(replacementPropertiesPath));
        String classesSparqlUpdate = classReplacementUpdateTemplate.replace("%%%values%%%", classReplacements);
        String complementsSparqlUpdate = complementsUpdateTemplate.replace("%%%values%%%", classReplacements);
        String objectPropertiesSparqlUpdate = objectPropertiesUpdateTemplate.replace("%%%values%%%", objectPropertyReplacements);
        try {
            LOGGER.debug("Will apply SPARQL update:\n" + classesSparqlUpdate);
            applySPARQLUpdate(repository, classesSparqlUpdate, Optional.of(counter));
            LOGGER.debug("Will apply SPARQL update:\n" + complementsSparqlUpdate);
            applySPARQLUpdate(repository, complementsSparqlUpdate, Optional.of(counter));
            LOGGER.debug("Will apply SPARQL update:\n" + objectPropertiesSparqlUpdate);
            applySPARQLUpdate(repository, objectPropertiesSparqlUpdate, Optional.of(counter));
            int changes = counter.mutationCount();
            LOGGER.info("Successfully applied database updates to replace terms: " + changes + " changes");
        } catch (RepositoryException | UpdateExecutionException | MalformedQueryException e) {
            throw new FatalTermReplacementError("Failed to apply SPARQL update.", e);
        }
    }

    private static Set<Pair<Pair<IRI, String>, Pair<IRI, String>>> loadTermReplacementFromFile(String path) throws FatalTermReplacementError {
        final Set<Pair<Pair<IRI, String>, Pair<IRI, String>>> pairs;
        try (FileReader fr = new FileReader(path);
             BufferedReader br = new BufferedReader(fr)) {
            pairs = br.lines()
                    .skip(1) //skip header
                    .map(s -> s.split("\t", -1))
                    .filter(items -> items.length > 1)
                    .map(items -> Pair.of(Pair.of(curieToIRI(items[0].trim()), items[0].trim()), Pair.of(curieToIRI(items[1].trim()), items[1].trim())))
                    .filter(pair -> pair.getLeft().getLeft().isPresent() && pair.getRight().getLeft().isPresent())
                    .map(pair -> Pair.of(Pair.of(pair.getLeft().getLeft().get(), pair.getLeft().getRight()), Pair.of(pair.getRight().getLeft().get(), pair.getRight().getRight())))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new FatalTermReplacementError("Could not read term replacements file.", e);
        }
        return pairs;
    }

    private static String formatAsSPARQLValuesList(Set<Pair<Pair<IRI, String>, Pair<IRI, String>>> pairs) {
        return pairs.stream()
                .map(pair -> "(<" + pair.getLeft().getLeft().toString() + "> " + "\"" + pair.getLeft().getRight() + "\"" + " <" + pair.getRight().getLeft().toString() + "> " + "\"" + pair.getRight().getRight() + "\"" + ")")
                .collect(Collectors.joining(" "));
    }

    protected static void applySPARQLUpdate(SailRepository repository, String update, Optional<SailMutationCounter> counter) throws RepositoryException, UpdateExecutionException, MalformedQueryException {
        try (SailRepositoryConnection connection = repository.getConnection()) {
            final Repository repo = connection.getRepository();
            NotifyingSail notifyingSail;
            if (repo instanceof SailRepository) {
                Sail sail = ((SailRepository) repo).getSail();
                if (sail instanceof NotifyingSail) {
                    notifyingSail = (NotifyingSail) sail;
                } else {
                    notifyingSail = null;
                }
            } else {
                notifyingSail = null;
            }
            connection.begin();
            counter.ifPresent(c -> {
                if (notifyingSail != null) {
                    notifyingSail.addSailChangedListener(c);
                }
            });
            try {
                connection.prepareUpdate(QueryLanguage.SPARQL, update).execute();
            } catch (UpdateExecutionException | RepositoryException | MalformedQueryException e) {
                connection.rollback();
                throw e;
            }
            counter.ifPresent(c -> {
                if (notifyingSail != null) {
                    notifyingSail.removeSailChangedListener(c);
                }
            });
        }
    }

    private static Optional<IRI> curieToIRI(String curie) {
        try {
            return Optional.of(curieHandler.getIRI(curie));
        } catch (MolecularModelManager.UnknownIdentifierException e) {
            LOGGER.warn("Unable to expand replaced_by value found in replacements file into an IRI: " + curie);
            return Optional.empty();
        }
    }

    public static class FatalTermReplacementError extends Exception {

        public FatalTermReplacementError(String message) {
            super(message);
        }

        public FatalTermReplacementError(String message, Exception cause) {
            super(message, cause);
        }

    }

}
