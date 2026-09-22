package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRollbackDao;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Validator supporting the rollback validation of change records of {@link Term} and {@link Vocabulary}.
 */
@Component
public class RollbackValidator {
    private static final Class<?>[] PRIMITIVE_CLASSES = {
            // not including URI - can represent a reference to another entity
            Number.class,
            String.class, // TermIt is not using Strings as identifiers
            Boolean.class,
            Temporal.class,
            Date.class,
            TemporalAmount.class,
            UUID.class,
            MultilingualString.class,
            Void.TYPE,
            Void.class
    };

    private final ChangeRollbackDao changeRollbackDao;
    private final DataRepositoryService dataRepositoryService;

    public RollbackValidator(ChangeRollbackDao changeRollbackDao, DataRepositoryService dataRepositoryService) {
        this.changeRollbackDao = changeRollbackDao;
        this.dataRepositoryService = dataRepositoryService;
    }

    /**
     * Checks whether the record can be rolled back.
     *
     * @param record the change record to check
     * @return {@code true} if the change record is associated with existing {@link Term} or {@link Vocabulary}
     * and can be rolled back, {@code false} otherwise.
     */
    @Transactional(readOnly = true)
    public boolean canRollback(UpdateChangeRecord record, Class<?> entityClass) {
        if (allValuesArePrimitives(record)) {
            return true;
        }

        final Class<?> javaFieldValueClass = changeRollbackDao.resolveClassAttribute(entityClass, record)
                                                              .map(Attribute::getValueJavaType)
                                                              .orElse(null);

        // Java Entity class has field matching the changed attribute
        if (javaFieldValueClass != null) {
            return canRollbackAttributeWithType(javaFieldValueClass, record);
        }

        // The value change does not match any Java entity class field
        return canRollbackNativeProperty(record);
    }

    private boolean allValuesArePrimitives(UpdateChangeRecord record) {
        return Stream.of(record.getOriginalValue(), record.getNewValue())
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(Object::getClass)
                .allMatch(RollbackValidator::isPrimitive);
    }

    private static boolean isPrimitive(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return true;
        }
        for (Class<?> primitiveClass : PRIMITIVE_CLASSES) {
            if (primitiveClass.isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;
    }

    private boolean canRollbackAttributeWithType(Class<?> attributeValueClass, UpdateChangeRecord record) {
        if (isPrimitive(attributeValueClass)) {
            return true;
        }
        if (record.getOriginalValue() == null) {
            return true;
        }
        for (Object originalValue : record.getOriginalValue()) {
            // assumes that every URI field must be valid entity reference
            if (originalValue instanceof URI entityIdentifier && !canRollbackToEntityReference(entityIdentifier)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks that the given entity has asserted type in the repository (the entity exists).
     *
     * @param referencedEntity the identifier of entity
     * @return {@code true} when the change record can safely be rolled back, {@code false} otherwise
     */
    private boolean canRollbackToEntityReference(URI referencedEntity) {
        if (referencedEntity == null) {
            return true;
        }
        return changeRollbackDao.entityExists(referencedEntity);
    }

    /**
     * Checks whether a matching custom attribute exists for the changed attribute and permits rollback based on its range.
     *
     * @param record the change record to check
     * @return {@code true} if the custom attribute exists and its range permits rollback, {@code false} otherwise
     */
    private boolean canRollbackNativeProperty(UpdateChangeRecord record) {
        return dataRepositoryService.findCustomAttribute(record.getChangedAttribute())
                .map(attr -> canRollbackCustomAttributeWithReferenceRange(attr, record))
                .orElse(false); // custom attribute does not exist
    }

    /**
     * Checks whether the record can be rolled back based on the custom attribute range.
     *
     * @param attribute the custom attribute changed by the record
     * @param record    the change record to check
     * @return {@code true} if the range permits rollback, including the validity of references where required,
     *                      {@code false} otherwise
     */
    private boolean canRollbackCustomAttributeWithReferenceRange(CustomAttribute attribute, UpdateChangeRecord record) {
        return switch (attribute.getRange().toString()) {
            case RDFS.RESOURCE -> true;
            case SKOS.CONCEPT -> canRollbackAttributeWithType(Term.class, record);
            case SKOS.CONCEPT_SCHEME -> canRollbackAttributeWithType(Vocabulary.class, record);
            default -> false;
        };
    }
}
