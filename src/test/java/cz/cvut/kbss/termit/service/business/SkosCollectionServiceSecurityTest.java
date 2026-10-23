package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.acl.RoleAccessControlRecord;
import cz.cvut.kbss.termit.model.acl.UserAccessControlRecord;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnableMethodSecurity
class SkosCollectionServiceSecurityTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private VocabularyService vocabularyService;

    @Autowired
    private AccessControlListService aclService;

    @Autowired
    private SkosCollectionService sut;

    private Vocabulary vocabulary;

    private Descriptor vocabularyDescriptor;

    private TermCollection unorderedCollection;

    private OrderedTermCollection orderedCollection;

    private SkosCollectionDto collectionDto;

    // restricted user
    private User author;

    private User admin;

    @BeforeEach
    void setUp() {
        enableRdfsInference(em);

        author = Generator.generateUserWithId();
        author.setTypes(new HashSet<>());
        author.addType(UserRole.RESTRICTED_USER.getType());
        transactional(() -> em.persist(author));

        // temporary admin user to create the setup
        admin = Generator.generateUserWithId();
        admin.addType(UserRole.ADMIN.getType());
        transactional(() -> em.persist(admin));
        Environment.setCurrentUser(admin);

        vocabulary = Generator.generateVocabularyWithId();
        vocabularyService.persist(vocabulary);
        revokeDefaultRestrictedUserAccess();
        vocabularyDescriptor = descriptorFactory.vocabularyDescriptor(vocabulary);

        final List<Term> unorderedMembers = Generator.generateTermsWithIds(3);
        unorderedCollection = Generator.generateTermCollection(vocabulary, unorderedMembers);

        final List<Term> orderedMembers = Generator.generateTermsWithIds(3);
        orderedCollection = Generator.generateOrderedTermCollection(vocabulary, orderedMembers);

        transactional(() -> Stream.of(unorderedMembers, orderedMembers, Set.of(unorderedCollection, orderedCollection))
                .flatMap(Collection::stream)
                .forEach(e -> em.persist(e, vocabularyDescriptor)));

        collectionDto = Environment.getDtoMapper().skosCollectionToDto(unorderedCollection);
        Environment.setCurrentUser(author);
    }

    /**
     * Restricted users have read access to new vocabularies by default (via role-based ACL record), revoke it so that
     * only explicitly granted users can read the vocabulary.
     */
    private void revokeDefaultRestrictedUserAccess() {
        final AccessControlRecord<?> readerRecord = aclService.findFor(vocabulary).orElseThrow().getRecords().stream()
                .filter(RoleAccessControlRecord.class::isInstance)
                .filter(r -> r.getHolder().getUri().toString().equals(UserRole.RESTRICTED_USER.getType()))
                .findAny()
                .orElseThrow();
        readerRecord.setAccessLevel(AccessLevel.NONE);
        vocabularyService.updateAccessControlLevel(vocabulary, readerRecord);
    }

    private void grantAccess(AccessLevel accessLevel) {
        Environment.setCurrentUser(admin);
        vocabularyService.addAccessControlRecords(vocabulary, new UserAccessControlRecord(accessLevel, author));
        Environment.setCurrentUser(author);
    }

    @Test
    void findAllSucceedsForUserWithVocabularyReadAccess() {
        grantAccess(AccessLevel.READ);
        assertDoesNotThrow(() -> sut.findAll(vocabulary.getUri(), Pageable.ofSize(10)));
    }

    @Test
    void findAllThrowsAccessDeniedForUserWithoutVocabularyReadAccess() {
        assertThrows(AccessDeniedException.class, () -> sut.findAll(vocabulary.getUri(), Pageable.ofSize(10)));
    }

    @Test
    void findRequiredDtoSucceedsForUserWithVocabularyReadAccess() {
        grantAccess(AccessLevel.READ);
        assertDoesNotThrow(() -> sut.findRequiredDto(unorderedCollection.getUri(), vocabulary.getUri()));
    }

    @Test
    void findRequiredDtoThrowsAccessDeniedForUserWithoutVocabularyReadAccess() {
        assertThrows(
                AccessDeniedException.class,
                () -> sut.findRequiredDto(unorderedCollection.getUri(), vocabulary.getUri()));
    }

    @Test
    void findMembersFromOrderedSucceedsForUserWithVocabularyReadAccess() {
        grantAccess(AccessLevel.READ);
        assertDoesNotThrow(
                () -> sut.findMembersFromOrdered(orderedCollection.getUri(), vocabulary.getUri(), Pageable.ofSize(10)));
    }

    @Test
    void findMembersFromOrderedThrowsAccessDeniedForUserWithoutVocabularyReadAccess() {
        assertThrows(
                AccessDeniedException.class,
                () -> sut.findMembersFromOrdered(orderedCollection.getUri(), vocabulary.getUri(), Pageable.ofSize(10)));
    }

    @Test
    void findMembersFromUnorderedSucceedsForUserWithVocabularyReadAccess() {
        grantAccess(AccessLevel.READ);
        assertDoesNotThrow(() ->
                sut.findMembersFromUnordered(unorderedCollection.getUri(), vocabulary.getUri(), Pageable.ofSize(10)));
    }

    @Test
    void findMembersFromUnorderedThrowsAccessDeniedForUserWithoutVocabularyReadAccess() {
        assertThrows(
                AccessDeniedException.class,
                () -> sut.findMembersFromUnordered(
                        unorderedCollection.getUri(), vocabulary.getUri(), Pageable.ofSize(10)));
    }

    private TermCollection generatePersistedCollection() {
        final List<Term> terms = Generator.generateTermsWithIds(3);
        final TermCollection collection = Generator.generateTermCollection(vocabulary, terms);
        transactional(() -> terms.forEach(t -> em.persist(t, vocabularyDescriptor)));
        return collection;
    }

    @Test
    void persistThrowsForUserWithoutWriteAccess() {
        grantAccess(AccessLevel.READ);
        assertThrows(AccessDeniedException.class, () -> sut.persist(generatePersistedCollection()));
    }

    @Test
    void persistDoesNotThrowForUserWithWriteAccess() {
        grantAccess(AccessLevel.WRITE);
        assertDoesNotThrow(() -> sut.persist(generatePersistedCollection()));
    }

    @Test
    void updateThrowsForUserWithoutWriteAccess() {
        grantAccess(AccessLevel.READ);
        assertThrows(AccessDeniedException.class, () -> sut.update(collectionDto));
    }

    @Test
    void updateDoesNotThrowForUserWithWriteAccess() {
        grantAccess(AccessLevel.WRITE);
        collectionDto.getLabel().set(Environment.LANGUAGE, "new label");
        assertDoesNotThrow(() -> sut.update(collectionDto));
    }

    @Test
    void updateOrderedMemberPositionThrowsForUserWithoutWriteAccess() {
        grantAccess(AccessLevel.READ);
        assertThrows(
                AccessDeniedException.class,
                () -> sut.updateOrderedMemberPosition(
                        orderedCollection.getUri(),
                        vocabulary.getUri(),
                        orderedCollection.getMembers().getFirst(),
                        0,
                        1));
    }

    @Test
    void updateOrderedMemberPositionDoesNotThrowForUserWithWriteAccess() {
        grantAccess(AccessLevel.WRITE);
        assertDoesNotThrow(() -> sut.updateOrderedMemberPosition(
                orderedCollection.getUri(),
                vocabulary.getUri(),
                orderedCollection.getMembers().getFirst(),
                0,
                1));
    }

    @Test
    void findAddMemberOptionsThrowsForUserWithoutWriteAccess() {
        grantAccess(AccessLevel.READ);
        assertThrows(
                AccessDeniedException.class,
                () -> sut.findAddMemberOptions(
                        unorderedCollection.getUri(), vocabulary.getUri(), null, Pageable.ofSize(10)));
    }

    @Test
    void findAddMemberOptionsDoesNotThrowForUserWithWriteAccess() {
        grantAccess(AccessLevel.WRITE);
        assertDoesNotThrow(() ->
                sut.findAddMemberOptions(unorderedCollection.getUri(), vocabulary.getUri(), null, Pageable.ofSize(10)));
    }

    @Test
    void removeThrowsForUserWithoutWriteAccess() {
        grantAccess(AccessLevel.READ);
        assertThrows(AccessDeniedException.class, () -> sut.remove(unorderedCollection.getUri(), vocabulary.getUri()));
    }

    @Test
    void removeDoesNotThrowForUserWithWriteAccess() {
        grantAccess(AccessLevel.WRITE);
        assertDoesNotThrow(() -> sut.remove(unorderedCollection.getUri(), vocabulary.getUri()));
    }
}
