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
package cz.cvut.kbss.termit.persistence.dao.acl;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.*;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.context.StaticContexts;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
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
        final UserRole editorRole = new UserRole(URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_plny_uzivatel_termitu));
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

    @Test
    void resolveSubjectOfReturnsIdentifierOfVocabularyWhoseAccessControlListIsPassedAsArgument() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final AccessControlList acl = new AccessControlList();
        transactional(() -> {
            em.persist(acl, descriptorFactory.accessControlListDescriptor());
            vocabulary.setAcl(acl.getUri());
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
        });

        final Optional<URI> result = sut.resolveSubjectOf(acl);
        assertTrue(result.isPresent());
        assertEquals(vocabulary.getUri(), result.get());
    }

    @Test
    void resolveSubjectOfReturnsEmptyOptionalWhenNoMatchingSubjectIsFound() {
        final AccessControlList acl = Generator.generateAccessControlList(false);
        final Optional<URI> result = sut.resolveSubjectOf(acl);
        assertFalse(result.isPresent());
    }

    @Test
    void removeRemovesAccessControlListAndItsRecords() {
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

        transactional(() -> sut.remove(acl));
        assertNull(em.find(AccessControlList.class, acl.getUri()));
        acl.getRecords().forEach(r -> assertNull(em.find(AccessControlRecord.class, r.getUri())));
    }

    @Test
    void findAssetsByAgentWithSecurityAccessReturnsResourcesWhenUserHasSecurityAccess() {
        final User user = Generator.generateUserWithId();
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Vocabulary otherVocabulary = Generator.generateVocabularyWithId();
        final AccessControlList acl = new AccessControlList();
        final UserAccessControlRecord record = new UserAccessControlRecord(AccessLevel.SECURITY, user);
        acl.addRecord(record);
        transactional(() -> {
            em.persist(user);
            em.persist(acl, descriptorFactory.accessControlListDescriptor());
            vocabulary.setAcl(acl.getUri());
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(otherVocabulary, descriptorFactory.vocabularyDescriptor(otherVocabulary));
        });

        final List<? extends Asset<?>> result = sut.findAssetsByAgentWithSecurityAccess(user);
        assertThat(result, containsSameEntities(List.of(vocabulary)));
    }
}
