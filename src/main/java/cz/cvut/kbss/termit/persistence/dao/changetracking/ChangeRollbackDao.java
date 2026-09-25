package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.IRI;
import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.jopa.query.QueryHints;
import cz.cvut.kbss.jopa.utils.EntityPropertiesUtils;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.exception.UpdateChangeRecordRollbackException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.util.HasProperties;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class ChangeRollbackDao {
    private final EntityManager em;

    public ChangeRollbackDao(EntityManager em) {
        this.em = em;
    }

    /**
     * Checks whether an entity with the given identifier exists
     *
     * @param entityIdentifier the identifier of the entity
     * @return {@code true} when the entity exists, {@code false} otherwise.
     */
    public boolean entityExists(URI entityIdentifier) {
        Objects.requireNonNull(entityIdentifier);
        try {
            return em.createNativeQuery("ASK { ?x a ?type }", Boolean.class)
                     .setParameter("x", entityIdentifier)
                     .setHint(QueryHints.DISABLE_INFERENCE, "true")
                     .getSingleResult();
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed check existence of entity " +
                    Utils.uriToString(entityIdentifier), e);
        }
    }

    /**
     * Resolves the entity class from {@link cz.cvut.kbss.jopa.model.metamodel.Metamodel Metamodel}
     * and tries to find attribute matching the changed attribute IRI.
     *
     * @param entityType the type of the changed entity
     * @param record the change record
     * @return The attribute if any attribute matching the IRI was found
     */
    public <T> Optional<Attribute<? super T, ?>> resolveClassAttribute(Class<T> entityType, UpdateChangeRecord record) {
        final IRI changedAttributeIRI = IRI.create(record.getChangedAttribute().toString());
        return em.getMetamodel().entity(entityType).getAttributes().stream()
                        .filter(attribute -> changedAttributeIRI.equals(attribute.getIRI()))
                        .findAny();
    }

    /**
     * Sets the given {@code originalValue} as the new value of the changed Java attribute.
     * The required class of the value is inspected from the {@code classAttribute} and identifiers
     * are mapped to the respective entity objects when required.
     *
     * @param originalValue the value to set
     * @param changedAsset the changed entity
     * @param classAttribute the changed Java attribute
     */
    public void rollbackClassAttribute(Set<Object> originalValue, Asset<?> changedAsset, Attribute<?, ?> classAttribute) {
        final Set<Object> mappedOriginalValue = resolveEntityReferences(originalValue, classAttribute);
        final Object newValue = getNewValue(mappedOriginalValue, classAttribute);
        EntityPropertiesUtils.setFieldValue(classAttribute.getJavaField(), changedAsset, newValue);
    }

    /**
     * Maps the given values to their respective entity reference objects when the {@code classAttribute}
     * holds entity objects.
     *
     * @param values Values to map
     * @param classAttribute the attribute description
     * @return Mapped {@code values} when the value type of the {@code classAttribute} is a Jopa entity type.
     *         Unchanged {@code values} otherwise.
     */
    private Set<Object> resolveEntityReferences(Set<Object> values, Attribute<?, ?> classAttribute) {
        final Class<?> valueType = classAttribute.getValueJavaType();
        if (values == null || !isEntityClass(valueType)) {
            return values;
        }

        return values.stream().map(identifier -> em.getReference(valueType, identifier))
                     .collect(Collectors.toSet());
    }

    /**
     * Checks whether metamodel contains the given entity class.
     *
     * @param clazz Class to check
     * @return {@code true} when the metamodel contains the given entity {@code class}, {@code false} otherwise.
     */
    private boolean isEntityClass(Class<?> clazz) {
        try {
            return em.getMetamodel().entity(clazz) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Maps the given {@code originalValue} to a collection or a single value based on the {@code classAttribute}.
     *
     * @param originalValue the original value
     * @param classAttribute the Java attribute
     * @return a mapped collection when attribute is a collection,
     *         the first value from the {@code originalValue} when the attribute is singular,
     *         {@code null} otherwise
     */
    private Object getNewValue(Set<Object> originalValue, Attribute<?, ?> classAttribute) {
        if (classAttribute.isCollection()) {
            Class<?> collectionClass = classAttribute.getJavaType();
            if (List.class.equals(collectionClass)) {
                return new ArrayList<>(originalValue);
            }
            if (Collection.class.equals(collectionClass) || Set.class.equals(collectionClass)) {
                return originalValue;
            }
            throw new UpdateChangeRecordRollbackException("Unsupported attribute type: " + collectionClass.getName());
        }

        if (originalValue == null || originalValue.isEmpty()) {
            return null;
        } else {
            assert originalValue.size() == 1;
            return originalValue.iterator().next();
        }
    }

    public void rollbackNativeProperty(Set<Object> originalValue, Asset<?> changedAsset, URI changedAttributeUri) {
        if (originalValue != null && !originalValue.isEmpty()) {
            getProperties(changedAsset).put(changedAttributeUri.toString(), originalValue);
        } else {
            getProperties(changedAsset).remove(changedAttributeUri.toString());
        }
    }

    private Map<String, Set<Object>> getProperties(Asset<?> asset) {
        if (asset instanceof HasProperties withProperties) {
            if (withProperties.getProperties() == null) {
                withProperties.setProperties(new HashMap<>());
            }
            return withProperties.getProperties();
        }
        throw new IllegalArgumentException("Asset does not implement HasProperties interface! " + Utils.uriToString(asset.getUri()));
    }
}
