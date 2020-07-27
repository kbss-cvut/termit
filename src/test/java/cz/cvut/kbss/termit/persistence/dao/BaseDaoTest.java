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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.exceptions.OWLPersistenceException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dao")
class BaseDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    private BaseDao<User> sut;

    @BeforeEach
    void setUp() {
        this.sut = new BaseDaoImpl(em);
    }

    @Test
    void findAllRetrievesAllExistingInstances() {
        final List<User> users =
                IntStream.range(0, 5).mapToObj(i -> {
                    final User u = Generator.generateUser();
                    u.setUri(Generator.generateUri());
                    return u;
                }).collect(Collectors.toList());
        transactional(() -> sut.persist(users));
        final List<User> result = sut.findAll();
        assertEquals(users.size(), result.size());
        assertTrue(users.containsAll(result));
    }

    @Test
    void existsReturnsTrueForExistingEntity() {
        final User user = Generator.generateUser();
        user.setUri(Generator.generateUri());
        transactional(() -> sut.persist(user));
        assertTrue(sut.exists(user.getUri()));
    }

    @Test
    void existsReturnsFalseForNonexistentEntity() {
        assertFalse(sut.exists(Generator.generateUri()));
    }

    @Test
    void findReturnsNonEmptyOptionalForExistingEntity() {
        final User user = Generator.generateUserWithId();
        transactional(() -> sut.persist(user));
        final Optional<User> result = sut.find(user.getUri());
        assertTrue(result.isPresent());
        assertEquals(user, result.get());
    }

    @Test
    void findReturnsEmptyOptionalForUnknownIdentifier() {
        final Optional<User> result = sut.find(Generator.generateUri());
        assertNotNull(result);
        assertFalse(result.isPresent());
    }

    @Test
    void updateReturnsManagedInstance() {
        final User user = Generator.generateUserWithId();
        transactional(() -> sut.persist(user));
        final String lastNameUpdate = "updatedLastName";
        user.setLastName(lastNameUpdate);
        transactional(() -> {
            final User updated = sut.update(user);
            assertTrue(em.contains(updated));
            assertEquals(lastNameUpdate, updated.getLastName());
        });
        assertEquals(lastNameUpdate, em.find(User.class, user.getUri()).getLastName());
    }

    @Test
    void removeRemovesEntity() {
        final User user = Generator.generateUserWithId();
        transactional(() -> sut.persist(user));
        transactional(() -> sut.remove(user));
        assertFalse(sut.exists(user.getUri()));
    }

    @Test
    void removeHandlesNonexistentEntity() {
        final User user = Generator.generateUserWithId();
        transactional(() -> sut.remove(user));
        assertFalse(sut.exists(user.getUri()));
    }

    @Test
    void removeByIdRemovesEntityWithSpecifiedIdentifier() {
        final User user = Generator.generateUserWithId();
        transactional(() -> sut.persist(user));
        transactional(() -> sut.remove(user.getUri()));
        assertFalse(sut.find(user.getUri()).isPresent());
    }

    @Test
    void exceptionDuringPersistIsWrappedInPersistenceException() {
        final PersistenceException e = assertThrows(PersistenceException.class, () -> {
            final User user = Generator.generateUser();
            transactional(() -> sut.persist(user));
        });
        assertThat(e.getCause(), is(instanceOf(OWLPersistenceException.class)));
    }

    @Test
    void exceptionDuringCollectionPersistIsWrappedInPersistenceException() {
        final List<User> users = Collections.singletonList(Generator.generateUserWithId());
        transactional(() -> sut.persist(users));

        final PersistenceException e = assertThrows(PersistenceException.class,
                () -> transactional(() -> sut.persist(users)));
        assertThat(e.getCause(), is(instanceOf(OWLPersistenceException.class)));
    }

    @Test
    void exceptionDuringUpdateIsWrappedInPersistenceException() {
        final User user = Generator.generateUserWithId();
        transactional(() -> sut.persist(user));
        user.setUri(null);
        final PersistenceException e = assertThrows(PersistenceException.class,
                () -> transactional(() -> sut.update(user)));
        assertThat(e.getCause(), is(instanceOf(OWLPersistenceException.class)));
    }

    @Test
    void getReferenceRetrievesReferenceToMatchingInstance() {
        final User user = Generator.generateUserWithId();
        transactional(() -> sut.persist(user));
        final Optional<User> result = sut.getReference(user.getUri());
        assertTrue(result.isPresent());
        assertEquals(user.getUri(), result.get().getUri());
    }

    @Test
    void getReferenceReturnsEmptyOptionalWhenNoMatchingInstanceExists() {
        final Optional<User> result = sut.getReference(Generator.generateUri());
        assertNotNull(result);
        assertFalse(result.isPresent());
    }

    private static class BaseDaoImpl extends BaseDao<User> {

        BaseDaoImpl(EntityManager em) {
            super(User.class, em);
        }
    }
}