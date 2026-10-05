package cz.cvut.kbss.termit.service.repository.migration;

import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.dto.IriMigrationParams;
import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.changetracking.IdentifierChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.IriMigrationDao;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeTrackingContextResolver;
import cz.cvut.kbss.termit.persistence.namespace.VocabularyNamespaceResolver;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Service capable of migrating IRIs of supported entities.
 *
 * @see IriMigrationType
 */
@Service
public class IriMigrationRepositoryService {
    private final IriMigrationDao iriMigrationDao;
    private final ChangeTrackingContextResolver changeTrackingContextResolver;
    private final TermRepositoryService termRepositoryService;
    private final VocabularyRepositoryService vocabularyRepositoryService;
    private final VocabularyNamespaceResolver vocabularyNamespaceResolver;
    private final ChangeRecordDao changeRecordDao;

    public IriMigrationRepositoryService(IriMigrationDao iriMigrationDao,
                                         ChangeTrackingContextResolver changeTrackingContextResolver,
                                         TermRepositoryService termRepositoryService,
                                         VocabularyRepositoryService vocabularyRepositoryService,
                                         VocabularyNamespaceResolver vocabularyNamespaceResolver,
                                         ChangeRecordDao changeRecordDao) {
        this.iriMigrationDao = iriMigrationDao;
        this.changeTrackingContextResolver = changeTrackingContextResolver;
        this.termRepositoryService = termRepositoryService;
        this.vocabularyRepositoryService = vocabularyRepositoryService;
        this.vocabularyNamespaceResolver = vocabularyNamespaceResolver;
        this.changeRecordDao = changeRecordDao;
    }

    /**
     * Performs IRI migration specified by the {@link IriMigrationPair} and optionally {@link IriMigrationParams}.
     * The operation is performed asynchronously within a standalone transaction.
     *
     * @param iriMigrationPair The pair of IRIs to migrate
     * @param params additional parameters to customize the migration process
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void migrateIdentifier(IriMigrationPair iriMigrationPair, IriMigrationParams params) {
        migrateIdentifierInternal(iriMigrationPair, params);
    }

    /**
     * @see #migrateIdentifier(IriMigrationPair, IriMigrationParams)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void migrateIdentifierInternal(IriMigrationPair iris, IriMigrationParams params) {
        final IriMigrationType migrationType = getMigrationType(iris);
        final Asset<?> changedAsset = getChangedAsset(iris, migrationType);

        new IriMigrationAction(
                this,
                iriMigrationDao,
                changeTrackingContextResolver,
                vocabularyNamespaceResolver,
                vocabularyRepositoryService,
                changedAsset,
                migrationType,
                iris,
                params).run();
        createChangeRecord(iris, changedAsset);
    }

    private void createChangeRecord(IriMigrationPair iris, Asset<?> changedAsset) {
        IdentifierChangeRecord record = new IdentifierChangeRecord();
        // TODO: fill record and persist
    }

    private Asset<?> getChangedAsset(IriMigrationPair pair, IriMigrationType migrationType) {
        return switch (migrationType) {
            case TERM -> termRepositoryService.findRequired(pair.originalIri());
            case VOCABULARY ->  vocabularyRepositoryService.findRequired(pair.originalIri());
            case CUSTOM_ATTRIBUTE -> null;
        };
    }

    private IriMigrationType getMigrationType(IriMigrationPair iris) {
        return iriMigrationDao.getEntityTypes(iris.originalIri())
                                                                  .map(IriMigrationType::fromEntityType)
                                                                  .filter(Objects::nonNull)
                                                                  .findAny()
                .orElseThrow(() ->
                        new InvalidParameterException("Identifier migration of the given entity is not allowed: " +
                                Utils.uriToString(iris.originalIri())));
    }
}
