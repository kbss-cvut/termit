package cz.cvut.kbss.termit.persistence.dao.acl;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.acl.RoleAccessControlRecord;
import cz.cvut.kbss.termit.model.acl.UserAccessControlRecord;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.context.StaticContexts;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

import static cz.cvut.kbss.termit.environment.util.ContainsSameEntities.containsSameEntities;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class AccessControlListDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private AccessControlListDao sut;

    @Test
    void findByIdRetrievesMatchingAccessControlListWrappedInOptional() {
        final AccessControlList acl = new AccessControlList();
        transactional(() -> em.persist(acl, descriptorFactory.accessControlListDescriptor()));
    }

    @Test
    void findForRetrievesAccessControlListForSpecifiedVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final AccessControlList acl = new AccessControlList();
        final RoleAccessControlRecord record = new RoleAccessControlRecord();
        final UserRole editorRole = new UserRole();
        editorRole.setUri(URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_plny_uzivatel_termitu));
        record.setHolder(editorRole);
        record.setAccessLevel(AccessLevel.WRITE);
        acl.addRecord(record);
        transactional(() -> {
            em.persist(editorRole);
            em.persist(acl, descriptorFactory.accessControlListDescriptor());
            vocabulary.setAcl(acl.getUri());
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
        });

        final Optional<AccessControlList> result = sut.findFor(vocabulary);
        assertTrue(result.isPresent());
        assertEquals(acl.getUri(), result.get().getUri());
        assertThat(result.get().getRecords(), containsSameEntities(acl.getRecords()));
    }

    @Test
    void findForReturnsEmptyOptionalWhenSpecifiedSubjectHasNoAcl() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));

        final Optional<AccessControlList> result = sut.findFor(vocabulary);
        assertFalse(result.isPresent());
    }

    @Test
    void persistPersistsAccessControlListWithRecordsIntoContext() {
        final AccessControlList acl = new AccessControlList();
        final UserAccessControlRecord record = new UserAccessControlRecord();
        final User user = Generator.generateUserAccount().toUser();
        record.setHolder(user);
        record.setAccessLevel(AccessLevel.WRITE);
        acl.addRecord(record);
        transactional(() -> em.persist(user));

        transactional(() -> sut.persist(acl));

        assertTrue(em.createNativeQuery("ASK WHERE {" +
                                                "GRAPH ?g {" +
                                                "?x a ?aclType ;" +
                                                "?hasRecord ?record ." +
                                                "?record ?hasLevel ?level . }}", Boolean.class)
                     .setParameter("g", URI.create(StaticContexts.ACCESS_CONTROL_LISTS))
                     .setParameter("aclType",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_seznam_rizeni_pristupu))
                     .setParameter("hasRecord",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_zaznam_rizeni_pristupu))
                     .setParameter("hasLevel",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_uroven_pristupovych_opravneni))
                     .getSingleResult());
    }

    @Test
    void updateSavesNewAccessControlRecords() {
        final AccessControlList acl = new AccessControlList();
        final UserAccessControlRecord record = new UserAccessControlRecord();
        final User user = Generator.generateUserWithId();
        record.setHolder(user);
        record.setAccessLevel(AccessLevel.WRITE);
        acl.addRecord(record);
        transactional(() -> {
            em.persist(user);
            em.persist(acl, descriptorFactory.accessControlListDescriptor());
        });
        final UserAccessControlRecord toAdd = new UserAccessControlRecord();
        final User anotherUser = Generator.generateUserWithId();
        transactional(() -> em.persist(anotherUser));
        toAdd.setHolder(anotherUser);
        toAdd.setAccessLevel(AccessLevel.READ);
        acl.addRecord(toAdd);
        transactional(() -> sut.update(acl));

        final AccessControlList result = em.find(AccessControlList.class, acl.getUri(),
                                                 descriptorFactory.accessControlListDescriptor());
        assertNotNull(result);
        assertThat(result.getRecords(), containsSameEntities(Arrays.asList(record, toAdd)));
    }

    @Test
    void updateRemovesOrphanAccessControlRecords() {
        final AccessControlList acl = new AccessControlList();
        final UserAccessControlRecord record = new UserAccessControlRecord();
        final User user = Generator.generateUserWithId();
        record.setHolder(user);
        record.setAccessLevel(AccessLevel.WRITE);
        acl.addRecord(record);
        transactional(() -> {
            em.persist(user);
            em.persist(acl, descriptorFactory.accessControlListDescriptor());
        });
        acl.getRecords().clear();

        transactional(() -> sut.update(acl));
        final AccessControlList result = em.find(AccessControlList.class, acl.getUri(),
                                                 descriptorFactory.accessControlListDescriptor());
        assertThat(result.getRecords(), anyOf(nullValue(), empty()));
        assertFalse(em.createNativeQuery("ASK WHERE { ?x a ?recordType . }", Boolean.class)
                      .setParameter("x", record.getUri())
                      .setParameter("recordType", URI.create(
                              cz.cvut.kbss.termit.util.Vocabulary.s_c_zaznam_rizeni_pristupu_uzivatele))
                      .getSingleResult());
    }

    @Test
    void getReferenceReturnsReferenceToMatchingAccessControlList() {
        final AccessControlList acl = new AccessControlList();
        transactional(() -> em.persist(acl, descriptorFactory.accessControlListDescriptor()));

        final Optional<AccessControlList> result = sut.getReference(acl.getUri());
        assertTrue(result.isPresent());
        assertEquals(acl.getUri(), result.get().getUri());
    }
}
