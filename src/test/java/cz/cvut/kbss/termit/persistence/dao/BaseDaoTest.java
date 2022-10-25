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
import static org.hamcrest.Matchers.*;
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
        final List<User> instances =
                IntStream.range(0, 5).mapToObj(i -> Generator.generateUserWithId()).collect(Collectors.toList());
        transactional(() -> sut.persist(instances));
        final List<User> result = sut.findAll();
        assertThat(result, hasItems(instances.toArray(new User[]{})));
    }

    @Test
    void existsReturnsTrueForExistingEntity() {
        final User instance = Generator.generateUserWithId();
        transactional(() -> sut.persist(instance));
        assertTrue(sut.exists(instance.getUri()));
    }

    @Test
    void existsReturnsFalseForNonexistentEntity() {
        assertFalse(sut.exists(Generator.generateUri()));
    }

    @Test
    void findReturnsNonEmptyOptionalForExistingEntity() {
        final User instance = Generator.generateUserWithId();
        transactional(() -> sut.persist(instance));
        final Optional<User> result = sut.find(instance.getUri());
        assertTrue(result.isPresent());
        assertEquals(instance, result.get());
    }

    @Test
    void findReturnsEmptyOptionalForUnknownIdentifier() {
        final Optional<User> result = sut.find(Generator.generateUri());
        assertNotNull(result);
        assertFalse(result.isPresent());
    }

    @Test
    void updateReturnsManagedInstance() {
        final User instance = Generator.generateUserWithId();
        transactional(() -> sut.persist(instance));
        final String lastNameUpdate = "updatedLastName";
        instance.setLastName(lastNameUpdate);
        transactional(() -> {
            final User updated = sut.update(instance);
            assertTrue(em.contains(updated));
            assertEquals(lastNameUpdate, updated.getLastName());
        });
        assertEquals(lastNameUpdate, em.find(User.class, instance.getUri()).getLastName());
    }

    @Test
    void removeRemovesEntity() {
        final User instance = Generator.generateUserWithId();
        transactional(() -> sut.persist(instance));
        transactional(() -> sut.remove(instance));
        assertFalse(sut.exists(instance.getUri()));
    }

    @Test
    void removeHandlesNonexistentEntity() {
        final User instance = Generator.generateUserWithId();
        transactional(() -> sut.remove(instance));
        assertFalse(sut.exists(instance.getUri()));
    }

    @Test
    void removeByIdRemovesEntityWithSpecifiedIdentifier() {
        final User instance = Generator.generateUserWithId();
        transactional(() -> sut.persist(instance));
        transactional(() -> sut.remove(instance));
        assertFalse(sut.find(instance.getUri()).isPresent());
    }

    @Test
    void exceptionDuringPersistIsWrappedInPersistenceException() {
        final PersistenceException e = assertThrows(PersistenceException.class, () -> {
            final User instance = Generator.generateUser();
            instance.setFirstName(null);
            transactional(() -> sut.persist(instance));
        });
        assertThat(e.getCause(), is(instanceOf(OWLPersistenceException.class)));
    }

    @Test
    void exceptionDuringCollectionPersistIsWrappedInPersistenceException() {
        final List<User> terms = Collections.singletonList(Generator.generateUserWithId());
        transactional(() -> sut.persist(terms));

        final PersistenceException e = assertThrows(PersistenceException.class,
                                                    () -> transactional(() -> sut.persist(terms)));
        assertThat(e.getCause(), is(instanceOf(OWLPersistenceException.class)));
    }

    @Test
    void exceptionDuringUpdateIsWrappedInPersistenceException() {
        final User instance = Generator.generateUserWithId();
        transactional(() -> em.persist(instance));
        instance.setUri(null);
        final PersistenceException e = assertThrows(PersistenceException.class,
                                                    () -> transactional(() -> sut.update(instance)));
        assertThat(e.getCause(), is(instanceOf(OWLPersistenceException.class)));
    }

    @Test
    void getReferenceRetrievesReferenceToMatchingInstance() {
        final User instance = Generator.generateUserWithId();
        transactional(() -> sut.persist(instance));
        final Optional<User> result = sut.getReference(instance.getUri());
        assertTrue(result.isPresent());
        assertEquals(instance.getUri(), result.get().getUri());
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
