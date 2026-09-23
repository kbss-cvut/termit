package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.termit.exception.UpdateChangeRecordRollbackException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord_;
import cz.cvut.kbss.termit.model.util.HasProperties;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRollbackDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.service.security.authorization.TermAuthorizationService;
import cz.cvut.kbss.termit.service.security.authorization.VocabularyAuthorizationService;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;

/**
 * Service capable of performing rollback of {@link UpdateChangeRecord}
 */
@Service
public class ChangeRollbackService {
    private final RollbackValidator rollbackValidator;
    private final ChangeRollbackDao rollbackDao;
    private final ChangeRecordService changeRecordService;
    private final TermRepositoryService termService;
    private final VocabularyRepositoryService vocabularyService;
    private final TermAuthorizationService termAuthorizationService;
    private final VocabularyAuthorizationService vocabularyAuthorizationService;
    private final IdentifierResolver identifierResolver;

    public ChangeRollbackService(RollbackValidator rollbackValidator,
                                 ChangeRollbackDao rollbackDao,
                                 ChangeRecordService changeRecordService,
                                 TermRepositoryService termService,
                                 VocabularyRepositoryService vocabularyService,
                                 TermAuthorizationService termAuthorizationService,
                                 VocabularyAuthorizationService vocabularyAuthorizationService,
                                 IdentifierResolver identifierResolver) {
        this.rollbackValidator = rollbackValidator;
        this.rollbackDao = rollbackDao;
        this.changeRecordService = changeRecordService;
        this.termService = termService;
        this.vocabularyService = vocabularyService;
        this.termAuthorizationService = termAuthorizationService;
        this.vocabularyAuthorizationService = vocabularyAuthorizationService;
        this.identifierResolver = identifierResolver;
    }

    /**
     * Checks whether the {@link UpdateChangeRecord} can be rolled back by the current user.
     *
     * @param record the change record to check
     * @return {@code true} when the change record can be rolled back and the current user is authorized to do so,
     *         {@code false} otherwise.
     */
    @Transactional(readOnly = true)
    public boolean canRollback(UpdateChangeRecord record) {
        final Asset<?> changedObject = resolveChangedAsset(record);
        if (changedObject == null) {
            return false; // unknown entity class
        }
        return canRollback(record, changedObject) && isModificationAuthorized(changedObject);
    }

    /**
     * Sets the {@link UpdateChangeRecord#REVERSIBLE_CHANGE_CLASS reversible class}
     * on each {@link UpdateChangeRecord} in the specified collection based on whether it
     * can be rolled back by the current user.
     *
     * @param records Change records to enrich with rollback possibility
     */
    @Transactional(readOnly = true)
    public void withReversibleType(Collection<AbstractChangeRecord> records) {
        Asset<?> changedAsset = null;
        boolean authorized = false;
        for(AbstractChangeRecord record : records) {
            if (changedAsset == null || !changedAsset.getUri().equals(record.getChangedEntity())) {
                changedAsset = resolveChangedAsset(record);
                authorized = isModificationAuthorized(changedAsset);
            }
            if (authorized &&
                    record instanceof UpdateChangeRecord updateRecord &&
                    canRollback(updateRecord, changedAsset)) {
                record.getTypes().add(UpdateChangeRecord.REVERSIBLE_CHANGE_CLASS);
            } else {
                record.getTypes().remove(UpdateChangeRecord.REVERSIBLE_CHANGE_CLASS);
            }
        }
    }

    /**
     * Checks whether the {@link UpdateChangeRecord} of the {@code changedObject} can be rolled back.
     *
     * @param record the change record
     * @param changedObject the changed entity
     * @return {@code true} when the change record can be rolled back, {@code false} otherwise.
     */
    private boolean canRollback(UpdateChangeRecord record, Asset<?> changedObject) {
        final Class<? extends Asset<?>> entityClass = resolveEntityClass(changedObject);
        if (!HasProperties.class.isAssignableFrom(entityClass)) {
            return false;
        }
        return rollbackValidator.canRollback(record, entityClass);
    }

    /**
     * Rolls back the change by setting the changed attribute to the original value.
     *
     * @param record the record to rollback
     */
    @Transactional
    public void rollback(UpdateChangeRecord record) {
        final Asset<?> changedAsset = resolveChangedAssetRequired(record);

        if (!canRollback(record, changedAsset)) {
            throw new UpdateChangeRecordRollbackException("Update change record cannot be rolled back");
        }

        ensureModificationAuthorized(changedAsset);
        doRollback(record, changedAsset);
    }

