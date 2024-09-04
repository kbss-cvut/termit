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
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private SnapshotDao sut;

    @Test
    void findRetrievesVocabularySnapshot() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
        final Instant timestamp = Utils.timestamp();
        final String suffix = "/test-snapshot";
        transactional(() -> em.createNativeQuery(Utils.loadQuery("snapshot/vocabulary.ru"))
                              .setParameter("vocabulary", vocabulary)
                              .setParameter("suffix", suffix)
                              .setParameter("created", timestamp)
                              .executeUpdate());

        final Optional<Snapshot> result = sut.find(URI.create(vocabulary.getUri().toString() + suffix));
        assertTrue(result.isPresent());
        assertEquals(vocabulary.getUri(), result.get().getVersionOf());
        assertThat(result.get().getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku));
    }

    @Test
    void findRetrievesTermSnapshot() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        vocabulary.getGlossary().addRootTerm(term);
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
        });
        final Instant timestamp = Utils.timestamp();
        final String suffix = "/test-snapshot";
        transactional(() -> em.createNativeQuery(Utils.loadQuery("snapshot/term.ru"))
                              .setParameter("vocabulary", vocabulary)
                              .setParameter("suffix", suffix)
                              .setParameter("created", timestamp)
                              .executeUpdate());

        final Optional<Snapshot> result = sut.find(URI.create(term.getUri().toString() + suffix));
        assertTrue(result.isPresent());
        assertEquals(term.getUri(), result.get().getVersionOf());
        assertThat(result.get().getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu));
    }

    @Test
    void findReturnsEmptyOptionalWhenNoMatchingSnapshotExists() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
        final Optional<Snapshot> result = sut.find(vocabulary.getUri());
        assertFalse(result.isPresent());
    }
}
