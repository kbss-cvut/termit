package cz.cvut.kbss.termit.service.repository.migration;

import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.dto.IriMigrationParams;
import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.persistence.dao.IriMigrationDao;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeTrackingContextResolver;
import cz.cvut.kbss.termit.persistence.namespace.VocabularyNamespaceResolver;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;

import java.net.URI;
import java.util.Objects;

/**
 * Class whose instance is representing a single IRI migration.
 *
 * @implNote A helper class for eliminating excessive method parameters and referencing class fields instead.
 * @see IriMigrationRepositoryService
 */
public class IriMigrationAction implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(IriMigrationAction.class);
    private final IriMigrationRepositoryService iriMigrationRepositoryService;
    private final IriMigrationDao iriMigrationDao;
    private final ChangeTrackingContextResolver changeTrackingContextResolver;
    private final VocabularyNamespaceResolver vocabularyNamespaceResolver;
    private final VocabularyRepositoryService vocabularyRepositoryService;

    @Nullable
    private final Asset<?> changedAsset;

    private final IriMigrationType migrationType;
    private final IriMigrationPair iris;
    private final IriMigrationParams params;

    IriMigrationAction(
            IriMigrationRepositoryService iriMigrationRepositoryService,
            IriMigrationDao iriMigrationDao,
            ChangeTrackingContextResolver changeTrackingContextResolver,
            VocabularyNamespaceResolver vocabularyNamespaceResolver,
            VocabularyRepositoryService vocabularyRepositoryService,
            ChangeRecordDao changeRecordDao,
            @Nullable Asset<?> changedAsset,
            IriMigrationType migrationType,
            IriMigrationPair iris,
            IriMigrationParams params) {
        this.iriMigrationRepositoryService = iriMigrationRepositoryService;
        this.iriMigrationDao = Objects.requireNonNull(iriMigrationDao);
        this.changeTrackingContextResolver = Objects.requireNonNull(changeTrackingContextResolver);
        this.vocabularyNamespaceResolver = vocabularyNamespaceResolver;
        this.vocabularyRepositoryService = vocabularyRepositoryService;
        this.changedAsset = changedAsset;
        iriMigrationDao.detach(changedAsset);
        this.migrationType = Objects.requireNonNull(migrationType);
        this.iris = Objects.requireNonNull(iris);
        this.params = Objects.requireNonNull(params);
    }

    /** Perform the migration */
    @Override
    public void run() {
        LOG.trace("Executing IRI migration: {}", iris);
        validateMigration();
        iriMigrationDao.migrateIdentifier(iris); // replace every identifier occurrence
        LOG.trace("Migrating graphs after IRI migration: {}", iris);
        migrateChangeRecordsGraph();
        migrateOccurrenceGraph();
        migrateVocabularyNamespace(params.preferredNamespaceUri());
        // changes to entity identifiers and graph identifiers were made
        iriMigrationDao.evictCache();
    }

    private void ensureNotExists(URI resource) {
        if (iriMigrationDao.getEntityTypes(resource).findAny().isPresent()) {
            throw new InvalidParameterException("Resource " + Utils.uriToString(resource) + " already exists!");
        }
    }

    private void validateMigration() {
        ensureNotExists(iris.newIri());
        switch (migrationType) {
            case TERM -> validateTermMigration();
            case VOCABULARY -> validateVocabularyMigration();
            case CUSTOM_ATTRIBUTE -> {
                /* no validation */
            }
        }
    }

    private void validateVocabularyMigration() {
        if (!(changedAsset instanceof Vocabulary) || !changedAsset.getUri().equals(iris.originalIri())) {
            throw new InvalidParameterException("Changed asset is not expected Vocabulary!");
        }
    }

    private void validateTermMigration() {
        if (!(changedAsset instanceof Term term) || !changedAsset.getUri().equals(iris.originalIri())) {
            throw new InvalidParameterException("Changed asset is not expected Term!");
        }

        // ensure the new term IRI is inside vocabulary namespace
        final String vocabularyNamespace = vocabularyNamespaceResolver.resolveNamespace(term.getVocabulary());
        if (!term.getUri().toString().startsWith(vocabularyNamespace)) {
            throw new InvalidParameterException("New Term IRI " + Utils.uriToString(term.getUri())
                    + " does not start with Vocabulary namespace <" + vocabularyNamespace + ">");
        }
    }

    /**
     * Moves occurrence graph to the new IRI if there is an occurrence graph for the original IRI.
     *
     * @see TermOccurrence#resolveContext(URI)
     */
    private void migrateOccurrenceGraph() {
        URI originalGraph = TermOccurrence.resolveContext(iris.originalIri());
        URI newGraph = TermOccurrence.resolveContext(iris.newIri());
        iriMigrationDao.moveGraph(originalGraph, newGraph);
    }

    /**
     * For {@link IriMigrationType#VOCABULARY} moves the change tracking context to the new IRI
     *
     * @see ChangeTrackingContextResolver#resolveChangeTrackingContext(Asset)
     */
    private void migrateChangeRecordsGraph() {
        if (migrationType != IriMigrationType.VOCABULARY) {
            return;
        }

        assert changedAsset instanceof Vocabulary;
        URI originalGraph = changeTrackingContextResolver.resolveChangeTrackingContext(changedAsset);
        changedAsset.setUri(iris.newIri()); // temporarily set new URI so that correct tracking context is resolved
        URI newGraph = changeTrackingContextResolver.resolveChangeTrackingContext(changedAsset);
        changedAsset.setUri(iris.originalIri());
        iriMigrationDao.moveGraph(originalGraph, newGraph);
    }

    private void migrateVocabularyNamespace(final URI newNamespaceUri) {
        if (newNamespaceUri == null || migrationType != IriMigrationType.VOCABULARY) {
            return;
        }
        final String newNamespace = newNamespaceUri.toString();

        assert changedAsset instanceof Vocabulary;
        final Vocabulary vocabulary = (Vocabulary) changedAsset;

        if (vocabulary.getPreferredNamespaceUri().equals(newNamespace)) {
            // new namespace is the same as the current one
            return;
        }
        final String originalNamespace = vocabulary.getPreferredNamespaceUri();

        LOG.info("Migrating vocabulary namespace '{}' -> '{}'", originalNamespace, newNamespace);

        // we are already after the IRI migration, using new IRI
        final Vocabulary migratedVocabulary = vocabularyRepositoryService.findRequired(iris.newIri());
        migratedVocabulary.setPreferredNamespaceUri(newNamespace);
        vocabularyRepositoryService.update(migratedVocabulary);
        migrateAllTerms(originalNamespace, newNamespace);
    }

    private void migrateAllTerms(final String originalNamespace, final String newNamespace) {
        LOG.info("Migrating identifiers of all terms from vocabulary {}", Utils.uriToString(iris.newIri()));
        assert migrationType == IriMigrationType.VOCABULARY;
        final IriMigrationParams termMigrationParams = new IriMigrationParams(null);
        iriMigrationDao
                .findAllTerms(iris.newIri())
                .map(originalTermUri -> mapTermUri(originalTermUri, originalNamespace, newNamespace))
                .filter(Objects::nonNull)
                .forEach(termMigration ->
                        // calling internal to stay in the same transaction
                        iriMigrationRepositoryService.migrateIdentifierInternal(
                                termMigration, IriMigrationType.TERM, termMigrationParams));
    }

    private static IriMigrationPair mapTermUri(URI originalTermUri, String originalNamespace, String newNamespace) {
        final String originalTermUriStr = originalTermUri.toString();
        if (originalTermUriStr.startsWith(newNamespace)) {
            // already correct namespace
            return null;
        }
        if (!originalTermUriStr.startsWith(originalNamespace)) {
            throw new InvalidParameterException("Term identifier " + Utils.uriToString(originalTermUri)
                    + " is not in vocabulary namespace " + originalNamespace);
        }
        final String newTermUriStr = newNamespace + originalTermUriStr.substring(0, originalNamespace.length());
        return new IriMigrationPair(originalTermUri, URI.create(newTermUriStr));
    }
}
