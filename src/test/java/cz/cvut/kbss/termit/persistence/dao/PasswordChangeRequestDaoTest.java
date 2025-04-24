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

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordChangeRequestDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private PasswordChangeRequestDao sut;

    private String randomToken() {
        return UUID.randomUUID().toString();
    }

    @Test
    void findAllByUsernameReturnsAllResults() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));

        final String TOKEN = randomToken();
        final String ANOTHER_TOKEN = randomToken();
        final PasswordChangeRequest passwordChangeRequest = new PasswordChangeRequest();
        final PasswordChangeRequest secondPasswordChangeRequest = new PasswordChangeRequest();
        passwordChangeRequest.setToken(TOKEN);
        secondPasswordChangeRequest.setToken(ANOTHER_TOKEN);
        passwordChangeRequest.setUserAccount(user);
        secondPasswordChangeRequest.setUserAccount(user);
        passwordChangeRequest.setCreatedAt(Utils.timestamp());
        secondPasswordChangeRequest.setCreatedAt(Utils.timestamp());
        transactional(() -> em.persist(passwordChangeRequest));
        transactional(() -> em.persist(secondPasswordChangeRequest));

        final List<PasswordChangeRequest> result = sut.findAllByUserAccount(user);
        assertTrue(result.stream().anyMatch(r -> passwordChangeRequest.getUri().equals(r.getUri())));
        assertTrue(result.stream().anyMatch(r -> secondPasswordChangeRequest.getUri().equals(r.getUri())));
        assertEquals(2, result.size());
    }

}
