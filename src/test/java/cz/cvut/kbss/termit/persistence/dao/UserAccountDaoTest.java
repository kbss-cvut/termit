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

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dao")
class UserAccountDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private UserAccountDao sut;

    @Test
    void findByUsernameReturnsMatchingUser() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));

        final Optional<UserAccount> result = sut.findByUsername(user.getUsername());
        assertTrue(result.isPresent());
        assertEquals(user, result.get());
    }

    @Test
    void findByUsernameReturnsEmptyOptionalWhenNoMatchingUserIsFound() {
        final Optional<UserAccount> result = sut.findByUsername("unknown@kbss.felk.cvut.cz");
        assertNotNull(result);
        assertFalse(result.isPresent());
    }

    @Test
    void existsByUsernameReturnsTrueForExistingUsername() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));

        assertTrue(sut.exists(user.getUsername()));
    }

    @Test
    void existsByUsernameReturnsFalseForUnknownUsername() {
        assertFalse(sut.exists("unknownUsername"));
    }

    @Test
    void findAllReturnsAccountsSortedByUserLastNameAndFirstName() {
        final List<UserAccount> accounts = IntStream.range(0, 10)
                                                    .mapToObj(i -> Generator.generateUserAccountWithPassword()).collect(
                        Collectors.toList());
        transactional(() -> accounts.forEach(em::persist));

        final List<UserAccount> result = sut.findAll();
        accounts.sort(Comparator.comparing(UserAccount::getLastName).thenComparing(UserAccount::getFirstName));
        assertEquals(result, accounts);
    }

    @Test
    void doesAdminExistReturnsTrueWhenAdminAccountExists() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        user.addType(Vocabulary.s_c_administrator_termitu);
        transactional(() -> em.persist(user));

        assertTrue(sut.doesAdminExist());
    }

    @Test
    void doesAdminExistReturnsFalseWhenNoAdminAccountExists() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));

        assertFalse(sut.doesAdminExist());
    }
}
