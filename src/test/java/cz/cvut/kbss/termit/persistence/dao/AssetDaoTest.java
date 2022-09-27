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
import cz.cvut.kbss.termit.model.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;

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
    void findRecentlyEditedLoadsSpecifiedCountOfRecentlyEditedResources() {
        enableRdfsInference(em);
        final List<Resource> resources = IntStream.range(0, 10).mapToObj(i -> Generator.generateResourceWithId())
                                                  .collect(Collectors.toList());
        transactional(() -> resources.forEach(em::persist));
        final List<PersistChangeRecord> persistRecords = resources.stream().map(Generator::generatePersistChange)
                                                                  .collect(Collectors.toList());
        setOldCreated(persistRecords.subList(0, 5));
        final List<URI> recent = resources.subList(5, resources.size()).stream().map(Resource::getUri)
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
        final List<Resource> resources = IntStream.range(0, 10).mapToObj(i -> Generator.generateResourceWithId())
                                                  .collect(Collectors.toList());
        transactional(() -> resources.forEach(em::persist));
        final List<PersistChangeRecord> persistRecords = resources.stream().map(Generator::generatePersistChange)
                                                                  .collect(Collectors.toList());
        setOldCreated(persistRecords);
        final List<Resource> recent = resources.subList(5, resources.size());
        final List<UpdateChangeRecord> updateRecords = recent.stream().map(Generator::generateUpdateChange)
                                                             .collect(Collectors.toList());
        transactional(() -> {
            persistRecords.forEach(em::persist);
            updateRecords.forEach(em::persist);
        });
        em.getEntityManagerFactory().getCache().evictAll();

        final Pageable pageSpec = PageRequest.of(0, 3);
        final List<URI> recentUris = recent.stream().map(Resource::getUri).collect(Collectors.toList());
        final Page<RecentlyModifiedAsset> result = sut.findLastEdited(pageSpec);
        assertEquals(pageSpec.getPageSize(), result.getNumberOfElements());
        assertTrue(recentUris
                           .containsAll(result.stream().map(RecentlyModifiedAsset::getUri).collect(Collectors.toList())));
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
        result.forEach(rma -> assertThat(rma.getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_vytvoreni_entity)));
    }

    @Test
    void findRecentlyEditedCorrectlyHandlesDifferentTypesOfAssets() {
        enableRdfsInference(em);
        final Resource resource = Generator.generateResourceWithId();
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            em.persist(resource);
            em.persist(vocabulary);
            em.persist(term);
        });
        final Map<Asset<?>, AbstractChangeRecord> changes = new HashMap<>();
        changes.put(resource, Generator.generatePersistChange(resource));
        changes.put(vocabulary, Generator.generatePersistChange(vocabulary));
        changes.put(term, Generator.generatePersistChange(term));
        transactional(() -> changes.values().forEach(em::persist));

        final Pageable pageSpec = PageRequest.of(0, 10);
        final Page<RecentlyModifiedAsset> result = sut.findLastEdited(pageSpec);
        assertEquals(changes.size(), result.getNumberOfElements());
        changes.forEach((k, v) -> {
            final Optional<RecentlyModifiedAsset> rma = result.stream().filter(a -> a.getUri().equals(k.getUri())).findAny();
            assertTrue(rma.isPresent());
            assertEquals(k.getUri(), rma.get().getUri());
            assertThat(rma.get().getTypes(), hasItem(k.getClass().getAnnotation(OWLClass.class).iri()));
        });
    }

    @Test
    void findRecentlyEditedByUserReturnsAssetsEditedBySpecifiedUser() {
        enableRdfsInference(em);
        final List<Resource> mineResources = IntStream.range(0, 5).mapToObj(i -> Generator.generateResourceWithId())
                                                      .collect(Collectors.toList());
        final List<PersistChangeRecord> persistRecords = mineResources.stream().map(Generator::generatePersistChange)
                                                                      .collect(Collectors.toList());
        final List<Resource> othersResources = IntStream.range(0, 5).mapToObj(i -> Generator.generateResourceWithId())
                                                        .collect(Collectors.toList());
        final User otherUser = Generator.generateUserWithId();
        transactional(() -> {
            mineResources.forEach(em::persist);
            othersResources.forEach(em::persist);
            em.persist(otherUser);
        });
        final List<PersistChangeRecord> otherPersistRecords = othersResources.stream().map(r -> {
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
        final Set<URI> mineUris = mineResources.stream().map(Resource::getUri).collect(Collectors.toSet());
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
        });

        final Page<RecentlyModifiedAsset> result = sut.findLastEdited(PageRequest.of(0, 1));
        assertFalse(result.isEmpty());
        assertEquals(term.getUri(), result.getContent().get(0).getUri());
        assertEquals(vocabulary.getUri(), result.getContent().get(0).getVocabulary());
    }
}
