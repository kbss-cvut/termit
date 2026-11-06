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

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.IRI;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.AssetPersistEvent;
import cz.cvut.kbss.termit.event.AssetUpdateEvent;
import cz.cvut.kbss.termit.event.CollectionTypeUpdateEvent;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.OrderedCollectionMembersUpdateChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection_;
import cz.cvut.kbss.termit.model.collection.SkosCollection;
import cz.cvut.kbss.termit.model.collection.SkosCollection_;
import cz.cvut.kbss.termit.model.collection.TermCollection_;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static cz.cvut.kbss.termit.service.changetracking.MetamodelBasedChangeCalculatorTest.cloneOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeTrackerTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private ChangeTracker sut;

    private User author;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        Environment.setCurrentUser(author);
        this.vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> {
            em.persist(author);
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
        });
    }

    @Test
    void onAssetPersistEventStoresCreationChangeRecordInRepository() {
        enableRdfsInference(em);
        final Term newTerm = Generator.generateTermWithId();
        newTerm.setVocabulary(vocabulary.getUri());
        transactional(() -> {
            em.persist(newTerm, descriptorFactory.termDescriptor(vocabulary));
            sut.onAssetPersistEvent(new AssetPersistEvent(this, newTerm));
        });

        final List<AbstractChangeRecord> result = findRecords(newTerm);
        assertEquals(1, result.size());
        final AbstractChangeRecord record = result.getFirst();
        assertThat(record, instanceOf(PersistChangeRecord.class));
        assertEquals(newTerm.getUri(), record.getChangedEntity());
        assertEquals(author, record.getAuthor());
        assertNotNull(record.getTimestamp());
    }

    private List<AbstractChangeRecord> findRecords(HasIdentifier entity) {
        return em.createNativeQuery(
                        "SELECT ?x WHERE { ?x a ?changeRecord ; ?concerns ?entity . }", AbstractChangeRecord.class)
                .setParameter("changeRecord", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_change))
                .setParameter("concerns", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_has_changed_entity))
                .setParameter("entity", entity.getUri())
                .getResultList();
    }

    @Test
    void onAssetUpdateEventEventDoesNothingWhenAssetDidNotChange() {
        enableRdfsInference(em);
        final Term original = Generator.generateTermWithId();
        original.setVocabulary(vocabulary.getUri());
        transactional(() -> em.persist(original, descriptorFactory.termDescriptor(original)));

        final Term update = cloneOf(original);
        transactional(() -> sut.onAssetUpdateEvent(new AssetUpdateEvent(this, update)));

        assertTrue(findRecords(original).isEmpty());
    }

    @Test
    void onAssetUpdateEventRecordsSingleChangeToLiteralAttribute() {
        enableRdfsInference(em);
        final Term original = Generator.generateTermWithId();
        original.setVocabulary(vocabulary.getUri());
        transactional(() -> em.persist(original, descriptorFactory.termDescriptor(vocabulary)));

        final Term update = cloneOf(original);
        update.setDefinition(MultilingualString.create("Updated definition of this term.", Environment.LANGUAGE));
        transactional(() -> sut.onAssetUpdateEvent(new AssetUpdateEvent(this, update)));

        final List<AbstractChangeRecord> result = findRecords(original);
        assertEquals(1, result.size());
        final AbstractChangeRecord record = result.getFirst();
        assertEquals(original.getUri(), record.getChangedEntity());
        assertThat(record, instanceOf(UpdateChangeRecord.class));
        assertEquals(
                SKOS.DEFINITION,
                ((UpdateChangeRecord) record).getChangedAttribute().toString());
    }

    @Test
    void onAssetUpdateEventRecordsMultipleChangesToAttributes() {
        enableRdfsInference(em);
        final Term original = Generator.generateTermWithId();
        original.setVocabulary(vocabulary.getUri());
        transactional(() -> em.persist(original, descriptorFactory.termDescriptor(vocabulary)));

        final Term update = cloneOf(original);
        update.setDefinition(MultilingualString.create("Updated definition of this term.", Environment.LANGUAGE));
        update.setSources(Collections.singleton(Generator.generateUri().toString()));
        transactional(() -> sut.onAssetUpdateEvent(new AssetUpdateEvent(this, update)));

        final List<AbstractChangeRecord> result = findRecords(original);
        assertEquals(2, result.size());
        result.forEach(record -> {
            assertEquals(original.getUri(), record.getChangedEntity());
            assertThat(record, instanceOf(UpdateChangeRecord.class));
            assertThat(
                    ((UpdateChangeRecord) record).getChangedAttribute().toString(),
                    anyOf(equalTo(SKOS.DEFINITION), equalTo(DC.Terms.SOURCE)));
        });
    }

    private static Stream<Arguments> changesOfCollectionType() {
        Vocabulary vocabulary = Generator.generateVocabularyWithId();
        return Stream.of(
                Arguments.of(
                        Generator.generateTermCollection(vocabulary),
                        Generator.generateOrderedTermCollection(vocabulary)),
                Arguments.of(
                        Generator.generateOrderedTermCollection(vocabulary),
                        Generator.generateTermCollection(vocabulary)));
    }

    private IRI getCollectionType(SkosCollection<?> collection) {
        return collection.isOrdered() ? OrderedTermCollection_.entityClassIRI : TermCollection_.entityClassIRI;
    }

    @ParameterizedTest
    @MethodSource("changesOfCollectionType")
    void onCollectionTypeUpdateEventRecordsChangeToCollectionType(
            SkosCollection<?> original, SkosCollection<?> updated) {
        enableRdfsInference(em);
        assertNotEquals(original.isOrdered(), updated.isOrdered());
        updated.setUri(original.getUri());

        sut.onCollectionTypeUpdateEvent(new CollectionTypeUpdateEvent(this, original));

        final List<AbstractChangeRecord> records = findRecords(original);
        assertEquals(1, records.size());
        UpdateChangeRecord result = assertInstanceOf(UpdateChangeRecord.class, records.getFirst());

        assertEquals(updated.getUri(), result.getChangedEntity());
        assertEquals(URI.create(RDF.TYPE), result.getChangedAttribute());

        String newType = getCollectionType(updated).toString();
        String oldType = getCollectionType(original).toString();

        assertThat(result.getNewValue(), Matchers.contains(newType));
        assertThat(result.getNewValue(), not(Matchers.contains(oldType)));
    }

    @Test
    void onAssetUpdateEventPersistsOrderedCollectionMembersUpdateChangeRecord() {
        enableRdfsInference(em);

        OrderedTermCollection collection = Generator.generateOrderedTermCollection(vocabulary);
        final List<URI> originalMembers = List.copyOf(collection.getMembers());
        transactional(() -> {
            em.persist(collection, descriptorFactory.vocabularyDescriptor(vocabulary));
        });

        collection.getMembers().remove(3);
        collection.getMembers().add(Generator.generateUri());

        final List<URI> newMembers = List.copyOf(collection.getMembers());

        assertNotEquals(originalMembers, newMembers);

        sut.onAssetUpdateEvent(new AssetUpdateEvent(this, collection));

        final List<AbstractChangeRecord> records = findRecords(collection);
        assertEquals(1, records.size());
        OrderedCollectionMembersUpdateChangeRecord result =
                assertInstanceOf(OrderedCollectionMembersUpdateChangeRecord.class, records.getFirst());

        assertEquals(collection.getUri(), result.getChangedEntity());
        assertEquals(OrderedTermCollection_.membersPropertyIRI.toURI(), result.getChangedAttribute());
        assertEquals(newMembers, result.getNewValue());
        assertEquals(originalMembers, result.getOriginalValue());
    }

    @Test
    void onAssetUpdateEventPersistsChangeRecordsForCollectionLabelAndMembers() {
        enableRdfsInference(em);

        OrderedTermCollection collection = Generator.generateOrderedTermCollection(vocabulary);
        transactional(() -> {
            em.persist(collection, descriptorFactory.vocabularyDescriptor(vocabulary));
        });

        collection.getMembers().remove(3);
        collection.getMembers().add(Generator.generateUri());
        collection.getLabel().set(Environment.LANGUAGE, "Updated label of SKOS collection");

        sut.onAssetUpdateEvent(new AssetUpdateEvent(this, collection));

        final List<AbstractChangeRecord> records = findRecords(collection);
        assertEquals(2, records.size());

        UpdateChangeRecord updateRecord = (UpdateChangeRecord) records.stream()
                .filter(r -> r instanceof UpdateChangeRecord)
                .findAny()
                .orElseThrow();
        assertEquals(SkosCollection_.labelPropertyIRI.toURI(), updateRecord.getChangedAttribute());

        OrderedCollectionMembersUpdateChangeRecord membersRecord =
                (OrderedCollectionMembersUpdateChangeRecord) records.stream()
                        .filter(r -> r instanceof OrderedCollectionMembersUpdateChangeRecord)
                        .findAny()
                        .orElseThrow();
        assertEquals(collection.getMembers(), membersRecord.getNewValue());
    }
}
