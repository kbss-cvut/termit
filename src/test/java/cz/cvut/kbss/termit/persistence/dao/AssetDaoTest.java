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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private AssetDao sut;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserWithId();
        transactional(() -> em.persist(user));
        Environment.setCurrentUser(user);
    }

    @Test
    void findRecentlyEditedLoadsSpecifiedCountOfRecentlyEditedAssets() {
        enableRdfsInference(em);
        final List<Vocabulary> vocabularies = IntStream.range(0, 10).mapToObj(i -> Generator.generateVocabularyWithId())
                                                       .collect(Collectors.toList());
        transactional(() -> vocabularies.forEach(em::persist));
        final List<PersistChangeRecord> persistRecords = vocabularies.stream().map(Generator::generatePersistChange)
                                                                     .collect(Collectors.toList());
        setOldCreated(persistRecords.subList(0, 5));
        final List<URI> recent = vocabularies.subList(5, vocabularies.size()).stream().map(Vocabulary::getUri)
                                             .collect(Collectors.toList());
        transactional(() -> persistRecords.forEach(em::persist));

        final Pageable pageSpec = PageRequest.of(0, recent.size() - 2);
        final Page<RecentlyModifiedAsset> result = sut.findLastEdited(pageSpec);
        assertEquals(pageSpec.getPageSize(), result.getNumberOfElements());
        assertTrue(recent.containsAll(result.stream().map(RecentlyModifiedAsset::getUri).collect(Collectors.toList())));
    }

    @Test
    void findRecentlyEditedUsesLastModifiedDateWhenAvailable() {
        enableRdfsInference(em);
        final List<Vocabulary> vocabularies = IntStream.range(0, 10).mapToObj(i -> Generator.generateVocabularyWithId())
                                                       .collect(Collectors.toList());
        transactional(() -> vocabularies.forEach(em::persist));
        final List<PersistChangeRecord> persistRecords = vocabularies.stream().map(Generator::generatePersistChange)
                                                                     .collect(Collectors.toList());
        setOldCreated(persistRecords);
        final List<Vocabulary> recent = vocabularies.subList(5, vocabularies.size());
        final List<UpdateChangeRecord> updateRecords = recent.stream().map(Generator::generateUpdateChange)
                                                             .collect(Collectors.toList());
        transactional(() -> {
            persistRecords.forEach(em::persist);
            updateRecords.forEach(em::persist);
        });
        em.getEntityManagerFactory().getCache().evictAll();

        final Pageable pageSpec = PageRequest.of(0, 3);
        final List<URI> recentUris = recent.stream().map(Vocabulary::getUri).collect(Collectors.toList());
        final Page<RecentlyModifiedAsset> result = sut.findLastEdited(pageSpec);
        assertEquals(pageSpec.getPageSize(), result.getNumberOfElements());
        assertTrue(recentUris
                           .containsAll(
                                   result.stream().map(RecentlyModifiedAsset::getUri).collect(Collectors.toList())));
    }

    private void setOldCreated(List<PersistChangeRecord> old) {
        old.forEach(r -> r.setTimestamp(Instant.ofEpochMilli(System.currentTimeMillis() - 24 * 3600 * 1000)));
    }

    @Test
    void findRecentlyEditedReturnsAlsoTypeOfChange() {
        enableRdfsInference(em);
        final Resource resource = Generator.generateResourceWithId();
        transactional(() -> em.persist(resource));
        final PersistChangeRecord persistRecord = Generator.generatePersistChange(resource);
        transactional(() -> em.persist(persistRecord));

        final Pageable pageSpec = PageRequest.of(0, 3);
        final Page<RecentlyModifiedAsset> result = sut.findLastEdited(pageSpec);
        assertFalse(result.isEmpty());
        result.forEach(
                rma -> assertThat(rma.getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_vytvoreni_entity)));
    }

    @Test
    void findRecentlyEditedCorrectlyHandlesDifferentTypesOfAssets() {
        enableRdfsInference(em);
        final Document document = Generator.generateDocumentWithId();
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.setDocument(document);
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            em.persist(document);
            em.persist(vocabulary);
            em.persist(term);
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            try (RepositoryConnection conn = em.unwrap(Repository.class).getConnection()) {
                final ValueFactory vf = conn.getValueFactory();
                conn.add(vf.createIRI(document.getUri().toString()),
                         vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_dokumentovy_slovnik),
                         vf.createIRI(vocabulary.getUri().toString()));
            }
        });
        final Map<Asset<?>, AbstractChangeRecord> changes = new HashMap<>();
        changes.put(document, Generator.generatePersistChange(document));
        changes.put(vocabulary, Generator.generatePersistChange(vocabulary));
        changes.put(term, Generator.generatePersistChange(term));
        transactional(() -> changes.values().forEach(em::persist));

        final Pageable pageSpec = PageRequest.of(0, 10);
        final Page<RecentlyModifiedAsset> result = sut.findLastEdited(pageSpec);
        assertEquals(changes.size(), result.getNumberOfElements());
        changes.forEach((k, v) -> {
            final Optional<RecentlyModifiedAsset> rma = result.stream().filter(a -> a.getUri().equals(k.getUri()))
                                                              .findAny();
            assertTrue(rma.isPresent());
            assertEquals(k.getUri(), rma.get().getUri());
            assertThat(rma.get().getTypes(), hasItem(k.getClass().getAnnotation(OWLClass.class).iri()));
        });
    }

    @Test
    void findRecentlyEditedByUserReturnsAssetsEditedBySpecifiedUser() {
        enableRdfsInference(em);
        final List<Vocabulary> myVocabularies = IntStream.range(0, 5)
                                                         .mapToObj(i -> Generator.generateVocabularyWithId())
                                                         .collect(Collectors.toList());
        final List<PersistChangeRecord> persistRecords = myVocabularies.stream().map(Generator::generatePersistChange)
                                                                       .collect(Collectors.toList());
        final List<Vocabulary> otherVocabularies = IntStream.range(0, 5)
                                                            .mapToObj(i -> Generator.generateVocabularyWithId())
                                                            .collect(Collectors.toList());
        final User otherUser = Generator.generateUserWithId();
        transactional(() -> {
            myVocabularies.forEach(em::persist);
            otherVocabularies.forEach(em::persist);
            em.persist(otherUser);
        });
        final List<PersistChangeRecord> otherPersistRecords = otherVocabularies.stream().map(r -> {
            final PersistChangeRecord rec = Generator.generatePersistChange(r);
            rec.setAuthor(otherUser);
            return rec;
        }).collect(Collectors.toList());
        transactional(() -> {
            persistRecords.forEach(em::persist);
            otherPersistRecords.forEach(em::persist);
        });

        final Pageable pageSpec = PageRequest.of(0, 3);
        final Page<RecentlyModifiedAsset> result = sut.findLastEditedBy(user, pageSpec);
        assertFalse(result.isEmpty());
        final Set<URI> mineUris = myVocabularies.stream().map(Vocabulary::getUri).collect(Collectors.toSet());
        assertTrue(
                mineUris.containsAll(result.stream().map(RecentlyModifiedAsset::getUri).collect(Collectors.toList())));
    }

    @Test
    void findLastEditedLoadsVocabularyForTerms() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId();
        final PersistChangeRecord persistRecord = Generator.generatePersistChange(term);
        persistRecord.setAuthor(Generator.generateUserWithId());
        transactional(() -> {
            em.persist(vocabulary);
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(term);
            em.persist(persistRecord.getAuthor());
            em.persist(persistRecord);
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });

        final Page<RecentlyModifiedAsset> result = sut.findLastEdited(PageRequest.of(0, 1));
        assertFalse(result.isEmpty());
        assertEquals(term.getUri(), result.getContent().get(0).getUri());
        assertEquals(vocabulary.getUri(), result.getContent().get(0).getVocabulary());
    }

    @Test
    void findLastEditedSkipsRecordsOfDeletedAssets() {
        enableRdfsInference(em);
        final PersistChangeRecord record = Generator.generatePersistChange(new Vocabulary(Generator.generateUri()));
        transactional(() -> em.persist(record));

        final Pageable pageSpec = PageRequest.of(0, 10);
        final Page<RecentlyModifiedAsset> result = sut.findLastEdited(pageSpec);
        assertTrue(result.stream().noneMatch(rma -> rma.getUri().equals(record.getChangedEntity())));
    }
}
