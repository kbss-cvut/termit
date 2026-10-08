package cz.cvut.kbss.termit.service.repository.migration;

import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.dto.IriMigrationParams;
import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Vocabulary_;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.util.SupportsSnapshots;
import cz.cvut.kbss.termit.persistence.dao.IriMigrationDao;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeTrackingContextResolver;
import cz.cvut.kbss.termit.persistence.namespace.VocabularyNamespaceResolver;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
    private final ChangeRecordDao changeRecordDao;
    private final User author;

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
            ChangeRecordDao changeRecordDao,
            User author,
            @Nullable Asset<?> changedAsset,
            IriMigrationType migrationType,
            IriMigrationPair iris,
            IriMigrationParams params) {
        this.iriMigrationRepositoryService = Objects.requireNonNull(iriMigrationRepositoryService);
        this.iriMigrationDao = Objects.requireNonNull(iriMigrationDao);
        this.changeTrackingContextResolver = Objects.requireNonNull(changeTrackingContextResolver);
        this.vocabularyNamespaceResolver = Objects.requireNonNull(vocabularyNamespaceResolver);
        this.changeRecordDao = Objects.requireNonNull(changeRecordDao);
        this.author = Objects.requireNonNull(author);
        this.changedAsset = changedAsset;
        if (changedAsset != null) {
            iriMigrationDao.detach(changedAsset);
        }
        this.migrationType = Objects.requireNonNull(migrationType);
        this.iris = Objects.requireNonNull(iris);
        this.params = Objects.requireNonNull(params);
    }

    /** Perform the migration */
    @Override
    public void run() {
        LOG.trace("Executing IRI migration: {}", iris);
        validateMigration();
        // must migrate namespace and all terms as first, because JOPA does not publish transaction changes
        // to select queries with RDF4J driver
        migrateVocabularyNamespace(params.preferredNamespaceUri());
        iriMigrationDao.migrateIdentifier(iris); // replace every identifier occurrence
        LOG.trace("Migrating graphs after IRI migration: {}", iris);
        migrateVocabularyGraph();
        migrateChangeRecordsGraph();
        migrateOccurrenceGraph();
        // changes to entity identifiers and graph identifiers were made
        iriMigrationDao.evictCache();
    }

    private void ensureNotExists(URI resource) {
        final Optional<URI> type = iriMigrationDao.getEntityTypes(resource).stream()
                .filter(t -> !t.toString().equals(RDFS.RESOURCE))
                .findAny();
        if (type.isPresent()) {
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

    private void ensureNotSnapshot(SupportsSnapshots supportsSnapshots) {
        if (supportsSnapshots.isSnapshot()) {
            throw new InvalidParameterException("Identifier of snapshot cannot be migrated!");
        }
    }

    private void validateVocabularyMigration() {
        if (!(changedAsset instanceof Vocabulary vocabulary)
                || !changedAsset.getUri().equals(iris.originalIri())) {
            throw new InvalidParameterException("Changed asset is not expected Vocabulary!");
        }
        ensureNotSnapshot(vocabulary);
    }

    private void validateTermMigration() {
        if (!(changedAsset instanceof Term term) || !changedAsset.getUri().equals(iris.originalIri())) {
            throw new InvalidParameterException("Changed asset is not expected Term!");
        }
        ensureNotSnapshot(term);

        // ensure the new term IRI is inside vocabulary namespace
        final String vocabularyNamespace = Optional.ofNullable(params.preferredNamespaceUri())
                .map(URI::toString)
                .orElseGet(() -> vocabularyNamespaceResolver.resolveNamespace(term.getVocabulary()));
        if (!iris.newIri().toString().startsWith(vocabularyNamespace)) {
            throw new InvalidParameterException("New Term IRI " + Utils.uriToString(iris.newIri())
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

    private void migrateVocabularyGraph() {
        if (migrationType != IriMigrationType.VOCABULARY) {
            return;
        }
        assert changedAsset instanceof Vocabulary;
        iriMigrationDao.moveGraph(iris.originalIri(), iris.newIri());
    }

    /**
     * For {@link IriMigrationType#VOCABULARY} changes the preferred namespace of the vocabulary and migrates the
     * identifiers of all its terms to the new namespace.
     *
     * @implNote Entity manager is flushed and cleared at the end to write entity changes to the repository
     */
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

        // executed before vocabulary IRI migration!
        iriMigrationDao.updatePreferredNamespace(vocabulary.getUri(), newNamespace);
        createNamespaceChangeRecord(originalNamespace, newNamespace);
        migrateAllTerms(originalNamespace, newNamespace);
    }

    private void createNamespaceChangeRecord(String originalNamespace, String newNamespace) {
        assert changedAsset != null;

        final UpdateChangeRecord record = new UpdateChangeRecord();
        record.setChangedEntity(iris.newIri());
        record.setChangedAttribute(Vocabulary_.preferredNamespaceUriPropertyIRI.toURI());
        record.setOriginalValue(Set.of(originalNamespace));
        record.setNewValue(Set.of(newNamespace));
        record.setTimestamp(Utils.timestamp());
        record.setAuthor(author);

        changeRecordDao.persist(record, changedAsset);
    }

    private void migrateAllTerms(final String originalNamespace, final String newNamespace) {
        assert changedAsset instanceof Vocabulary;
        assert migrationType == IriMigrationType.VOCABULARY;

        LOG.info("Migrating identifiers of all terms from vocabulary {}", Utils.uriToString(changedAsset.getUri()));

        try (Stream<URI> terms = iriMigrationDao.findAllTerms(changedAsset.getUri())) {
            terms.map(originalTermUri -> mapTermUri(originalTermUri, originalNamespace, newNamespace))
                    .filter(Objects::nonNull)
                    .forEach(termMigration ->
                            // calling internal to stay in the same transaction
                            iriMigrationRepositoryService.migrateIdentifierInternal(
                                    termMigration, IriMigrationType.TERM, params, author));
        }
        iriMigrationDao.flushChanges();
    }

    private static IriMigrationPair mapTermUri(URI originalTermUri, String originalNamespace, String newNamespace) {
        final String originalTermUriStr = originalTermUri.toString();
        if (!originalTermUriStr.startsWith(originalNamespace)) {
            throw new InvalidParameterException("Term identifier " + Utils.uriToString(originalTermUri)
                    + " is not in the original vocabulary namespace " + originalNamespace);
        }

        final String newTermUriStr =
                newNamespace + removeNamespace(originalTermUriStr, originalNamespace, newNamespace);
        if (newTermUriStr.equals(originalTermUriStr)) {
            // term is already in correct namespace
            return null;
        }

        return new IriMigrationPair(originalTermUri, URI.create(newTermUriStr));
    }

    private static String removeNamespace(String termUri, String originalNamespace, String newNamespace) {
        if (termUri.startsWith(newNamespace) && newNamespace.startsWith(originalNamespace)) {
            // originalNamespace: slovník/pojem/
            // pojem:             slovník/pojem/special/mujPojem
            // newNamespace:      slovník/pojem/special
            // -> newPojem        slovník/pojem/special/mujPojem
            return termUri.substring(newNamespace.length());
        }

        return termUri.substring(originalNamespace.length());
    }
}
