package cz.cvut.kbss.termit.service.repository.migration;

import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.dto.IriMigrationParams;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.changetracking.IdentifierChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.IriMigrationDao;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeTrackingContextResolver;
import cz.cvut.kbss.termit.persistence.namespace.VocabularyNamespaceResolver;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class IriMigrationRepositoryService {
    private static final Logger LOG = LoggerFactory.getLogger(IriMigrationRepositoryService.class);
    private final IriMigrationDao iriMigrationDao;
    private final ChangeTrackingContextResolver changeTrackingContextResolver;
    private final TermRepositoryService termRepositoryService;
    private final VocabularyRepositoryService vocabularyRepositoryService;
    private final VocabularyNamespaceResolver vocabularyNamespaceResolver;
    private final ChangeRecordDao changeRecordDao;
    private final SecurityUtils securityUtils;

    public IriMigrationRepositoryService(IriMigrationDao iriMigrationDao,
                                         ChangeTrackingContextResolver changeTrackingContextResolver,
                                         TermRepositoryService termRepositoryService,
                                         VocabularyRepositoryService vocabularyRepositoryService,
                                         VocabularyNamespaceResolver vocabularyNamespaceResolver,
                                         ChangeRecordDao changeRecordDao, SecurityUtils securityUtils) {
        this.iriMigrationDao = iriMigrationDao;
        this.changeTrackingContextResolver = changeTrackingContextResolver;
        this.termRepositoryService = termRepositoryService;
        this.vocabularyRepositoryService = vocabularyRepositoryService;
        this.vocabularyNamespaceResolver = vocabularyNamespaceResolver;
        this.changeRecordDao = changeRecordDao;
        this.securityUtils = securityUtils;
    }

    /**
     * Performs IRI migration specified by the {@link IriMigrationPair} and optionally {@link IriMigrationParams}.
     * The operation is performed asynchronously within a standalone transaction.
     *
     * @param iriMigrationPair The pair of IRIs to migrate
     * @param migrationType the expected type of the entity with the original IRI
     * @param params additional parameters to customize the migration process
     * @throws NotFoundException when the entity with the original IRI and the expected type does not exist
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void migrateIdentifier(IriMigrationPair iriMigrationPair, IriMigrationType migrationType,
                                  IriMigrationParams params) {
        migrateIdentifierInternal(iriMigrationPair, migrationType, params);
    }

    /**
     * @see #migrateIdentifier(IriMigrationPair, IriMigrationType, IriMigrationParams)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void migrateIdentifierInternal(IriMigrationPair iris, IriMigrationType migrationType, IriMigrationParams params) {
        ensureExists(iris, migrationType);
        final Asset<?> changedAsset = getChangedAsset(iris, migrationType);

        new IriMigrationAction(
                this,
                iriMigrationDao,
                changeTrackingContextResolver,
                vocabularyNamespaceResolver,
                vocabularyRepositoryService,
                changeRecordDao,
                changedAsset,
                migrationType,
                iris,
                params).run();
        createChangeRecord(iris, changedAsset);
    }

    private Asset<?> getChangedAsset(IriMigrationPair pair, IriMigrationType migrationType) {
        return switch (migrationType) {
            case TERM -> termRepositoryService.findRequired(pair.originalIri());
            case VOCABULARY ->  vocabularyRepositoryService.findRequired(pair.originalIri());
            case CUSTOM_ATTRIBUTE -> null;
        };
    }

    /**
     * Ensures that the entity with the original IRI exists and is of the type expected by the migration type.
     */
    private void ensureExists(IriMigrationPair iris, IriMigrationType migrationType) {
        if (iriMigrationDao.getEntityTypes(iris.originalIri()).noneMatch(migrationType.getEntityType()::equals)) {
            throw new NotFoundException("Entity " + Utils.uriToString(iris.originalIri()) + " of type " +
                    Utils.uriToString(migrationType.getEntityType()) + " not found.");
        }
    }

    private void createChangeRecord(IriMigrationPair iris, Asset<?> changedAsset) {
        if (changedAsset == null) {
            LOG.debug("Skipping identifier migration change record creation for migration: {}", iris);
            return;
        }

        IdentifierChangeRecord record = new IdentifierChangeRecord();
        // the record must be associated with the new (current) entity identifier
        record.setChangedEntity(iris.newIri());
        record.setTimestamp(Instant.now());
        record.setAuthor(securityUtils.getCurrentUser().toUser());
        record.setOriginalIdentifier(iris.originalIri());

//        changeRecordDao.persist(record, changedAsset);
        // TODO: persist
    }
}
