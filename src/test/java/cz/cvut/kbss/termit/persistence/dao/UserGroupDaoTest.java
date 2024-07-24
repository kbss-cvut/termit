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
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.persistence.context.StaticContexts;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserGroupDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private UserGroupDao sut;

    @Test
    void persistGeneratesIdentifier() {
        final UserGroup group = Generator.generateUserGroup();
        group.setUri(null);
        transactional(() -> sut.persist(group));
        assertNotNull(group.getUri());
        assertNotNull(em.find(UserGroup.class, group.getUri()));
    }

    @Test
    void persistSavesEntitiesInCorrectContext() {
        final UserGroup group = Generator.generateUserGroup();
        group.setUri(null);
        transactional(() -> sut.persist(group));
        assertTrue(em.createNativeQuery("ASK WHERE { GRAPH ?ctx { ?x a ?type . } }", Boolean.class)
                     .setParameter("ctx", URI.create(StaticContexts.USER_GROUPS))
                     .setParameter("x", group)
                     .setParameter("type", URI.create(Vocabulary.s_c_sioc_Usergroup)).getSingleResult());
        assertTrue(sut.find(group.getUri()).isPresent());
    }
}
