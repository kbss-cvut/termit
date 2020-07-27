/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import javax.validation.Validator;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseAssetRepositoryServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private BaseAssetRepositoryServiceImpl sut;

    @Configuration
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
        final User author = Generator.generateUserWithId();
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
                                                                     .collect(
                                                                             Collectors.toList());
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
            changeRecords.get(i).setTimestamp(Instant.ofEpochMilli(System.currentTimeMillis() - i * 1000 * 60));
        }
    }

    @Test
    void findRecentlyEditedPerUserLoadsRecentlyEditedItemsPerUser() {
        enableRdfsInference(em);
        final List<Vocabulary> vocabularies = IntStream.range(0, 5).mapToObj(i -> Generator.generateVocabularyWithId())
                                                       .collect(Collectors.toList());
        transactional(() -> vocabularies.forEach(em::persist));
        final List<PersistChangeRecord> persistRecords = vocabularies.stream().map(Generator::generatePersistChange)
                                                                     .collect(
                                                                             Collectors.toList());
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
    void persistThrowsValidationExceptionWhenIdentifierDoesNotMatchValidationPattern() {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create("http://example.org/test-vocabulary?test=0&test1=2"));
        assertThrows(ValidationException.class, () -> sut.persist(vocabulary));
    }
}
