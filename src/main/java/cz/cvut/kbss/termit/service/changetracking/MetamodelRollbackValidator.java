package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRollbackDao;
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
public class MetamodelRollbackValidator {
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

    public MetamodelRollbackValidator(ChangeRollbackDao changeRollbackDao) {
        this.changeRollbackDao = changeRollbackDao;
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

        return canRollbackEntityClass(entityClass, record);
    }

    private boolean allValuesArePrimitives(UpdateChangeRecord record) {
        return Stream.of(record.getOriginalValue(), record.getNewValue())
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(Object::getClass)
                .allMatch(MetamodelRollbackValidator::isPrimitive);
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

    private boolean canRollbackEntityClass(Class<?> entityClass, UpdateChangeRecord record) {
        return changeRollbackDao.resolveClassAttribute(entityClass, record)
                .map(Attribute::getValueJavaType)
                .map(clazz -> canRollbackAttributeWithType(clazz, record))
                .orElse(false);
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
}
