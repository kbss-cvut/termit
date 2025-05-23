/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.jopa.model.EntityManagerFactory;
import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.jopa.model.metamodel.EntityType;
import cz.cvut.kbss.jopa.model.metamodel.Metamodel;
import cz.cvut.kbss.jopa.model.metamodel.PluralAttribute;
import cz.cvut.kbss.jopa.model.metamodel.PropertiesSpecification;
import cz.cvut.kbss.jopa.model.metamodel.TypesSpecification;
import cz.cvut.kbss.jopa.utils.EntityPropertiesUtils;
import cz.cvut.kbss.jopa.utils.IdentifierTransformer;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.changetracking.IgnoreChanges;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static cz.cvut.kbss.jopa.utils.EntityPropertiesUtils.getAttributeValue;
import static cz.cvut.kbss.jopa.utils.EntityPropertiesUtils.getIdentifier;

@Component
public class MetamodelBasedChangeCalculator implements ChangeCalculator {

    private final Metamodel metamodel;

    @Autowired
    public MetamodelBasedChangeCalculator(EntityManagerFactory emf) {
        this.metamodel = emf.getMetamodel();
    }

    @Override
    public Collection<UpdateChangeRecord> calculateChanges(Asset<?> changed, Asset<?> original) {
        Objects.requireNonNull(changed);
        Objects.requireNonNull(original);

        final Collection<UpdateChangeRecord> records = new ArrayList<>();
        final EntityType<? extends Asset> et = metamodel.entity(changed.getClass());
        for (Attribute<?, ?> att : et.getAttributes()) {
            if (att.isInferred() || shouldIgnoreChanges(att)) {
                continue;
            }
            final Object originalValue = EntityPropertiesUtils.getAttributeValue(att, original);
            final Object updateValue = EntityPropertiesUtils.getAttributeValue(att, changed);
            if (att.isAssociation()) {
                final Optional<UpdateChangeRecord> change = resolveAssociationChange(originalValue, updateValue, att,
                                                                                     original.getUri());
                change.ifPresent(records::add);

            } else if (!areEqual(att, originalValue, updateValue)) {
                final UpdateChangeRecord record = createChangeRecord(original.getUri(), att.getIRI().toURI());
                recordValues(record, att, originalValue, updateValue);
                records.add(record);
            }
        }
        resolveTypesChange(original, changed, et, original.getUri()).ifPresent(records::add);
        records.addAll(resolveUnmappedPropertiesChanges(original, changed, et, original.getUri()));
        return records;
    }

    private boolean shouldIgnoreChanges(Attribute<?, ?> att) {
        return att.getJavaField().isAnnotationPresent(IgnoreChanges.class);
    }

    private boolean areEqual(Attribute<?, ?> att, Object originalValue, Object newValue) {
        return att.isCollection() ? areCollectionsEqual((Collection<?>) originalValue, (Collection<?>) newValue) :
               Objects.equals(originalValue, newValue);
    }

    private void recordValues(UpdateChangeRecord record, Attribute<?, ?> att, Object originalValue, Object newValue) {
        if (!att.isCollection()) {
            if (originalValue != null) {
                record.setOriginalValue(Collections.singleton(originalValue));
            }
            if (newValue != null) {
                record.setNewValue(Collections.singleton(newValue));
            }
        } else {
            if (originalValue != null) {
                record.setOriginalValue(new HashSet<>((Collection<?>) originalValue));
            }
            if (newValue != null) {
                record.setNewValue(new HashSet<>((Collection<?>) newValue));
            }
        }
    }

    private Optional<UpdateChangeRecord> resolveAssociationChange(Object originalValue, Object updateValue,
                                                                  Attribute<?, ?> att, URI assetId) {
        if (originalValue == null && updateValue == null) {
            return Optional.empty();
        }
        final Object originalToCompare;
        final Object updateToCompare;
        if (att.isCollection()) {
            final PluralAttribute<?, ?, ?> pluralAtt = (PluralAttribute<?, ?, ?>) att;
            if (IdentifierTransformer.isValidIdentifierType(pluralAtt.getElementType().getJavaType())) {
                originalToCompare = originalValue;
                updateToCompare = updateValue;
            } else {
                originalToCompare = extractIdentifiersInCollection(originalValue);
                updateToCompare = extractIdentifiersInCollection(updateValue);
            }
        } else if (IdentifierTransformer.isValidIdentifierType(att.getJavaType())) {
            originalToCompare = originalValue;
            updateToCompare = updateValue;
        } else {
            originalToCompare = originalValue != null ? getIdentifier(originalValue, metamodel) : null;
            updateToCompare = updateValue != null ? getIdentifier(updateValue, metamodel) : null;
        }

        if (areEqual(att, originalToCompare, updateToCompare)) {
            return Optional.empty();
        } else {
            final UpdateChangeRecord record = createChangeRecord(assetId, att.getIRI().toURI());
            recordValues(record, att, originalToCompare, updateToCompare);
            return Optional.of(record);
        }
    }

