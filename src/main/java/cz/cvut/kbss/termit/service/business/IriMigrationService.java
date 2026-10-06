package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.dto.IriMigrationParams;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.service.repository.migration.IriMigrationRepositoryService;
import cz.cvut.kbss.termit.service.repository.migration.IriMigrationType;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.service.security.authorization.TermAuthorizationService;
import cz.cvut.kbss.termit.service.security.authorization.VocabularyAuthorizationService;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Service for migrating IRIs of supported entities.
 *
 * <p>Resolves the asset whose identifier is being changed and ensures that the current user is authorized to modify it
 * before the migration is started.
 */
@Service
public class IriMigrationService {

    private static final Logger LOG = LoggerFactory.getLogger(IriMigrationService.class);
    private final IriMigrationRepositoryService repositoryService;

    private final VocabularyRepositoryService vocabularyRepositoryService;

    private final TermRepositoryService termRepositoryService;

    private final VocabularyAuthorizationService vocabularyAuthorizationService;

    private final TermAuthorizationService termAuthorizationService;

    private final SecurityUtils securityUtils;

    @Autowired
    public IriMigrationService(
            IriMigrationRepositoryService repositoryService,
            VocabularyRepositoryService vocabularyRepositoryService,
            TermRepositoryService termRepositoryService,
            VocabularyAuthorizationService vocabularyAuthorizationService,
            TermAuthorizationService termAuthorizationService,
            SecurityUtils securityUtils) {
        this.repositoryService = repositoryService;
        this.vocabularyRepositoryService = vocabularyRepositoryService;
        this.termRepositoryService = termRepositoryService;
        this.vocabularyAuthorizationService = vocabularyAuthorizationService;
        this.termAuthorizationService = termAuthorizationService;
        this.securityUtils = securityUtils;
    }

    /**
     * Migrates the identifier specified by the {@link IriMigrationPair}.
     *
     * <p>The changed asset is resolved and the authorization of the current user is verified synchronously, the
     * migration itself is then performed asynchronously.
     *
     * @param iris The pair of IRIs to migrate
     * @param migrationType Type of the entity whose identifier is being migrated
     * @param params Additional parameters to customize the migration process
     * @throws NotFoundException When the vocabulary or term with the original IRI does not exist
     * @throws AuthorizationException When the current user is not authorized to modify the changed asset
     */
    @Transactional(readOnly = true)
    public void migrateIdentifier(IriMigrationPair iris, IriMigrationType migrationType, IriMigrationParams params) {
        Objects.requireNonNull(iris);
        Objects.requireNonNull(migrationType);
        Objects.requireNonNull(params);
        validateParameters(migrationType, params);
        if (!canModifyChangedAsset(iris, migrationType)) {
            throw new AuthorizationException("User " + securityUtils.getCurrentUser()
                    + " is not authorized to migrate identifier " + Utils.uriToString(iris.originalIri()) + ".");
        }
        LOG.info("Migrating {} identifier: {}", migrationType.name(), iris);
        repositoryService.migrateIdentifier(iris, migrationType, params);
        LOG.debug("Migrated {} identifier: {}", migrationType.name(), iris);
    }

    private static void validateParameters(IriMigrationType migrationType, IriMigrationParams params) {
        if (params.preferredNamespaceUri() != null && migrationType != IriMigrationType.VOCABULARY) {
            throw new InvalidParameterException(
                    "Preferred namespace uri is not supported by migration type " + migrationType.name());
        }
    }

    /**
     * Resolves the asset whose identifier is being changed and checks whether the current user can modify it.
     *
     * <p>Custom attributes can be migrated only by administrators.
     *
     * @param iris The pair of IRIs to migrate
     * @param migrationType Type of the entity whose identifier is being migrated
     * @return {@code true} if the current user is authorized to modify the changed asset, {@code false} otherwise
     */
    private boolean canModifyChangedAsset(IriMigrationPair iris, IriMigrationType migrationType) {
        return switch (migrationType) {
            case VOCABULARY -> {
                final Vocabulary vocabulary = vocabularyRepositoryService.findRequired(iris.originalIri());
                yield vocabularyAuthorizationService.canMigrateIdentifier(vocabulary);
            }
            case TERM -> {
                final Term term = termRepositoryService.findRequired(iris.originalIri());
                yield termAuthorizationService.canMigrateIdentifier(term);
            }
            case CUSTOM_ATTRIBUTE -> securityUtils.getCurrentUser().isAdmin();
        };
    }
}
