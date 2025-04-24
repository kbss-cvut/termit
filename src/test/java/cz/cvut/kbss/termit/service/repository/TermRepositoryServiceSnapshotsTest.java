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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.SnapshotNotEditableException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TermRepositoryServiceSnapshotsTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private TermRepositoryService sut;

    private UserAccount user;
    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));
        Environment.setCurrentUser(user);

        this.vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
    }

    @Test
    void updateOfSnapshotThrowsSnapshotNotEditableException() {
        final Term snapshot = generateAndPersistSnapshot();

        snapshot.getLabel().set("de", "Apfelbaum, der");
        assertThrows(SnapshotNotEditableException.class, () -> sut.update(snapshot));
    }

    private Term generateAndPersistSnapshot() {
        final Term t = Generator.generateTermWithId();
        t.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu);
        vocabulary.getGlossary().addRootTerm(t);
        t.setVocabulary(vocabulary.getUri());
        transactional(() -> {
            em.persist(t, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
        });
        return t;
    }

    @Test
    void setStateOnSnapshotThrowsSnapshotNotEditableException() {
        final Term snapshot = generateAndPersistSnapshot();

        assertThrows(SnapshotNotEditableException.class, () -> sut.setState(snapshot, Generator.TERM_STATES[0]));
    }

    @Test
    void addChildTermToSnapshotThrowsSnapshotNotEditableException() {
        final Term snapshot = generateAndPersistSnapshot();
        final Term child = Generator.generateTermWithId(vocabulary.getUri());

        assertThrows(SnapshotNotEditableException.class, () -> sut.addChildTerm(child, snapshot));
    }
}