    /**
     * Finds an update change record with the specified local name.
     * <p>
     * The local name is resolved against the {@link UpdateChangeRecord} identifier namespace.
     *
     * @param recordLocalName Change record local name
     * @return Matching update change record
     * @throws cz.cvut.kbss.termit.exception.NotFoundException If no matching record is found
     */
    public UpdateChangeRecord findRecordByLocalName(String recordLocalName) {
        final URI recordUri = identifierResolver.resolveIdentifier(UpdateChangeRecord_.entityClassIRI.toString(), recordLocalName);
        return changeRecordService.findUpdateRequired(recordUri);
    }

    /**
     * Resolves the Java field of the entity class and updates its value.
     * When the attribute is not resolved to a specific Java field, the attribute is updated via
     * {@link cz.cvut.kbss.jopa.model.annotations.Properties @Properties} field.
     *
     * @param record the record to rollback
     * @param changedAsset the changed entity
     */
    private void doRollback(UpdateChangeRecord record, Asset<?> changedAsset) {
        final Class<? extends Asset<?>> entityClass = resolveEntityClass(changedAsset);
        final Attribute<?, ?> classAttribute = rollbackDao.resolveClassAttribute(entityClass, record).orElse(null);
        if (classAttribute != null) {
            rollbackDao.rollbackClassAttribute(record.getOriginalValue(), changedAsset, classAttribute);
        } else {
            rollbackDao.rollbackNativeProperty(record.getOriginalValue(), changedAsset, record.getChangedAttribute());
        }
        updateChangedAsset(changedAsset);
    }

    /**
     * Resolves instances of {@link Term}, {@link Vocabulary} and their subclasses to their respective main entity classes.
     *
     * @param asset the entity whose main class should be resolved
     * @return the resolved main entity class
     * @throws UpdateChangeRecordRollbackException when the {@code asset} is not instance of supported entity class.
     */
    private Class<? extends Asset<?>> resolveEntityClass(Asset<?> asset) {
        if (asset instanceof Term) {
            return Term.class;
        }
        if (asset instanceof Vocabulary) {
            return Vocabulary.class;
        }
        throw unsupportedAssetType(asset);
    }

    /**
     * Resolves the changed asset entity.
     *
     * @param record the change record
     * @return the changed asset
     * @throws UpdateChangeRecordRollbackException when the asset was not found or is unsupported type
     */
    private Asset<?> resolveChangedAssetRequired(UpdateChangeRecord record) {
        final Asset<?> changedAsset = resolveChangedAsset(record);
        if (changedAsset == null) {
            throw new UpdateChangeRecordRollbackException("Rollback not supported for asset " +
                    Utils.uriToString(record.getChangedEntity()));
        }
        return changedAsset;
    }

    /**
     * Tries to find {@link Vocabulary} or {@link Term} by the changed asset identifier.
     *
     * @param record the change record
     * @return the resolved changed asset or {@code null}
     */
    private Asset<?> resolveChangedAsset(AbstractChangeRecord record) {
        Optional<Vocabulary> vocabulary = vocabularyService.find(record.getChangedEntity());
        if (vocabulary.isPresent()) {
            return vocabulary.get();
        }
        Optional<Term> term = termService.find(record.getChangedEntity());
        if (term.isPresent()) {
            return term.get();
        }
        return null;
    }

    /**
     * Ensures the current user is authorized to modify the specified asset.
     *
     * @param asset the asset to be modified
     * @throws AccessDeniedException when the modification of the given asset is not authorized.
     * @see #isModificationAuthorized(Asset)
     */
    private void ensureModificationAuthorized(Asset<?> asset) {
        if (!isModificationAuthorized(asset)) {
            throw new AccessDeniedException("Not authorized to roll back changes to the asset.");
        }
    }

    /**
     * Checks whether the current user is authorized to modify the specified {@link Vocabulary} or {@link Term}.
     *
     * @param asset the asset to be modified
     * @return {@code true} when the specified asset is {@link Vocabulary} or {@link Term}
     *         and the current user is authorized for its modification, {@code false} otherwise.
     */
    private boolean isModificationAuthorized(Asset<?> asset) {
        if (asset instanceof Term term) {
            return termAuthorizationService.canModify(term);
        }
        if (asset instanceof Vocabulary vocabulary) {
            return vocabularyAuthorizationService.canModify(vocabulary);
        }
        return false;
    }

    /**
     * Updates the given asset using respective repository service.
     *
     * @param asset the asset to update
     * @throws UpdateChangeRecordRollbackException when the asset is not {@link Term} or {@link Vocabulary}
     */
    private void updateChangedAsset(Asset<?> asset) {
        if (asset instanceof Term term) {
            termService.update(term);
        } else if (asset instanceof Vocabulary vocabulary) {
            vocabularyService.update(vocabulary);
        } else {
            throw unsupportedAssetType(asset);
        }
    }

    private UpdateChangeRecordRollbackException unsupportedAssetType(Asset<?> asset) {
        return new UpdateChangeRecordRollbackException("The type of Asset " + Utils.uriToString(asset.getUri()) +
                " is not supported");
    }
}
