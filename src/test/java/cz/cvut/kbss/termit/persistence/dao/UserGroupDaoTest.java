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
                     .setParameter("type", URI.create(Vocabulary.s_c_Usergroup)).getSingleResult());
        assertTrue(sut.find(group.getUri()).isPresent());
    }
}
