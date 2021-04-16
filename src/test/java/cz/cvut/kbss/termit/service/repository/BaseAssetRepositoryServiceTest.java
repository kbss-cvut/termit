/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.RecentlyCommentedAsset;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class BaseAssetRepositoryServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private BaseAssetRepositoryServiceImpl sut;

    private User author;

    @TestConfiguration
    public static class Config {

        @Bean
        public BaseAssetRepositoryServiceImpl baseRepositoryAssetService(VocabularyDao vocabularyDao,
                                                                         Validator validator,
                                                                         SecurityUtils securityUtils) {
            return new BaseAssetRepositoryServiceImpl(vocabularyDao, validator, securityUtils);
        }

        @Bean
        public LocalValidatorFactoryBean validatorFactoryBean() {
            return new LocalValidatorFactoryBean();
        }
    }

    @BeforeEach
    void setUp() {
        author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
    }

    @Test
    void findRecentlyEditedLoadsRecentlyEditedItems() {
        enableRdfsInference(em);
        final List<Vocabulary> vocabularies = IntStream.range(0, 5).mapToObj(i -> Generator.generateVocabularyWithId())
                .collect(Collectors.toList());
        transactional(() -> vocabularies.forEach(em::persist));
        final List<PersistChangeRecord> persistRecords = vocabularies.stream().map(Generator::generatePersistChange)
                .collect(Collectors.toList());
        setCreated(persistRecords);
        transactional(() -> persistRecords.forEach(em::persist));

        em.getEntityManagerFactory().getCache().evictAll();

        final int count = 2;
        final List<RecentlyModifiedAsset> result = sut.findLastEdited(count);
        persistRecords.sort(Comparator.comparing(AbstractChangeRecord::getTimestamp).reversed());
        assertEquals(count, result.size());
        assertEquals(persistRecords.subList(0, count).stream().map(AbstractChangeRecord::getChangedEntity)
                        .collect(Collectors.toList()),
                result.stream().map(RecentlyModifiedAsset::getUri).collect(Collectors.toList()));
    }

    private void setCreated(List<? extends AbstractChangeRecord> changeRecords) {
        for (int i = 0; i < changeRecords.size(); i++) {
            changeRecords.get(i).setTimestamp(Instant.ofEpochMilli(System.currentTimeMillis() - (long) i * 3600 * 1000));
        }
    }

    @Test
    void findRecentlyEditedPerUserLoadsRecentlyEditedItemsPerUser() {
        enableRdfsInference(em);
        final List<Vocabulary> vocabularies = IntStream.range(0, 5).mapToObj(i -> Generator.generateVocabularyWithId())
                .collect(Collectors.toList());
        transactional(() -> vocabularies.forEach(em::persist));
        final List<PersistChangeRecord> persistRecords = vocabularies.stream().map(Generator::generatePersistChange)
                .collect(Collectors.toList());
        setCreated(persistRecords);
        transactional(() -> persistRecords.forEach(em::persist));
        em.getEntityManagerFactory().getCache().evictAll();

        final int count = 2;
        final List<RecentlyModifiedAsset> result = sut.findLastEditedBy(persistRecords.get(0).getAuthor(), count);
        assertEquals(count, result.size());
        assertEquals(persistRecords.subList(0, count).stream().map(AbstractChangeRecord::getChangedEntity)
                        .collect(Collectors.toList()),
                result.stream().map(RecentlyModifiedAsset::getUri).collect(Collectors.toList()));
    }

    @Test
    void findLastCommentedLoadsLastCommentedItems() {
        enableRdfsInference(em);
        final List<Term> terms = IntStream.range(0, 5).mapToObj(i -> Generator.generateTermWithId())
            .collect(Collectors.toList());
        AtomicInteger i = new AtomicInteger(0);
        terms.forEach( t -> t.setLabel(MultilingualString.create("Term " + i.incrementAndGet(),"cs")));
        transactional(() -> terms.forEach(em::persist));

        final List<Comment> comments = terms.stream().map(t -> Generator.generateComment(author,t)).collect(
            Collectors.toList());
        transactional(() -> comments.forEach(em::persist));

        em.getEntityManagerFactory().getCache().evictAll();

        final int count = 2;
        final List<RecentlyCommentedAsset> result = sut.findLastCommented(count);
        assertEquals(count, result.size());
    }

    @Test
    void findMyLastCommentedLoadsLastCommentedItems() {
        enableRdfsInference(em);
        final List<Term> terms = IntStream.range(0, 5).mapToObj(i -> Generator.generateTermWithId())
            .collect(Collectors.toList());
        AtomicInteger i = new AtomicInteger(0);
        terms.forEach( t -> t.setLabel(MultilingualString.create("Term " + i.incrementAndGet(),"cs")));
        transactional(() -> terms.forEach(em::persist));

        final List<PersistChangeRecord> persistRecords = terms.stream().map(Generator::generatePersistChange)
            .collect(
                Collectors.toList());
        setCreated(persistRecords);
        transactional(() -> persistRecords.forEach(em::persist));

        final List<Comment> comments = terms.stream().map(t -> Generator.generateComment(author,t)).collect(
            Collectors.toList());
        transactional(() -> comments.forEach(em::persist));

        em.getEntityManagerFactory().getCache().evictAll();

        final int count = 2;
        final List<RecentlyCommentedAsset> result = sut.findMyLastCommented(author, count);
        assertEquals(count, result.size());
    }

    @Test
    void findLastCommentedInReactionLoadsLastCommentedItems() {
        enableRdfsInference(em);
        final List<Term> terms = IntStream.range(0, 5).mapToObj(i -> Generator.generateTermWithId())
            .collect(Collectors.toList());
        AtomicInteger i = new AtomicInteger(0);
        terms.forEach( t -> t.setLabel(MultilingualString.create("Term " + i.incrementAndGet(),"cs")));
        transactional(() -> terms.forEach(em::persist));

        final List<PersistChangeRecord> persistRecords = terms.stream().map(Generator::generatePersistChange)
            .collect(
                Collectors.toList());
        setCreated(persistRecords);
        transactional(() -> persistRecords.forEach(em::persist));

        final List<Comment> comments = terms.stream().map(t -> Generator.generateComment(author,t)).collect(
            Collectors.toList());
        transactional(() -> comments.forEach(em::persist));

        User anotherUser = Generator.generateUserWithId();
        transactional(() -> em.persist(anotherUser));

        final List<Comment> otherComments = terms.stream().map(t -> Generator.generateComment(anotherUser,t)).collect(
            Collectors.toList());
        transactional(() -> otherComments.forEach(em::persist));

        em.getEntityManagerFactory().getCache().evictAll();

        final int count = 2;
        final List<RecentlyCommentedAsset> result = sut.findLastCommentedInReaction(author, count);
        assertEquals(count, result.size());
        result.stream().forEach(a ->
            assertNotNull(a.getMyLastComment())
        );
    }

    @Test
    void persistThrowsValidationExceptionWhenIdentifierDoesNotMatchValidationPattern() {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create("http://example.org/test-vocabulary?test=0&test1=2"));
        assertThrows(ValidationException.class, () -> sut.persist(vocabulary));
    }
}
