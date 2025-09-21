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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.AggregatedChangeInfo;
import cz.cvut.kbss.termit.dto.PrefixDeclaration;
import cz.cvut.kbss.termit.dto.RdfStatement;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.filter.ChangeRecordFilterDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.AssetPersistEvent;
import cz.cvut.kbss.termit.event.AssetUpdateEvent;
import cz.cvut.kbss.termit.event.RefreshLastModifiedEvent;
import cz.cvut.kbss.termit.event.VocabularyEvent;
import cz.cvut.kbss.termit.event.VocabularyWillBeRemovedEvent;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Model;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.util.EntityToOwlClassMapper;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.util.Constants;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.environment.util.ContainsSameEntities.containsSameEntities;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class VocabularyDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Spy
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private VocabularyDao sut;

    @SpyBean
    private ChangeRecordDao changeRecordDao;

    private User author;

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
        sut.setApplicationEventPublisher(eventPublisher);
    }

    @Test
    void findAllReturnsVocabulariesOrderedByName() {
        final List<Vocabulary> vocabularies = IntStream.range(0, 5).mapToObj(i -> Generator.generateVocabularyWithId())
                                                       .collect(Collectors.toList());
        transactional(() -> vocabularies.forEach(v -> em.persist(v, descriptorFor(v))));

        final List<Vocabulary> result = sut.findAll();
        vocabularies.sort(Comparator.comparing(Environment::getPrimaryLabel));
        for (int i = 0; i < vocabularies.size(); i++) {
            assertEquals(vocabularies.get(i).getUri(), result.get(i).getUri());
        }
    }

    @Test
    void persistSavesVocabularyIntoContextGivenByItsIri() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> sut.persist(vocabulary));

        final Descriptor descriptor = descriptorFor(vocabulary);
        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri(), descriptor);
        assertNotNull(result);
    }

    private Descriptor descriptorFor(Vocabulary vocabulary) {
        return descriptorFactory.vocabularyDescriptor(vocabulary);
    }

    @Test
    void updateUpdatesVocabularyInContextGivenByItsIri() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));

        final String newName = "Updated vocabulary name";
        vocabulary.setLabel(MultilingualString.create(newName, Environment.LANGUAGE));
        transactional(() -> sut.update(vocabulary));

        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri(), descriptor);
        assertNotNull(result);
        assertEquals(newName, result.getLabel().get(Environment.LANGUAGE));
    }

    @Test
    void updateEvictsPossiblyPreviouslyLoadedInstanceFromSecondLevelCache() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));
        // This causes the second level cache to be initialized with the loaded vocabulary (in the default context)
        final List<Vocabulary> vocabularies = sut.findAll();
        assertEquals(1, vocabularies.size());

        final String newName = "Updated vocabulary name";
        vocabulary.setLabel(MultilingualString.create(newName, Environment.LANGUAGE));
        transactional(() -> sut.update(vocabulary));
        final List<Vocabulary> result = sut.findAll();
        assertEquals(1, result.size());
        assertEquals(newName, result.get(0).getLabel().get(Environment.LANGUAGE));
    }

    @Test
    void updateWorksCorrectlyInContextsForDocumentVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.setModel(new Model());
        final Document doc = new Document();
        doc.setLabel("test-document");
        doc.setUri(Generator.generateUri());
        final File file = new File();
        file.setLabel("test-file");
        file.setUri(Generator.generateUri());
        doc.addFile(file);
        vocabulary.setDocument(doc);
        final Descriptor vocabularyDescriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        final Descriptor docDescriptor = descriptorFactory.documentDescriptor(vocabulary);
        transactional(() -> {
            em.persist(file, docDescriptor);
            em.persist(doc, docDescriptor);
            em.persist(vocabulary, vocabularyDescriptor);
        });

        final String newComment = "New comment";
        vocabulary.setDescription(MultilingualString.create(newComment, Environment.LANGUAGE));
        transactional(() -> sut.update(vocabulary));

        final Vocabulary result = em.find(Vocabulary.class, vocabulary.getUri(), vocabularyDescriptor);
        assertEquals(newComment, result.getDescription().get(Environment.LANGUAGE));
    }

    @Test
    void updateGlossaryMergesGlossaryIntoPersistenceContext() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));
        final Term term = Generator.generateTermWithId();
        vocabulary.getGlossary().addRootTerm(term);
        final Descriptor termDescriptor = descriptorFactory.termDescriptor(vocabulary);
        transactional(() -> {
            em.persist(term, termDescriptor);
            sut.updateGlossary(vocabulary);
        });

        transactional(() -> {
            // If we don't run this in transaction, the delegate em is closed right after find and lazy loading of terms
            // does not work
            final Glossary result = em.find(Glossary.class, vocabulary.getGlossary().getUri());
            assertTrue(result.getRootTerms().contains(term.getUri()));
        });
    }

    @Test
    void updateGlossaryMergesGlossaryIntoCorrectRepositoryContext() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));
        final Term term = Generator.generateTermWithId();
        vocabulary.getGlossary().addRootTerm(term);
        final Descriptor termDescriptor = descriptorFactory.termDescriptor(vocabulary);
        transactional(() -> {
            em.persist(term, termDescriptor);
            sut.updateGlossary(vocabulary);
        });

        final Glossary result = em.find(Glossary.class, vocabulary.getGlossary().getUri(),
                                        descriptorFactory.glossaryDescriptor(vocabulary));
        assertTrue(result.getRootTerms().contains(term.getUri()));
    }

    @Test
    void updateGlossaryReturnsManagedGlossaryInstance() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));
        transactional(() -> {
            final Glossary merged = sut.updateGlossary(vocabulary);
            assertTrue(em.contains(merged));
        });
    }

    @Test
    void hasHierarchyBetweenTermsReturnsFalseForVocabulariesWithoutSKOSRelatedTerms() {
        final Vocabulary subjectVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary targetVocabulary = Generator.generateVocabularyWithId();
        transactional(() -> {
            em.persist(subjectVocabulary, descriptorFactory.vocabularyDescriptor(subjectVocabulary));
            em.persist(targetVocabulary, descriptorFactory.vocabularyDescriptor(targetVocabulary));
        });

        assertFalse(sut.hasHierarchyBetweenTerms(subjectVocabulary.getUri(), targetVocabulary.getUri()));
    }

    @Test
    void hasHierarchyBetweenTermsReturnsTrueForSKOSRelatedTermsInSpecifiedVocabularies() {
        final Vocabulary subjectVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary targetVocabulary = Generator.generateVocabularyWithId();
        subjectVocabulary.setImportedVocabularies(Collections.singleton(targetVocabulary.getUri()));
        final Term child = Generator.generateTermWithId();
        final Term parentTerm = Generator.generateTermWithId();
        child.addParentTerm(parentTerm);
        subjectVocabulary.getGlossary().addRootTerm(child);
        targetVocabulary.getGlossary().addRootTerm(parentTerm);
        transactional(() -> {
            em.persist(subjectVocabulary, descriptorFactory.vocabularyDescriptor(subjectVocabulary));
            em.persist(targetVocabulary, descriptorFactory.vocabularyDescriptor(targetVocabulary));
            child.setGlossary(subjectVocabulary.getGlossary().getUri());
            em.persist(child, descriptorFactory.termDescriptor(subjectVocabulary));
            parentTerm.setGlossary(targetVocabulary.getGlossary().getUri());
            em.persist(parentTerm, descriptorFactory.termDescriptor(targetVocabulary));
            Generator.addTermInVocabularyRelationship(child, subjectVocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(parentTerm, targetVocabulary.getUri(), em);
        });

        assertTrue(sut.hasHierarchyBetweenTerms(subjectVocabulary.getUri(), targetVocabulary.getUri()));
    }

    @Test
    void hasHierarchyBetweenTermsReturnsTrueForSKOSRelatedTermsInTransitivelyImportedVocabularies() {
        final Vocabulary subjectVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary targetVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary transitiveVocabulary = Generator.generateVocabularyWithId();
        subjectVocabulary.setImportedVocabularies(Collections.singleton(targetVocabulary.getUri()));
        targetVocabulary.setImportedVocabularies(Collections.singleton(transitiveVocabulary.getUri()));
        final Term child = Generator.generateTermWithId();
        final Term parentTerm = Generator.generateTermWithId();
        child.addParentTerm(parentTerm);
        subjectVocabulary.getGlossary().addRootTerm(child);
        transitiveVocabulary.getGlossary().addRootTerm(parentTerm);
        transactional(() -> {
            em.persist(subjectVocabulary, descriptorFactory.vocabularyDescriptor(subjectVocabulary));
            em.persist(targetVocabulary, descriptorFactory.vocabularyDescriptor(targetVocabulary));
            em.persist(transitiveVocabulary, descriptorFactory.vocabularyDescriptor(transitiveVocabulary));
            child.setGlossary(subjectVocabulary.getGlossary().getUri());
            em.persist(child, descriptorFactory.termDescriptor(subjectVocabulary));
            parentTerm.setGlossary(transitiveVocabulary.getGlossary().getUri());
            em.persist(parentTerm, descriptorFactory.termDescriptor(transitiveVocabulary));
            Generator.addTermInVocabularyRelationship(child, subjectVocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(parentTerm, transitiveVocabulary.getUri(), em);
        });

        assertTrue(sut.hasHierarchyBetweenTerms(subjectVocabulary.getUri(), targetVocabulary.getUri()));
    }

    @Test
    void getTransitivelyImportedVocabulariesReturnsAllImportedVocabulariesForVocabulary() {
        final Vocabulary subjectVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary importedVocabularyOne = Generator.generateVocabularyWithId();
        final Vocabulary importedVocabularyTwo = Generator.generateVocabularyWithId();
        final Vocabulary transitiveVocabulary = Generator.generateVocabularyWithId();
        subjectVocabulary.setImportedVocabularies(
                new HashSet<>(Arrays.asList(importedVocabularyOne.getUri(), importedVocabularyTwo.getUri())));
        importedVocabularyOne.setImportedVocabularies(Collections.singleton(transitiveVocabulary.getUri()));
        transactional(() -> {
            em.persist(subjectVocabulary, descriptorFactory.vocabularyDescriptor(subjectVocabulary));
            em.persist(importedVocabularyOne, descriptorFactory.vocabularyDescriptor(importedVocabularyOne));
            em.persist(importedVocabularyTwo, descriptorFactory.vocabularyDescriptor(importedVocabularyTwo));
            em.persist(transitiveVocabulary, descriptorFactory.vocabularyDescriptor(transitiveVocabulary));
        });

        final Collection<URI> result = sut.getTransitivelyImportedVocabularies(subjectVocabulary.getUri());
        assertEquals(3, result.size());
        assertTrue(result.contains(importedVocabularyOne.getUri()));
        assertTrue(result.contains(importedVocabularyTwo.getUri()));
        assertTrue(result.contains(transitiveVocabulary.getUri()));
    }

    @Test
    void initializesLastModificationTimestampToCurrentDateTimeOnInit() {
        final long result = sut.getLastModified();
        assertThat(result, greaterThan(0L));
        assertThat(result, lessThanOrEqualTo(System.currentTimeMillis()));
    }

    @Test
    void refreshLastModifiedUpdatesLastModifiedTimestampToCurrentDateTime() throws Exception {
        final long before = sut.getLastModified();
        Thread.sleep(100);  // force time to move on
        sut.refreshLastModified(new RefreshLastModifiedEvent(this));
        final long after = sut.getLastModified();
        assertThat(after, greaterThan(before));
    }

    @Test
    void persistRefreshesLastModifiedValue() {
        final long before = sut.getLastModified();
        final Vocabulary voc = Generator.generateVocabularyWithId();
        transactional(() -> sut.persist(voc));
        final long after = sut.getLastModified();
        assertThat(after, greaterThan(before));
    }

    @Test
    void persistPublishesAssetPersistEvent() {
        final Vocabulary voc = Generator.generateVocabularyWithId();
        transactional(() -> sut.persist(voc));

        final ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        final Optional<AssetPersistEvent> evt = captor.getAllValues().stream()
                                                      .filter(AssetPersistEvent.class::isInstance)
                                                      .map(AssetPersistEvent.class::cast).findFirst();
        assertTrue(evt.isPresent());
        assertEquals(voc, evt.get().getAsset());
    }

    @Test
    void removeRefreshesLastModifiedValue() {
        final long before = sut.getLastModified();
        final Vocabulary voc = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(voc, descriptorFactory.vocabularyDescriptor(voc)));
        transactional(() -> sut.remove(voc));
        final long after = sut.getLastModified();
        assertThat(after, greaterThan(before));
    }

    @Test
    void updateRefreshesLastModifiedValue() {
        final long before = sut.getLastModified();
        final Vocabulary voc = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(voc, descriptorFactory.vocabularyDescriptor(voc)));
        final String newLabel = "New vocabulary label";
        voc.setLabel(MultilingualString.create(newLabel, Environment.LANGUAGE));
        transactional(() -> sut.update(voc));
        final Optional<Vocabulary> result = sut.find(voc.getUri());
        assertTrue(result.isPresent());
        assertEquals(newLabel, result.get().getLabel().get(Environment.LANGUAGE));
        final long after = sut.getLastModified();
        assertThat(after, greaterThan(before));
    }

    @Test
    void updatePublishesAssetUpdateEvent() {
        final Vocabulary voc = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(voc, descriptorFactory.vocabularyDescriptor(voc)));
        final String newLabel = "New vocabulary label";
        voc.setLabel(MultilingualString.create(newLabel, Environment.LANGUAGE));

        transactional(() -> sut.update(voc));
        final ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        final Optional<AssetUpdateEvent> evt = captor.getAllValues().stream()
                                                     .filter(AssetUpdateEvent.class::isInstance)
                                                     .map(AssetUpdateEvent.class::cast).findFirst();
        assertTrue(evt.isPresent());
        assertEquals(voc, evt.get().getAsset());
    }

    @Test
    void getChangesOfContentLoadsAggregatedChangesOfTermsInVocabulary() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term termOne = Generator.generateTermWithId();
        final Term termTwo = Generator.generateTermWithId();
        final List<AbstractChangeRecord> oneChanges = Generator.generateChangeRecords(termOne, author);
        // Randomize the timestamp. We do not care about sequentiality of persist/update here
        oneChanges.forEach(ch -> ch.setTimestamp(Instant.now().minus(Generator.randomInt(1, 10), ChronoUnit.DAYS)));
        final List<AbstractChangeRecord> twoChanges = Generator.generateChangeRecords(termTwo, author);
        twoChanges.forEach(ch -> ch.setTimestamp(Instant.now().minus(Generator.randomInt(1, 10), ChronoUnit.DAYS)));
        transactional(() -> {
            vocabulary.getGlossary().addRootTerm(termOne);
            vocabulary.getGlossary().addRootTerm(termTwo);
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            termOne.setGlossary(vocabulary.getGlossary().getUri());
            termTwo.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(termOne, descriptorFactory.termDescriptor(vocabulary));
            em.persist(termTwo, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(termOne, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(termTwo, vocabulary.getUri(), em);
            oneChanges.forEach(ch -> em.persist(ch));
            twoChanges.forEach(ch -> em.persist(ch));
        });
        final Map<LocalDate, Integer> persists = resolveExpectedPersists(oneChanges, twoChanges);
        final Map<LocalDate, Integer> updates = resolveExpectedUpdates(oneChanges, twoChanges);

        final List<AggregatedChangeInfo> result = sut.getChangesOfContent(vocabulary);
        result.stream().filter(r -> r.hasType(cz.cvut.kbss.termit.util.Vocabulary.s_c_vytvoreni_entity))
              .forEach(r -> {
                  assertTrue(persists.containsKey(r.getDate()));
                  assertEquals(persists.get(r.getDate()), r.getCount());
              });
        result.stream().filter(r -> r.hasType(cz.cvut.kbss.termit.util.Vocabulary.s_c_uprava_entity))
              .forEach(r -> {
                  assertTrue(updates.containsKey(r.getDate()));
                  assertEquals(updates.get(r.getDate()), r.getCount());
              });
    }

    private Map<LocalDate, Integer> resolveExpectedPersists(List<AbstractChangeRecord> oneChanges,
                                                            List<AbstractChangeRecord> twoChanges) {
        final Map<LocalDate, Integer> persists = new HashMap<>();
        // Expect at most one persist record
        removeDuplicateDailyRecords(oneChanges, PersistChangeRecord.class).stream()
                                                                          .filter(ch -> ch instanceof PersistChangeRecord)
                                                                          .forEach(ch -> persists.put(
                                                                                  LocalDate.ofInstant(ch.getTimestamp(),
                                                                                                      ZoneId.systemDefault()),
                                                                                  1));
        removeDuplicateDailyRecords(twoChanges, PersistChangeRecord.class).stream()
                                                                          .filter(ch -> ch instanceof PersistChangeRecord)
                                                                          .forEach(ch -> persists.compute(
                                                                                  LocalDate.ofInstant(ch.getTimestamp(),
                                                                                                      ZoneId.systemDefault()),
                                                                                  (k, v) -> v == null ? 1 : 2));
        return persists;
    }

    /**
     * Ensures there is at most one change record per day.
     */
    private Collection<AbstractChangeRecord> removeDuplicateDailyRecords(Collection<AbstractChangeRecord> records,
                                                                         Class<? extends AbstractChangeRecord> type) {
        final Map<LocalDate, AbstractChangeRecord> map = new HashMap<>();
        records.stream().filter(type::isInstance)
               .forEach(r -> map.put(LocalDate.ofInstant(r.getTimestamp(), ZoneId.systemDefault()), r));
        return map.values();
    }

    private Map<LocalDate, Integer> resolveExpectedUpdates(List<AbstractChangeRecord> oneChanges,
                                                           List<AbstractChangeRecord> twoChanges) {
        final Map<LocalDate, Integer> updates = new HashMap<>();
        removeDuplicateDailyRecords(oneChanges, UpdateChangeRecord.class).stream()
                                                                         .filter(ch -> ch instanceof UpdateChangeRecord)
                                                                         .forEach(ch -> updates.put(
                                                                                 LocalDate.ofInstant(ch.getTimestamp(),
                                                                                                     ZoneId.systemDefault()),
                                                                                 1));
        removeDuplicateDailyRecords(twoChanges, UpdateChangeRecord.class).stream()
                                                                         .filter(ch -> ch instanceof UpdateChangeRecord)
                                                                         .forEach(ch -> updates.compute(
                                                                                 LocalDate.ofInstant(ch.getTimestamp(),
                                                                                                     ZoneId.systemDefault()),
                                                                                 (k, v) -> v == null ? 1 : 2));
        return updates;
    }

    @Test
    void findGlossaryReturnsTheGlossary() {
        final Glossary glossary = new Glossary();
        URI uri = URI.create("https://example.org/1");
        glossary.setUri(uri);
        transactional(() -> em.persist(glossary));
        final Optional<Glossary> result = sut.findGlossary(uri);
        assertTrue(result.isPresent());
        assertEquals(uri, result.get().getUri());
    }

    @Test
    void getTermCountRetrievesNumberOfTermsInVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = IntStream.range(0, 10).mapToObj(i -> Generator.generateTermWithId(vocabulary.getUri()))
                                          .toList();
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            terms.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t));
                Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
            });
        });

        assertEquals(terms.size(), sut.getTermCount(vocabulary));
    }

    @Test
    void getTermCountReturnsZeroForUnknownVocabulary() {
        assertEquals(0, sut.getTermCount(Generator.generateVocabularyWithId()));
    }

    @Test
    void removeCascadesOperationToGlossaryAndModel() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));

        transactional(() -> sut.remove(vocabulary));
        assertNull(em.find(Glossary.class, vocabulary.getGlossary().getUri()));
        assertNull(em.find(Model.class, vocabulary.getModel().getUri()));
    }

    @Test
    void findSnapshotsRetrievesSnapshotsOfSpecifiedVocabularyOrderedByCreationDateDescending() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));
        final List<Vocabulary> snapshots = IntStream.range(0, 5).mapToObj(i -> {
            final Instant timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(i * 2L);
            return generateSnapshotStub(vocabulary, timestamp);
        }).collect(Collectors.toList());

        final List<Snapshot> result = sut.findSnapshots(vocabulary);
        assertEquals(result.size(), snapshots.size());
        assertThat(result, containsSameEntities(snapshots));
    }

    private Vocabulary generateSnapshotStub(Vocabulary vocabulary, Instant timestamp) {
        final String strTimestamp = timestamp.toString().replace(":", "");
        final Vocabulary stub = new Vocabulary();
        stub.setUri(URI.create(vocabulary.getUri().toString() + "/version/" + strTimestamp));
        final Glossary glossaryStub = new Glossary();
        glossaryStub.setUri(URI.create(vocabulary.getGlossary().getUri().toString() + "/version/" + strTimestamp));
        stub.setGlossary(glossaryStub);
        final Model modelStub = new Model();
        modelStub.setUri(URI.create(vocabulary.getModel().getUri().toString() + "/version/" + strTimestamp));
        stub.setModel(modelStub);
        stub.setLabel(vocabulary.getLabel());
        stub.setDescription(vocabulary.getDescription());
        stub.setPrimaryLanguage(vocabulary.getPrimaryLanguage());
        transactional(() -> {
            final Descriptor descriptor = descriptorFactory.vocabularyDescriptor(stub);
            em.persist(stub, descriptor);
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection connection = repo.getConnection()) {
                final ValueFactory vf = connection.getValueFactory();
                final IRI stubIri = vf.createIRI(stub.getUri().toString());
                connection.begin();
                connection.add(stubIri, vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_slovniku),
                               vf.createIRI(vocabulary.getUri().toString()), stubIri);
                connection.add(stubIri,
                               vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_datum_a_cas_vytvoreni_verze),
                               vf.createLiteral(Date.from(timestamp)), stubIri);
                connection.add(stubIri, RDF.TYPE, vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku),
                               stubIri);
                connection.commit();
            }
        });
        return stub;
    }

    @Test
    void findVersionValidAtReturnsSnapshotValidAtSpecifiedInstant() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));
        Vocabulary expected = null;
        Instant validAt = null;
        Instant timestamp;
        for (int i = 0; i < 5; i++) {
            timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus((i + 1) * 2L, ChronoUnit.DAYS);
            final Vocabulary v = generateSnapshotStub(vocabulary, timestamp);
            if (i == 3) {
                expected = v;
                validAt = timestamp.plus(1, ChronoUnit.HOURS);
            }
        }

        final Optional<Vocabulary> result = sut.findVersionValidAt(vocabulary, validAt);
        assertTrue(result.isPresent());
        assertEquals(expected, result.get());
    }

    @Test
    void findVersionValidAtReturnsNullWhenNoSnapshotAtSpecifiedInstantExists() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));
        IntStream.range(0, 5).forEach(i -> {
            final Instant timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS)
                                             .minus((i + 1) * 2L, ChronoUnit.HOURS);
            generateSnapshotStub(vocabulary, timestamp);
        });

        final Optional<Vocabulary> result = sut.findVersionValidAt(vocabulary, Instant.now().minus(1, ChronoUnit.DAYS));
        assertNotNull(result);
        assertFalse(result.isPresent());
    }

    @Test
    void findAllDoesNotIncludeSnapshotsInResult() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Descriptor descriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> em.persist(vocabulary, descriptor));
        generateSnapshotStub(vocabulary, Instant.now().truncatedTo(ChronoUnit.SECONDS));

        final List<Vocabulary> result = sut.findAll();
        assertEquals(Collections.singletonList(vocabulary), result);
    }

    @Test
    void isEmptyReturnsTrueForEmptyVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFor(vocabulary)));
        assertTrue(sut.isEmpty(vocabulary));
    }

    @Test
    void isEmptyReturnsFalseForNonemptyVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = Collections.singletonList(Generator.generateTermWithId(vocabulary.getUri()));
        transactional(() -> {
            em.persist(vocabulary, descriptorFor(vocabulary));
            terms.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t));
                Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
            });
        });
        assertFalse(sut.isEmpty(vocabulary));
    }

    @Test
    void resolvePrefixRetrievesPrefixDeclarationForVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.setProperties(new HashMap<>());
        final String prefix = "vocab";
        final String namespace = cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik + "/";
        vocabulary.getProperties().put(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespacePrefix,
                                       Collections.singleton(prefix));
        vocabulary.getProperties().put(cz.cvut.kbss.termit.util.Vocabulary.s_p_preferredNamespaceUri,
                                       Collections.singleton(namespace));
        transactional(() -> em.persist(vocabulary, descriptorFor(vocabulary)));

        final PrefixDeclaration result = sut.resolvePrefix(vocabulary.getUri());
        assertNotNull(result);
        assertEquals(prefix, result.getPrefix());
        assertEquals(namespace, result.getNamespace());
    }

    @Test
    void removeVocabularyRemovesVocabularyGlossaryModelAndAllTermsWithoutDocument() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = IntStream.range(0, 10).mapToObj(i -> Generator.generateTermWithId(vocabulary.getUri()))
                                          .toList();
        final Document doc = Generator.generateDocumentWithId();
        vocabulary.setDocument(doc);
        transactional(() -> {
            em.persist(vocabulary, descriptorFor(vocabulary));
            em.persist(doc, descriptorFactory.documentDescriptor(vocabulary));
            terms.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t));
                Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
            });
        });

        transactional(() -> sut.removeVocabularyKeepDocument(vocabulary));
        final String query = "ASK { ?x a ?type }";
        // vocabulary removed
        assertFalse(em.createNativeQuery(query, Boolean.class)
                      .setParameter("type", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik))
                      .getSingleResult());
        // glossary removed
        assertFalse(em.createNativeQuery(query, Boolean.class)
                      .setParameter("type", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_glosar))
                      .getSingleResult());
        // model removed
        assertFalse(em.createNativeQuery(query, Boolean.class)
                      .setParameter("type", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_model))
                      .getSingleResult());

        // all terms removed
        assertFalse(em.createNativeQuery(query, Boolean.class).setParameter("type", URI.create(SKOS.CONCEPT))
                      .getSingleResult());

        // document not removed
        assertTrue(em.createNativeQuery(query, Boolean.class)
                     .setParameter("type", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_dokument))
                     .getSingleResult());

        // vocabulary removed from cache
        assertFalse(em.getEntityManagerFactory().getCache().contains(Vocabulary.class, vocabulary.getUri(),
                                                                     descriptorFactory.vocabularyDescriptor(
                                                                             vocabulary)));
    }

    @Test
    void removePublishesEventAndDropsGraph() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = IntStream.range(0, 10).mapToObj(i -> Generator.generateTermWithId(vocabulary.getUri()))
                                          .toList();
        final Document doc = Generator.generateDocumentWithId();
        vocabulary.setDocument(doc);
        transactional(() -> {
            em.persist(vocabulary, descriptorFor(vocabulary));
            em.persist(doc, descriptorFactory.documentDescriptor(vocabulary));
            terms.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t));
                Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
            });
        });

        assertTrue(em.createNativeQuery("ASK WHERE { GRAPH ?vocabulary { ?s ?p ?o }}", Boolean.class)
                      .setParameter("vocabulary", vocabulary.getUri())
                      .getSingleResult());

        transactional(() -> sut.remove(vocabulary));

        ArgumentCaptor<VocabularyEvent> eventCaptor = ArgumentCaptor.forClass(VocabularyWillBeRemovedEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

        VocabularyWillBeRemovedEvent event = (VocabularyWillBeRemovedEvent) eventCaptor
                .getAllValues().stream()
                .filter(e -> e instanceof VocabularyWillBeRemovedEvent)
                .findAny().orElseThrow();
        
        assertNotNull(event);

        assertEquals(event.getVocabularyIri(), vocabulary.getUri());

        assertFalse(em.createNativeQuery("ASK WHERE{ GRAPH ?vocabulary { ?s ?p ?o }}", Boolean.class)
                      .setParameter("vocabulary", vocabulary.getUri())
                      .getSingleResult());
    }

    @Test
    void persistPersistsDocumentWhenItDoesNotExist() {
        final Vocabulary instance = Generator.generateVocabularyWithId();
        final Document document = Generator.generateDocumentWithId();
        instance.setDocument(document);
        transactional(() -> sut.persist(instance));

        final Vocabulary result = em.find(Vocabulary.class, instance.getUri(), descriptorFor(instance));
        assertNotNull(result);
        assertNotNull(result.getDocument());
        assertEquals(document, result.getDocument());
        assertEquals(document,
                     em.find(Document.class, document.getUri(), descriptorFactory.documentDescriptor(instance)));
    }

    @Test
    void internalAddRealtionCreatesRelation() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Vocabulary secondVocabulary = Generator.generateVocabularyWithId();
        final URI relation = URI.create(SKOS.RELATED);

        transactional(() -> {
            sut.persist(vocabulary);
            sut.persist(secondVocabulary);

            Environment.addRelation(vocabulary.getUri(), relation, secondVocabulary.getUri(), em);
        });

        Boolean result = em.createNativeQuery("""
                                   ASK WHERE {
                                       ?vocabulary a ?vocabularyType .
                                       ?secondVocabulary a ?vocabularyType .
                                       ?vocabulary ?relation ?secondVocabulary .
                                   }
                                   """, Boolean.class)
                           .setParameter("vocabulary", vocabulary.getUri())
                           .setParameter("secondVocabulary", secondVocabulary.getUri())
                           .setParameter("vocabularyType", URI.create(EntityToOwlClassMapper.getOwlClassForEntity(Vocabulary.class)))
                           .setParameter("relation", relation).getSingleResult();

        assertTrue(result);
    }

    public static Set<URI> skosConceptMatchRelationshipsSource() {
        return Constants.SKOS_CONCEPT_MATCH_RELATIONSHIPS;
    }

    /**
     * term - relation - secondTerm
     */
    @ParameterizedTest
    @MethodSource("cz.cvut.kbss.termit.persistence.dao.VocabularyDaoTest#skosConceptMatchRelationshipsSource")
    void getAnyExternalRelationsReturnsTermsWithOutgoingRelations(URI termRelation) {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Vocabulary secondVocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Term secondTerm = Generator.generateTermWithId(secondVocabulary.getUri());

        transactional(() -> {
            sut.persist(vocabulary);
            sut.persist(secondVocabulary);

            em.persist(term, descriptorFactory.termDescriptor(term));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);

            em.persist(secondTerm, descriptorFactory.termDescriptor(secondTerm));
            Generator.addTermInVocabularyRelationship(secondTerm, secondVocabulary.getUri(), em);

            Environment.addRelation(term.getUri(), termRelation, secondTerm.getUri(), em);
        });

        final List<RdfStatement> relations = sut.getTermRelations(vocabulary);

        assertEquals(1, relations.size());
        final RdfStatement relation = relations.get(0);
        assertEquals(term.getUri(), relation.getObject());
        assertEquals(termRelation, relation.getRelation());
        assertEquals(secondTerm.getUri(), relation.getSubject());
    }

    /**
     * secondTerm - relation - term
     */
    @ParameterizedTest
    @MethodSource("cz.cvut.kbss.termit.persistence.dao.VocabularyDaoTest#skosConceptMatchRelationshipsSource")
    void getAnyExternalRelationsReturnsTermsWithIncommingRelations(URI termRelation) {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Vocabulary secondVocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Term secondTerm = Generator.generateTermWithId(secondVocabulary.getUri());

        transactional(() -> {
            sut.persist(vocabulary);
            sut.persist(secondVocabulary);

            em.persist(term, descriptorFactory.termDescriptor(term));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);

            em.persist(secondTerm, descriptorFactory.termDescriptor(secondTerm));
            Generator.addTermInVocabularyRelationship(secondTerm, secondVocabulary.getUri(), em);

            Environment.addRelation(secondTerm.getUri(), termRelation, term.getUri(), em);
        });

        final List<RdfStatement> relations = sut.getTermRelations(vocabulary);

        assertEquals(1, relations.size());
        final RdfStatement relation = relations.get(0);
        assertEquals(secondTerm.getUri(), relation.getObject());
        assertEquals(termRelation, relation.getRelation());
        assertEquals(term.getUri(), relation.getSubject());
    }

    /**
     * secondTerm - relation - term<br>
     * term - relation - secondTerm
     */
    @ParameterizedTest
    @MethodSource("cz.cvut.kbss.termit.persistence.dao.VocabularyDaoTest#skosConceptMatchRelationshipsSource")
    void getAnyExternalRelationsReturnsTermsWithBothRelations(URI termRelation) {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Vocabulary secondVocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Term secondTerm = Generator.generateTermWithId(secondVocabulary.getUri());

        transactional(() -> {
            sut.persist(vocabulary);
            sut.persist(secondVocabulary);

            em.persist(term, descriptorFactory.termDescriptor(term));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);

            em.persist(secondTerm, descriptorFactory.termDescriptor(secondTerm));
            Generator.addTermInVocabularyRelationship(secondTerm, secondVocabulary.getUri(), em);

            Environment.addRelation(secondTerm.getUri(), termRelation, term.getUri(), em);
            Environment.addRelation(term.getUri(), termRelation, secondTerm.getUri(), em);
        });

        final List<RdfStatement> relations = sut.getTermRelations(vocabulary);

        assertEquals(2, relations.size());
        relations.forEach(relation -> {
            assertEquals(termRelation, relation.getRelation());
            if(relation.getObject().equals(term.getUri())) {
                assertEquals(secondTerm.getUri(), relation.getSubject());
            } else if(relation.getObject().equals(secondTerm.getUri())) {
                assertEquals(term.getUri(), relation.getSubject());
            } else {
                Assertions.fail("The Relation object is neither a term nor a secondTerm");
            }
        });
    }

    @Test
    void getDetailedHistoryOfContentCallsChangeRecordDaoWithFilter() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<AbstractChangeRecord> records = List.of();
        final URI skosConcept = URI.create(SKOS.CONCEPT);
        final Pageable unpaged = Pageable.unpaged();
        final ChangeRecordFilterDto filterDto = new ChangeRecordFilterDto();
        filterDto.setAuthorName("Name of the author");

        doReturn(records).when(changeRecordDao).findAllRelatedToType(vocabulary, filterDto, skosConcept, unpaged);

        sut.getDetailedHistoryOfContent(vocabulary, filterDto, unpaged);

        verify(changeRecordDao).findAllRelatedToType(vocabulary, filterDto, skosConcept, unpaged);
    }

    @Test
    void getLanguagesReturnsDistinctLanguagesUsedByVocabularyTerms() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Term term2 = Generator.generateTermWithId(vocabulary.getUri());
        term2.getLabel().set("cs", "Název v češtině");
        transactional(() -> {
            em.persist(vocabulary, descriptorFor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(term));
            em.persist(term2, descriptorFactory.termDescriptor(term2));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(term2, vocabulary.getUri(), em);
        });

        final List<String> languages = sut.getLanguages(vocabulary.getUri());
        assertEquals(2, languages.size());
        assertThat(languages, hasItems(Environment.LANGUAGE, "cs"));
    }

    @Test
    void getPrimaryLanguageReturnsThePrimaryLanguageOfTheVocabulary() {
        final String lang = "pl";
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Vocabulary vocabulary2 = Generator.generateVocabularyWithId();
        vocabulary2.setPrimaryLanguage(lang);
        transactional(()->{
            em.persist(vocabulary);
            em.persist(vocabulary2);
        });

        final String primaryLanguage = sut.getPrimaryLanguage(vocabulary.getUri());
        final String primaryLanguage2 = sut.getPrimaryLanguage(vocabulary2.getUri());
        assertEquals(Environment.LANGUAGE, primaryLanguage);
        assertEquals(lang, primaryLanguage2);
    }
}
