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

import cz.cvut.kbss.jopa.exceptions.OWLPersistenceException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Term;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private BaseDao<Term> sut;

    @BeforeEach
    void setUp() {
        this.sut = new BaseDaoImpl(em);
        sut.setApplicationEventPublisher(eventPublisher);
    }

    @Test
    void findAllRetrievesAllExistingInstances() {
        final List<Term> terms =
                IntStream.range(0, 5).mapToObj(i -> {
                    final Term u = Generator.generateTerm();
                    u.setUri(Generator.generateUri());
                    return u;
                }).collect(Collectors.toList());
        transactional(() -> sut.persist(terms));
        final List<Term> result = sut.findAll();
        assertThat(result, hasItems(terms.toArray(new Term[]{})));
    }

    @Test
    void existsReturnsTrueForExistingEntity() {
        final Term term = Generator.generateTerm();
        term.setUri(Generator.generateUri());
        transactional(() -> sut.persist(term));
        assertTrue(sut.exists(term.getUri()));
    }

    @Test
    void existsReturnsFalseForNonexistentEntity() {
        assertFalse(sut.exists(Generator.generateUri()));
    }

    @Test
    void findReturnsNonEmptyOptionalForExistingEntity() {
        final Term term = Generator.generateTermWithId();
        transactional(() -> sut.persist(term));
        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(term, result.get());
    }

    @Test
    void findReturnsEmptyOptionalForUnknownIdentifier() {
        final Optional<Term> result = sut.find(Generator.generateUri());
        assertNotNull(result);
        assertFalse(result.isPresent());
    }

    @Test
    void updateReturnsManagedInstance() {
        final Term term = Generator.generateTermWithId();
        transactional(() -> sut.persist(term));
        final String lastNameUpdate = "updatedLastName";
        term.getLabel().set(Environment.LANGUAGE, lastNameUpdate);
        transactional(() -> {
            final Term updated = sut.update(term);
            assertTrue(em.contains(updated));
            assertEquals(lastNameUpdate, updated.getLabel().get(Environment.LANGUAGE));
        });
        assertEquals(lastNameUpdate, em.find(Term.class, term.getUri()).getLabel().get(Environment.LANGUAGE));
    }

    @Test
    void removeRemovesEntity() {
        final Term term = Generator.generateTermWithId();
        transactional(() -> sut.persist(term));
        transactional(() -> sut.remove(term));
        assertFalse(sut.exists(term.getUri()));
    }

    @Test
    void removeHandlesNonexistentEntity() {
        final Term term = Generator.generateTermWithId();
        transactional(() -> sut.remove(term));
        assertFalse(sut.exists(term.getUri()));
    }

    @Test
    void removeByIdRemovesEntityWithSpecifiedIdentifier() {
        final Term term = Generator.generateTermWithId();
        transactional(() -> sut.persist(term));
        transactional(() -> sut.remove(term));
        assertFalse(sut.find(term.getUri()).isPresent());
    }

    @Test
    void removeRemovesEntityWithNonMergeableFields() {
        final Term term = Generator.generateTermWithId();
        transactional(() -> sut.persist(term));
        transactional(() -> sut.remove(term));
        assertFalse(sut.exists(term.getUri()));
    }

    @Test
    void exceptionDuringPersistIsWrappedInPersistenceException() {
        final PersistenceException e = assertThrows(PersistenceException.class, () -> {
            final Term term = Generator.generateTerm();
            transactional(() -> sut.persist(term));
        });
        assertThat(e.getCause(), is(instanceOf(OWLPersistenceException.class)));
    }

    @Test
    void exceptionDuringCollectionPersistIsWrappedInPersistenceException() {
        final List<Term> terms = Collections.singletonList(Generator.generateTermWithId());
        transactional(() -> sut.persist(terms));

        final PersistenceException e = assertThrows(PersistenceException.class,
                                                    () -> transactional(() -> sut.persist(terms)));
        assertThat(e.getCause(), is(instanceOf(OWLPersistenceException.class)));
    }

    @Test
    void getReferenceRetrievesReferenceToMatchingInstance() {
        final Term term = Generator.generateTermWithId();
        transactional(() -> sut.persist(term));
        readOnlyTransactional(() -> {
            final Term result = sut.getReference(term.getUri());
            assertNotNull(result);
            // This will trigger state loading
            assertEquals(term, result);
        });
    }

    @Test
    void detachDetachesInstanceFromPersistenceContext() {
        final Term term = Generator.generateTermWithId();
        transactional(() -> sut.persist(term));
        transactional(() -> {
            final Optional<Term> instance = sut.find(term.getUri());
            assertTrue(instance.isPresent());
            assertTrue(em.contains(instance.get()));
            sut.detach(instance.get());
            assertFalse(em.contains(instance.get()));
        });
    }

    @Test
    void detachDoesNothingWhenEntityIsNotManaged() {
        final Term term = Generator.generateTermWithId();
        assertDoesNotThrow(() -> sut.detach(term));
    }

    private static class BaseDaoImpl extends BaseDao<Term> {

        BaseDaoImpl(EntityManager em) {
            super(Term.class, em);
        }
    }
}
