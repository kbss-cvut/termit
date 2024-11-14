/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.filter.ChangeRecordFilterDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.DeleteChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ChangeRecordDaoTest extends BaseDaoTestRunner {
    private static final URI SKOS_CONCEPT = URI.create(SKOS.CONCEPT);

    @Autowired
    private ChangeTrackingContextResolver contextResolver;

    @Autowired
    private EntityManager em;

    @Autowired
    private ChangeRecordDao sut;

    private User author;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.vocabulary = Generator.generateVocabularyWithId();
        this.author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
    }

    @Test
    void persistSavesChangeRecordIntoContext() {
        final AbstractChangeRecord record = generatePersistRecord(Instant.now(), Generator.generateUri());
        transactional(() -> sut.persist(record, vocabulary));

        final AbstractChangeRecord result = em.find(AbstractChangeRecord.class, record.getUri());
        assertNotNull(result);
        assertTrue(em.createNativeQuery("ASK WHERE { GRAPH ?g { ?x a ?changeRecord . } }", Boolean.class)
                     .setParameter("g", contextResolver.resolveChangeTrackingContext(vocabulary))
                     .setParameter("x", record.getUri())
                     .getSingleResult());
    }

    private PersistChangeRecord generatePersistRecord(Instant timestamp, URI changedObject) {
        final PersistChangeRecord record = new PersistChangeRecord();
        record.setAuthor(author);
        record.setTimestamp(timestamp);
        record.setChangedEntity(changedObject);
        return record;
    }

    private UpdateChangeRecord generateUpdateRecord(Instant timestamp, URI changedObject) {
        final UpdateChangeRecord record = new UpdateChangeRecord();
        record.setAuthor(author);
        record.setTimestamp(timestamp);
        record.setChangedEntity(changedObject);
        record.setChangedAttribute(URI.create(SKOS.PREF_LABEL));
        return record;
    }

    @Test
    void findAllRetrievesChangeRecordsRelatedToSpecifiedAsset() {
        enableRdfsInference(em);
        final Term asset = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            em.persist(vocabulary);
            em.persist(asset, persistDescriptor(vocabulary.getUri()));
        });
        final List<AbstractChangeRecord> records = IntStream.range(0, 5).mapToObj(
                i -> generateUpdateRecord(Instant.ofEpochMilli(System.currentTimeMillis() - i * 10000L),
                                          asset.getUri())).collect(Collectors.toList());
        final URI changeContext = contextResolver.resolveChangeTrackingContext(vocabulary);
        transactional(() -> records.forEach(r -> em.persist(r, persistDescriptor(changeContext))));

        final List<AbstractChangeRecord> result = sut.findAll(asset);
        assertEquals(records.size(), result.size());
        assertTrue(records.containsAll(result));
    }

    private Descriptor persistDescriptor(URI context) {
        final EntityDescriptor descriptor = new EntityDescriptor(context);
        descriptor.addAttributeDescriptor(em.getMetamodel().entity(AbstractChangeRecord.class).getAttribute("author"),
                                          new EntityDescriptor());
        return descriptor;
    }

    @Test
    void findAllReturnsChangeRecordsOrderedByTimestampDescending() {
        enableRdfsInference(em);
        final Term asset = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            em.persist(vocabulary);
            em.persist(asset, persistDescriptor(vocabulary.getUri()));
        });
        final List<AbstractChangeRecord> records = IntStream.range(0, 5).mapToObj(
                i -> generateUpdateRecord(Instant.ofEpochMilli(System.currentTimeMillis() + i * 10000L),
                                          asset.getUri())).collect(Collectors.toList());
        final URI changeContext = contextResolver.resolveChangeTrackingContext(vocabulary);
        transactional(() -> records.forEach(r -> em.persist(r, persistDescriptor(changeContext))));

        final List<AbstractChangeRecord> result = sut.findAll(asset);
        records.sort(Comparator.comparing(AbstractChangeRecord::getTimestamp).reversed());
        assertEquals(records, result);
    }

    @Test
    void findAllReturnsChangeRecordsOrderedByTimestampDescendingAndChangedAttributeId() {
        enableRdfsInference(em);
        final Term asset = Generator.generateTermWithId(vocabulary.getUri());
        final Instant now = Utils.timestamp();
        final UpdateChangeRecord rOne = generateUpdateRecord(now, asset.getUri());
        rOne.setChangedAttribute(URI.create(SKOS.PREF_LABEL));
        final UpdateChangeRecord rTwo = generateUpdateRecord(now, asset.getUri());
        rTwo.setChangedAttribute(URI.create(SKOS.DEFINITION));
        final Descriptor changeContextDescriptor = persistDescriptor(contextResolver.resolveChangeTrackingContext(vocabulary));
        transactional(() -> {
            em.persist(vocabulary);
            em.persist(asset, persistDescriptor(vocabulary.getUri()));
            em.persist(rOne, changeContextDescriptor);
            em.persist(rTwo, changeContextDescriptor);
        });

        final List<AbstractChangeRecord> result = sut.findAll(asset);
        assertEquals(2, result.size());
        result.forEach(r -> assertThat(r, instanceOf(UpdateChangeRecord.class)));
        assertEquals(SKOS.DEFINITION, ((UpdateChangeRecord) result.get(0)).getChangedAttribute().toString());
        assertEquals(SKOS.PREF_LABEL, ((UpdateChangeRecord) result.get(1)).getChangedAttribute().toString());
    }

    @Test
    void findAllReturnsEmptyListForUnknownAsset() {
        enableRdfsInference(em);
        final List<AbstractChangeRecord> result = sut.findAll(Generator.generateTermWithId());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void persistSavesChangeRecordWithLiteralValueChange() {
        final UpdateChangeRecord record = generateUpdateRecord(Utils.timestamp(), Generator.generateUri());
        record.setOriginalValue(Collections.singleton("original value"));
        record.setNewValue(Collections.singleton("new value"));
        transactional(() -> sut.persist(record, vocabulary));

        final UpdateChangeRecord result = em.find(UpdateChangeRecord.class, record.getUri());
        assertNotNull(result);
        assertEquals(record.getOriginalValue(), result.getOriginalValue());
        assertEquals(record.getNewValue(), result.getNewValue());
    }

    @Test
    void persistSavesRecordWithReferenceValueChange() {
        final UpdateChangeRecord record = generateUpdateRecord(Utils.timestamp(), Generator.generateUri());
        record.setNewValue(Collections.singleton(Generator.generateUri()));
        transactional(() -> sut.persist(record, vocabulary));

        final UpdateChangeRecord result = em.find(UpdateChangeRecord.class, record.getUri());
        assertNotNull(result);
        assertThat(result.getOriginalValue(), anyOf(nullValue(), empty()));
        assertEquals(record.getNewValue(), result.getNewValue());
    }

    @Test
    void supportsWorkingWithMultilingualAttributes() {
        enableRdfsInference(em);
        final UpdateChangeRecord record = generateUpdateRecord(Utils.timestamp(), vocabulary.getUri());
        final MultilingualString original = MultilingualString.create("Test term", "en");
        final MultilingualString newValue = new MultilingualString(original.getValue());
        newValue.set("cs", "Testovací pojem");
        record.setOriginalValue(Collections.singleton(original));
        record.setNewValue(Collections.singleton(newValue));
        transactional(() -> sut.persist(record, vocabulary));

        final List<AbstractChangeRecord> result = sut.findAll(vocabulary);
        assertEquals(1, result.size());
        assertThat(result.get(0), instanceOf(UpdateChangeRecord.class));
        final UpdateChangeRecord updateRecord = (UpdateChangeRecord) result.get(0);
        assertEquals(Collections.singleton(original), updateRecord.getOriginalValue());
        assertEquals(Collections.singleton(newValue), consolidateMultilingualStrings(updateRecord.getNewValue()));
    }

    private Set<Object> consolidateMultilingualStrings(Set<Object> source) {
        final List<MultilingualString> target = new ArrayList<>();
        for (Object src : source) {
            assert src instanceof MultilingualString;
            final MultilingualString ms = (MultilingualString) src;
            if (target.isEmpty() || ms.getLanguages().size() > 1) {
                target.add(ms);
                continue;
            }
            final String lang = ms.getLanguages().iterator().next();
            final Optional<MultilingualString> existing = target.stream().filter(e -> !e.contains(lang)).findFirst();
            if (existing.isPresent()) {
                existing.get().set(lang, ms.get(lang));
            } else {
                target.add(ms);
            }
        }
        return new HashSet<>(target);
    }

    @Test
    void getAuthorsRetrievesUsersAssociatedWithPersistChangeRecordsOfSpecifiedAsset() {
        enableRdfsInference(em);
        final Term asset = Generator.generateTermWithId(vocabulary.getUri());
        final AbstractChangeRecord persistRecord = generatePersistRecord(Utils.timestamp(), asset.getUri());
        final User editor = Generator.generateUserWithId();
        final AbstractChangeRecord anotherPersistRecord = generatePersistRecord(Utils.timestamp(), Generator.generateUri());
        anotherPersistRecord.setAuthor(editor);
        final AbstractChangeRecord updateRecord = generateUpdateRecord(Utils.timestamp(), asset.getUri());
        updateRecord.setAuthor(editor);
        transactional(() -> {
            em.persist(asset);
            em.persist(editor);
            final Descriptor descriptor = persistDescriptor(vocabulary.getUri());
            em.persist(persistRecord, descriptor);
            em.persist(anotherPersistRecord, descriptor);
            em.persist(updateRecord, descriptor);
        });

        final Set<User> result = sut.getAuthors(asset);
        assertEquals(Collections.singleton(author), result);
    }

    @Test
    void findAllRelatedToTypeReturnsChangeRecordsWithoutVocabularyChanges() {
        enableRdfsInference(em);

        final Term firstTerm = Generator.generateTermWithId(vocabulary.getUri());
        final Term secondTerm = Generator.generateTermWithId(vocabulary.getUri());

        final List<AbstractChangeRecord> firstChanges = Generator.generateChangeRecords(firstTerm, author);
        final List<AbstractChangeRecord> secondChanges = Generator.generateChangeRecords(secondTerm, author);

        final List<AbstractChangeRecord> vocabularyChanges = Generator.generateChangeRecords(vocabulary, author);

        final Descriptor changeContextDescriptor = persistDescriptor(contextResolver.resolveChangeTrackingContext(vocabulary));
        final Descriptor vocabularyDescriptor = persistDescriptor(vocabulary.getUri());

        transactional(() -> {
            em.persist(vocabulary, vocabularyDescriptor);
            em.persist(firstTerm, vocabularyDescriptor);
            em.persist(secondTerm, vocabularyDescriptor);

            Stream.of(firstChanges, secondChanges, vocabularyChanges)
                  .flatMap(Collection::stream)
                  .forEach(r -> em.persist(r, changeContextDescriptor));
        });

        final ChangeRecordFilterDto filter = new ChangeRecordFilterDto();

        final int recordsCount = firstChanges.size() + secondChanges.size();
        final Pageable pageable = Pageable.unpaged();

        final List<AbstractChangeRecord> contentChanges = sut.findAllRelatedToType(vocabulary, filter, SKOS_CONCEPT, pageable);

        assertEquals(recordsCount, contentChanges.size());
        final long persistCount = contentChanges.stream().filter(ch -> ch instanceof PersistChangeRecord).count();
        final long updatesCount = contentChanges.stream().filter(ch -> ch instanceof UpdateChangeRecord).count();
        final long deleteCount = contentChanges.stream().filter(ch -> ch instanceof DeleteChangeRecord).count();
        // check that all changes are related to the first or the second term
        assertTrue(contentChanges.stream()
                                 .allMatch(ch -> firstTerm.getUri().equals(ch.getChangedEntity()) ||
                                         secondTerm.getUri().equals(ch.getChangedEntity())));
        assertEquals(2, persistCount);
        assertEquals(recordsCount - 2, updatesCount); // -2 persist records
        assertEquals(0, deleteCount);
    }

    @Test
    void findAllRelatedToTypeReturnsRecordsOfExistingTermFilteredByTermName() {
        enableRdfsInference(em);

        final String needle = "needle";
        final String haystack = "A label that contains needle somewhere";
        final String mud = "The n3edle is not here";

        // needle is inside the label of first and the second term
        final Term firstTerm = Generator.generateTermWithId(vocabulary.getUri());
        firstTerm.getLabel().set(Environment.LANGUAGE, haystack);
        final Term secondTerm = Generator.generateTermWithId(vocabulary.getUri());
        secondTerm.getLabel().set(mud + needle);
        final Term thirdTerm = Generator.generateTermWithId(vocabulary.getUri());
        thirdTerm.getLabel().set(Environment.LANGUAGE, mud);

        final List<AbstractChangeRecord> firstChanges = Generator.generateChangeRecords(firstTerm, author);
        final List<AbstractChangeRecord> secondChanges = Generator.generateChangeRecords(secondTerm, author);
        final List<AbstractChangeRecord> thirdChanges = Generator.generateChangeRecords(thirdTerm, author);

        final Descriptor changeContextDescriptor = persistDescriptor(contextResolver.resolveChangeTrackingContext(vocabulary));
        final Descriptor vocabularyDescriptor = persistDescriptor(vocabulary.getUri());

        transactional(() -> {
            em.persist(vocabulary, vocabularyDescriptor);

            em.persist(firstTerm, vocabularyDescriptor);
            em.persist(secondTerm, vocabularyDescriptor);
            em.persist(thirdTerm, vocabularyDescriptor);

            Stream.of(firstChanges, secondChanges, thirdChanges)
                  .flatMap(Collection::stream)
                  .forEach(r -> em.persist(r, changeContextDescriptor));
        });

        final ChangeRecordFilterDto filter = new ChangeRecordFilterDto();
        filter.setAssetLabel(needle);

        // needle is inside the label of first and the second term
        final int recordsCount = firstChanges.size() + secondChanges.size();
        final Pageable pageable = Pageable.ofSize(recordsCount * 2);

        final List<AbstractChangeRecord> contentChanges = sut.findAllRelatedToType(vocabulary, filter, SKOS_CONCEPT, pageable);

        assertEquals(recordsCount, contentChanges.size());
        final long persistCount = contentChanges.stream().filter(ch -> ch instanceof PersistChangeRecord).count();
        final long updatesCount = contentChanges.stream().filter(ch -> ch instanceof UpdateChangeRecord).count();
        final long deleteCount = contentChanges.stream().filter(ch -> ch instanceof DeleteChangeRecord).count();
        assertEquals(2, persistCount);
        assertEquals(recordsCount - 2, updatesCount); // -2 persist records
        assertEquals(0, deleteCount);
    }


    @Test
    void findAllRelatedToTypeReturnsRecordsOfExistingTermFilteredByChangedAttributeName() {
        enableRdfsInference(em);

        final Term firstTerm = Generator.generateTermWithId(vocabulary.getUri());
        final Term secondTerm = Generator.generateTermWithId(vocabulary.getUri());

        final List<AbstractChangeRecord> firstChanges = Generator.generateChangeRecords(firstTerm, author);
        final List<AbstractChangeRecord> secondChanges = Generator.generateChangeRecords(secondTerm, author);

        final Random random = new Random();
        final AtomicInteger recordCount = new AtomicInteger(0);
        final URI changedAttribute = URI.create(SKOS.DEFINITION);
        final URI anotherChangedAttribute = URI.create(RDFS.LABEL);
        final String changedAttributeName = "definition";

        final Descriptor changeContextDescriptor = persistDescriptor(contextResolver.resolveChangeTrackingContext(vocabulary));
        final Descriptor vocabularyDescriptor = persistDescriptor(vocabulary.getUri());

        // randomize changed attributes
        Stream.of(firstChanges, secondChanges).flatMap(Collection::stream)
              .filter(r -> r instanceof UpdateChangeRecord)
              .map(r -> (UpdateChangeRecord) r)
              .forEach(r -> {
                  // ensuring at least one has the "changedAttribute"
                  if(random.nextBoolean() || recordCount.get() == 0) {
                      r.setChangedAttribute(changedAttribute);
                      recordCount.incrementAndGet();
                  } else {
                      r.setChangedAttribute(anotherChangedAttribute);
                  }
              });

        transactional(() -> {
            em.persist(vocabulary);

            em.persist(firstTerm, vocabularyDescriptor);
            em.persist(secondTerm, vocabularyDescriptor);

            Stream.of(firstChanges, secondChanges)
                  .flatMap(Collection::stream)
                  .forEach(r -> em.persist(r, changeContextDescriptor));
        });

        final ChangeRecordFilterDto filter = new ChangeRecordFilterDto();
        filter.setChangedAttributeName(changedAttributeName);

        final Pageable pageable = Pageable.unpaged();

        final List<AbstractChangeRecord> contentChanges = sut.findAllRelatedToType(vocabulary, filter, SKOS_CONCEPT, pageable);

        assertEquals(recordCount.get(), contentChanges.size());
        final long persistCount = contentChanges.stream().filter(ch -> ch instanceof PersistChangeRecord).count();
        final long updatesCount = contentChanges.stream().filter(ch -> ch instanceof UpdateChangeRecord).count();
        final long deleteCount = contentChanges.stream().filter(ch -> ch instanceof DeleteChangeRecord).count();
        assertEquals(0, persistCount);
        assertEquals(recordCount.get(), updatesCount);
        assertEquals(0, deleteCount);
    }

    @Test
    void findAllRelatedToTypeReturnsRecordsOfExistingTermFilteredByAuthorName() {
        enableRdfsInference(em);

        final Term firstTerm = Generator.generateTermWithId(vocabulary.getUri());
        final Term secondTerm = Generator.generateTermWithId(vocabulary.getUri());

        final List<AbstractChangeRecord> firstChanges = Generator.generateChangeRecords(firstTerm, author);
        final List<AbstractChangeRecord> secondChanges = Generator.generateChangeRecords(secondTerm, author);

        // make new author
        final User anotherAuthor = Generator.generateUserWithId();
        anotherAuthor.setFirstName("Karel");
        anotherAuthor.setLastName("Novák");
        transactional(() -> em.persist(anotherAuthor));
        Environment.setCurrentUser(anotherAuthor);

        final int recordCount = 2;
        // author is this.author (Environment current user)
        firstChanges.add(Generator.generateUpdateChange(firstTerm));
        secondChanges.add(Generator.generateUpdateChange(secondTerm));

        final Descriptor changeContextDescriptor = persistDescriptor(contextResolver.resolveChangeTrackingContext(vocabulary));
        final Descriptor vocabularyDescriptor = persistDescriptor(vocabulary.getUri());

        transactional(() -> {
            em.persist(vocabulary);

            em.persist(firstTerm, vocabularyDescriptor);
            em.persist(secondTerm, vocabularyDescriptor);

            Stream.of(firstChanges, secondChanges)
                  .flatMap(Collection::stream)
                  .forEach(r -> em.persist(r, changeContextDescriptor));
        });

        final ChangeRecordFilterDto filter = new ChangeRecordFilterDto();
        // full name without first two and last two characters
        filter.setAuthorName(anotherAuthor.getFullName().substring(2, anotherAuthor.getFullName().length() - 2));

        final Pageable pageable = Pageable.unpaged();

        final List<AbstractChangeRecord> contentChanges = sut.findAllRelatedToType(vocabulary, filter, SKOS_CONCEPT, pageable);

        assertEquals(recordCount, contentChanges.size());
        final long persistCount = contentChanges.stream().filter(ch -> ch instanceof PersistChangeRecord).count();
        final long updatesCount = contentChanges.stream().filter(ch -> ch instanceof UpdateChangeRecord).count();
        final long deleteCount = contentChanges.stream().filter(ch -> ch instanceof DeleteChangeRecord).count();
        assertEquals(0, persistCount);
        assertEquals(recordCount, updatesCount);
        assertEquals(0, deleteCount);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            UpdateChangeRecord.class,
            PersistChangeRecord.class,
            DeleteChangeRecord.class
    })
    void findAllRelatedToTypeReturnsRecordsOfExistingTermFilteredByChangeType(Class<? extends AbstractChangeRecord> typeClass) {
        enableRdfsInference(em);
        final URI typeUri = URI.create(typeClass.getAnnotation(OWLClass.class).iri());

        final Term firstTerm = Generator.generateTermWithId(vocabulary.getUri());
        final Term secondTerm = Generator.generateTermWithId(vocabulary.getUri());

        final List<AbstractChangeRecord> firstChanges = Generator.generateChangeRecords(firstTerm, author);
        final List<AbstractChangeRecord> secondChanges = Generator.generateChangeRecords(secondTerm, author);
        final DeleteChangeRecord deleteChangeRecord = new DeleteChangeRecord();
        deleteChangeRecord.setChangedEntity(secondTerm.getUri());
        deleteChangeRecord.setTimestamp(Utils.timestamp());
        deleteChangeRecord.setAuthor(author);
        deleteChangeRecord.setLabel(secondTerm.getLabel());

        final int recordCount = (int) Stream.of(firstChanges, secondChanges, List.of(deleteChangeRecord)).flatMap(List::stream).filter(typeClass::isInstance).count();

        final Descriptor changeContextDescriptor = persistDescriptor(contextResolver.resolveChangeTrackingContext(vocabulary));
        final Descriptor vocabularyDescriptor = persistDescriptor(vocabulary.getUri());

        transactional(() -> {
            em.persist(vocabulary);

            em.persist(firstTerm, vocabularyDescriptor);
            em.persist(secondTerm, vocabularyDescriptor);

            Stream.of(firstChanges, secondChanges, List.of(deleteChangeRecord))
                  .flatMap(Collection::stream)
                  .forEach(r -> em.persist(r, changeContextDescriptor));
        });

        final ChangeRecordFilterDto filter = new ChangeRecordFilterDto();
        // full name without first two and last two characters
        filter.setChangeType(typeUri);

        final Pageable pageable = Pageable.unpaged();

        final List<AbstractChangeRecord> contentChanges = sut.findAllRelatedToType(vocabulary, filter, SKOS_CONCEPT, pageable);

        assertEquals(recordCount, contentChanges.size());
        assertTrue(contentChanges.stream().allMatch(typeClass::isInstance));
    }

}