    private Object extractIdentifiersInCollection(Object col) {
        return col != null ?
               ((Collection<?>) col).stream().map(item -> getIdentifier(item, metamodel)).collect(Collectors.toSet()) :
               Collections.emptySet();
    }

    private UpdateChangeRecord createChangeRecord(URI assetId, URI property) {
        final UpdateChangeRecord record = new UpdateChangeRecord();
        record.setChangedEntity(assetId);
        record.setChangedAttribute(property);
        return record;
    }

    private Optional<UpdateChangeRecord> resolveTypesChange(Asset<?> original, Asset<?> update,
                                                            EntityType<? extends Asset> et,
                                                            URI assetId) {
        final TypesSpecification<?, ?> typesSpec = et.getTypes();
        if (typesSpec == null) {
            return Optional.empty();
        }
        final Collection<?> origTypes = (Collection<?>) getAttributeValue(typesSpec, original);
        final Collection<?> updateTypes = (Collection<?>) getAttributeValue(typesSpec, update);
        if (areCollectionsEqual(origTypes, updateTypes)) {
            return Optional.empty();
        } else {
            final UpdateChangeRecord record = createChangeRecord(assetId, URI.create(RDF.TYPE));
            if (origTypes != null) {
                record.setOriginalValue(origTypes.stream().map(t -> URI.create(t.toString()))
                                                 .collect(Collectors.toSet()));
            }
            if (updateTypes != null) {
                record.setNewValue(updateTypes.stream().map(t -> URI.create(t.toString()))
                                              .collect(Collectors.toSet()));
            }
            return Optional.of(record);
        }
    }

    private static boolean areCollectionsEqual(Collection<?> original, Collection<?> update) {
        if (Objects.equals(original, update)) {
            return true;
        }
        return original == null && update.isEmpty() || original != null && original.isEmpty() && update == null;
    }

    private Collection<UpdateChangeRecord> resolveUnmappedPropertiesChanges(Asset<?> original, Asset<?> update,
                                                                            EntityType<? extends Asset> et,
                                                                            URI assetId) {
        final PropertiesSpecification<?, ?, ?, ?> propsSpec = et.getProperties();
        if (propsSpec == null) {
            return Collections.emptySet();
        }
        Map<?, ?> originalProps = (Map<?, ?>) getAttributeValue(propsSpec, original);
        Map<?, ?> updateProps = (Map<?, ?>) getAttributeValue(propsSpec, update);
        if (originalProps == null) {
            originalProps = Collections.emptyMap();
        }
        if (updateProps == null) {
            updateProps = Collections.emptyMap();
        }
        final Collection<UpdateChangeRecord> records = new ArrayList<>();
        for (Map.Entry<?, ?> origEntry : originalProps.entrySet()) {
            final Object key = origEntry.getKey();
            if (!updateProps.containsKey(key) || !Objects.equals(origEntry.getValue(), updateProps.get(key))) {
                final UpdateChangeRecord record = createChangeRecord(assetId, IdentifierTransformer.valueAsUri(key));
                recordPropertyValues(record, origEntry.getValue(), updateProps.get(key));
                records.add(record);
            }
        }
        final Set<?> addedProps = new HashSet<>(updateProps.keySet());
        addedProps.removeAll(originalProps.keySet());
        for (Object p : addedProps) {
            final UpdateChangeRecord record = createChangeRecord(assetId, IdentifierTransformer.valueAsUri(p));
            recordPropertyValues(record, null, updateProps.get(p));
            records.add(record);
        }
        return records;
    }

    private static void recordPropertyValues(UpdateChangeRecord record, Object origValue, Object updateValue) {
        if (origValue != null) {
            record.setOriginalValue(new HashSet<>((Set<?>) origValue));
        }
        if (updateValue != null) {
            record.setNewValue(new HashSet<>((Set<?>) updateValue));
        }
    }
}
