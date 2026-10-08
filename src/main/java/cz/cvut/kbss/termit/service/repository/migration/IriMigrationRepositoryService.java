package cz.cvut.kbss.termit.service.repository.migration;

import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.dto.IriMigrationParams;
import cz.cvut.kbss.termit.event.EvictCacheEvent;
import cz.cvut.kbss.termit.event.IriMigratedEvent;
import cz.cvut.kbss.termit.event.IriMigrationFailedEvent;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.changetracking.IdentifierChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.IriMigrationDao;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeTrackingContextResolver;
import cz.cvut.kbss.termit.persistence.namespace.VocabularyNamespaceResolver;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTaskScheduler;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTasksRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service capable of migrating IRIs of supported entities.
 *
 * @see IriMigrationType
 */
@Service
public class IriMigrationRepositoryService extends LongRunningTaskScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(IriMigrationRepositoryService.class);
    private final IriMigrationDao iriMigrationDao;
    private final ChangeTrackingContextResolver changeTrackingContextResolver;
    private final TermRepositoryService termRepositoryService;
    private final VocabularyRepositoryService vocabularyRepositoryService;
    private final VocabularyNamespaceResolver vocabularyNamespaceResolver;
    private final ChangeRecordDao changeRecordDao;
    private final ApplicationEventPublisher applicationEventPublisher;

    public IriMigrationRepositoryService(
            LongRunningTasksRegistry longRunningTasksRegistry,
            IriMigrationDao iriMigrationDao,
            ChangeTrackingContextResolver changeTrackingContextResolver,
            TermRepositoryService termRepositoryService,
            VocabularyRepositoryService vocabularyRepositoryService,
            VocabularyNamespaceResolver vocabularyNamespaceResolver,
            ChangeRecordDao changeRecordDao,
            ApplicationEventPublisher applicationEventPublisher) {
        super(longRunningTasksRegistry);
        this.iriMigrationDao = iriMigrationDao;
        this.changeTrackingContextResolver = changeTrackingContextResolver;
        this.termRepositoryService = termRepositoryService;
        this.vocabularyRepositoryService = vocabularyRepositoryService;
        this.vocabularyNamespaceResolver = vocabularyNamespaceResolver;
        this.changeRecordDao = changeRecordDao;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Performs IRI migration specified by the {@link IriMigrationPair} and optionally {@link IriMigrationParams}. The
     * operation is performed asynchronously within a standalone transaction.
     *
     * @param iriMigrationPair The pair of IRIs to migrate
     * @param migrationType the expected type of the entity with the original IRI
     * @param params additional parameters to customize the migration process
     * @param author the user performing the migration, recorded as the author of the created change records
     * @param task the long-running task tracking the migration
     * @throws NotFoundException when the entity with the original IRI and the expected type does not exist
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void migrateIdentifier(
            IriMigrationPair iriMigrationPair,
            IriMigrationType migrationType,
            IriMigrationParams params,
            User author,
            IriMigrationLongRunningTask task) {
        try {
            task.markStarted();
            notifyMigrationTaskChanged(task);
            migrateIdentifierInternal(iriMigrationPair, migrationType, params, author);

            applicationEventPublisher.publishEvent(new IriMigratedEvent(this, migrationType, iriMigrationPair));
            LOG.debug("Evicting all application caches, Identifier migrated {}", iriMigrationPair);
            applicationEventPublisher.publishEvent(new EvictCacheEvent(this));
        } catch (TermItException e) {
            applicationEventPublisher.publishEvent(new IriMigrationFailedEvent(this, e.getMessage(), e.getMessageId()));
            throw e;
        } catch (RuntimeException e) {
            applicationEventPublisher.publishEvent(new IriMigrationFailedEvent(this, e.getMessage(), null));
            throw e;
        } finally {
            task.markAsDone();
            notifyMigrationTaskChanged(task);
        }
    }

    /**
     * @see #migrateIdentifier(IriMigrationPair, IriMigrationType, IriMigrationParams, User,
     *     IriMigrationLongRunningTask)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void migrateIdentifierInternal(
            IriMigrationPair iris, IriMigrationType migrationType, IriMigrationParams params, User author) {
        ensureExists(iris, migrationType);
        final Asset<?> changedAsset = getChangedAsset(iris, migrationType);

        new IriMigrationAction(
                        this,
                        iriMigrationDao,
                        changeTrackingContextResolver,
                        vocabularyNamespaceResolver,
                        changeRecordDao,
                        author,
                        changedAsset,
                        migrationType,
                        iris,
                        params)
                .run();
        createChangeRecord(iris, changedAsset, author);
    }

    private Asset<?> getChangedAsset(IriMigrationPair pair, IriMigrationType migrationType) {
        return switch (migrationType) {
            case TERM -> termRepositoryService.findRequired(pair.originalIri());
            case VOCABULARY -> vocabularyRepositoryService.findRequired(pair.originalIri());
            case CUSTOM_ATTRIBUTE -> null;
        };
    }

    /** Ensures that the entity with the original IRI exists and is of the type expected by the migration type. */
    private void ensureExists(IriMigrationPair iris, IriMigrationType migrationType) {
        if (!iriMigrationDao.getEntityTypes(iris.originalIri()).contains(migrationType.getEntityType())) {
            throw new NotFoundException("Entity " + Utils.uriToString(iris.originalIri()) + " of type "
                    + Utils.uriToString(migrationType.getEntityType()) + " not found.");
        }
    }

    private void createChangeRecord(IriMigrationPair iris, Asset<?> changedAsset, User author) {
        if (changedAsset == null) {
            LOG.debug("Skipping identifier migration change record creation for migration: {}", iris);
            return;
        }

        IdentifierChangeRecord record = new IdentifierChangeRecord();
        // the record must be associated with the new (current) entity identifier
        record.setChangedEntity(iris.newIri());
        record.setTimestamp(Instant.now());
        record.setAuthor(author);
        record.setOriginalIdentifier(iris.originalIri());
        record.setNewIdentifier(iris.newIri());

        assert changedAsset.getUri().equals(iris.originalIri());
        try {
            changedAsset.setUri(iris.newIri());
            // temporarily changing the URI to allow correct
            changeRecordDao.persist(record, changedAsset);
        } finally {
            changedAsset.setUri(iris.originalIri());
        }
    }

    public void notifyMigrationTaskChanged(IriMigrationLongRunningTask task) {
        notifyTaskChanged(task);
    }
}
