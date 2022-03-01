/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.occurrence.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.occurrence.TermDefinitionSource;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TermOccurrenceServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private TermOccurrenceService sut;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserWithId();
        transactional(() -> em.persist(user));
        Environment.setCurrentUser(user);
    }

    @Test
    void persistOccurrenceSavesSpecifiedOccurrenceIntoRepository() {
        final Term term = Generator.generateTermWithId();
        final File resource = Generator.generateFileWithId("test.html");
        transactional(() -> {
            em.persist(term);
            em.persist(resource);
        });
        final TermDefinitionSource definitionSource = new TermDefinitionSource(term.getUri(),
                new FileOccurrenceTarget(resource));
        definitionSource.getTarget().setSelectors(Collections.singleton(new TextQuoteSelector("test")));

        transactional(() -> sut.persistOccurrence(definitionSource));
        final TermDefinitionSource result = em.find(TermDefinitionSource.class, definitionSource.getUri());
        assertNotNull(result);
        assertEquals(term.getUri(), result.getTerm());
        assertEquals(resource.getUri(), result.getTarget().getSource());
    }
}
