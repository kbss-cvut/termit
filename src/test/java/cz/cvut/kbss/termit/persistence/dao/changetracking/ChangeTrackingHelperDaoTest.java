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
package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeTrackingHelperDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private ChangeTrackingHelperDao sut;

    private User author;

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
    }

    @Test
    void findStoredRetrievesRepositoryInstanceOfSpecifiedAsset() {
        final Vocabulary voc = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(voc, descriptorFactory.vocabularyDescriptor(voc)));

        final Vocabulary result = sut.findStored(voc);
        assertNotNull(result);
        assertEquals(voc.getUri(), result.getUri());
    }

    @Test
    void findStoredThrowsNotFoundExceptionWhenStoredInstanceIsNotFound() {
        final Vocabulary voc = Generator.generateVocabularyWithId();
        assertThrows(NotFoundException.class, () -> sut.findStored(voc));
    }

    @Test
    void findStoredDetachesRetrievedInstanceFromPersistenceContext() {
        final Vocabulary voc = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(voc, descriptorFactory.vocabularyDescriptor(voc)));

        transactional(() -> {
            final Vocabulary result = sut.findStored(voc);
            assertNotNull(result);
            assertFalse(em.contains(result));
        });
    }
}
