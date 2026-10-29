package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.SkosCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkosCollectionRepositoryServiceTest extends BaseServiceTestRunner {
    @Autowired
    private SkosCollectionRepositoryService sut;

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private VocabularyDao vocabularyDao;

    @Autowired
    private DtoMapper dtoMapper;

    private Vocabulary vocabulary;
    private Descriptor vocabularyDescriptor;

    private List<Term> unorderedMembers;
    private TermCollection unorderedCollection;

    private List<Term> orderedMembers;
    private OrderedTermCollection orderedCollection;

    private final Pageable pageable = Pageable.ofSize(1000);

    @BeforeEach
    void setUp() {
        enableRdfsInference(em);
        vocabulary = Generator.generateVocabularyWithId();
        vocabularyDescriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> vocabularyDao.persist(vocabulary));

        unorderedMembers = Generator.generateTermsWithIds(5);
        unorderedCollection = Generator.generateTermCollection(vocabulary, unorderedMembers);

        orderedMembers = Generator.generateTermsWithIds(5);
        orderedCollection = Generator.generateOrderedTermCollection(vocabulary, orderedMembers);

        transactional(() -> {
            Stream.of(unorderedMembers, orderedMembers, Set.of(unorderedCollection, orderedCollection))
                    .flatMap(Collection::stream)
                    .forEach(e -> em.persist(e, vocabularyDescriptor));
        });
    }

    @Test
    void findMembersMapsIdentifiersToRespectiveEntities() {
        final int collectionIndex = 3;
        orderedCollection.getMembers().add(collectionIndex, unorderedCollection.getUri());
        transactional(() -> em.merge(orderedCollection, vocabularyDescriptor));

        final List<HasIdentifier> result =
                new ArrayList<>(sut.findMembers(orderedCollection, pageable).getContent());

        assertEquals(orderedCollection.getMembers().size(), result.size());

        final HasIdentifier nestedCollection = result.remove(collectionIndex);
        assertInstanceOf(SkosCollectionDto.class, nestedCollection);
        final SkosCollectionDto expectedDto = dtoMapper.skosCollectionToDto(unorderedCollection);
        assertEquals(expectedDto, nestedCollection);
        final List<TermInfo> expectedTerms =
                orderedMembers.stream().map(Term::toTermInfo).toList();
        assertEquals(expectedTerms, result);
    }

    @Test
    void findMembersMaintainsMemberOrder() {
        final List<URI> initialOrder = new ArrayList<>(orderedCollection.getMembers());
        final List<URI> actualMembers = sut.findMembers(orderedCollection, pageable).stream()
                .map(HasIdentifier::getUri)
                .toList();
        assertOrder(initialOrder, actualMembers);
    }

    @Test
    void findMembersReturnsOnlyMembersOfRequestedPage() {
        final List<HasIdentifier> result =
                sut.findMembers(orderedCollection, PageRequest.of(1, 2)).getContent();

        assertEquals(2, result.size());
        result.forEach(Assertions::assertNotNull);
        final Set<URI> expected = new HashSet<>(orderedCollection.getMembers().subList(2, 4));
        assertEquals(expected, result.stream().map(HasIdentifier::getUri).collect(Collectors.toSet()));
    }

    @Test
    void findMembersReturnsPageMembersInCollectionOrder() {
        final List<URI> expectedOrder =
                new ArrayList<>(orderedCollection.getMembers().subList(2, 4));

        final List<URI> actualMembers = sut.findMembers(orderedCollection, PageRequest.of(1, 2)).stream()
                .map(HasIdentifier::getUri)
                .toList();
        assertOrder(expectedOrder, actualMembers);
    }

    @Test
    void findMembersReturnsEmptyListForPageBeyondLastMember() {
        final Page<HasIdentifier> result = sut.findMembers(orderedCollection, PageRequest.of(1, 10));
        assertTrue(result.isEmpty());
    }

    @Test
    void findMembersResolvesNestedCollectionOnRequestedPage() {
        final int collectionIndex = 3;
        orderedCollection.getMembers().add(collectionIndex, unorderedCollection.getUri());
        transactional(() -> em.merge(orderedCollection, vocabularyDescriptor));

        final List<HasIdentifier> result =
                sut.findMembers(orderedCollection, PageRequest.of(1, 2)).getContent();

        assertEquals(2, result.size());
        final HasIdentifier nestedCollection = result.get(collectionIndex - 2);
        assertInstanceOf(SkosCollectionDto.class, nestedCollection);
        assertEquals(dtoMapper.skosCollectionToDto(unorderedCollection), nestedCollection);
        assertEquals(orderedMembers.get(2).toTermInfo(), result.getFirst());
    }

    @Test
    void findMembersWithMaxIntegerPageSizeReturnsAllMembers() {
        final List<URI> initialOrder = new ArrayList<>(orderedCollection.getMembers());

        final List<URI> actualMembers = sut.findMembers(orderedCollection, Pageable.ofSize(Integer.MAX_VALUE)).stream()
                .map(HasIdentifier::getUri)
                .toList();
        assertOrder(initialOrder, actualMembers);
    }

    @Test
    void orderedCollectionMaintainsMemberOrderWhenLoadedFromRepository() {
        loadAndAssertOrder(orderedCollection);

        Collections.swap(orderedCollection.getMembers(), 1, 3);

        sut.update(orderedCollection, vocabulary);
        em.clear();
        em.getEntityManagerFactory().getCache().evictAll();

        loadAndAssertOrder(orderedCollection);
    }

    @Test
    void persistPersistsValidUnorderedCollection() {
        final TermCollection collection = Generator.generateTermCollection(vocabulary, unorderedMembers);

        sut.persist(collection, vocabulary);

        final TermCollection managed = readOnlyTransactional(
                () -> sut.findUnordered(collection.getUri(), vocabulary).orElseThrow());
        assertEquals(collection.getUri(), managed.getUri());
        assertEquals(collection.getLabel(), managed.getLabel());
        assertEquals(collection.getMembers(), managed.getMembers());
    }

    @Test
    void persistPersistsValidOrderedCollection() {
        final OrderedTermCollection collection = Generator.generateOrderedTermCollection(vocabulary, orderedMembers);

        sut.persist(collection, vocabulary);

        final OrderedTermCollection managed = readOnlyTransactional(
                () -> sut.findOrdered(collection.getUri(), vocabulary).orElseThrow());
        assertEquals(collection.getUri(), managed.getUri());
        assertEquals(collection.getLabel(), managed.getLabel());
        assertEquals(collection.getMembers(), managed.getMembers());
    }

    @Test
    void findReturnsEmptyOptionalWhenCollectionDoesNotExist() {
        final Optional<SkosCollection<?>> result = sut.find(Generator.generateUri(), vocabulary);
        assertTrue(result.isEmpty());
    }

    @Test
    void updateThrowsNotFoundExceptionWhenCollectionDoesNotExist() {
        final TermCollection collection = Generator.generateTermCollection(vocabulary, unorderedMembers);
        final NotFoundException ex = assertThrows(NotFoundException.class, () -> sut.update(collection, vocabulary));
        assertThat(ex.getMessage()).containsIgnoringCase("not found");
        assertThat(ex.getMessage()).contains(collection.getUri().toString());
    }

    @Test
    void updateThrowsValidationExceptionWhenOrderedCollectionIsUpdatedAsUnordered() {
        final TermCollection update = new TermCollection();
        update.setUri(orderedCollection.getUri());
        update.setLabel(orderedCollection.getLabel());
        update.setVocabulary(vocabulary.getUri());
        update.setMembers(new HashSet<>(orderedCollection.getMembers()));
        update.setTypes(Set.of());

        final ValidationException ex = assertThrows(ValidationException.class, () -> sut.update(update, vocabulary));
        assertThat(ex.getMessage()).contains(OrderedTermCollection.class.getSimpleName());
        assertThat(ex.getMessage()).contains("as " + TermCollection.class.getSimpleName());
    }

    @Test
    void updateThrowsValidationExceptionWhenUnorderedCollectionIsUpdatedAsOrdered() {
        final OrderedTermCollection update = new OrderedTermCollection();
        update.setUri(unorderedCollection.getUri());
        update.setLabel(unorderedCollection.getLabel());
        update.setVocabulary(vocabulary.getUri());
        update.setMembers(new ArrayList<>(unorderedCollection.getMembers()));

        final ValidationException ex = assertThrows(ValidationException.class, () -> sut.update(update, vocabulary));
        assertThat(ex.getMessage()).contains(TermCollection.class.getSimpleName());
        assertThat(ex.getMessage()).contains("as " + OrderedTermCollection.class.getSimpleName());
    }

    @Test
    void persistPersistsUnorderedCollectionWithoutMembers() {
        final TermCollection collection = Generator.generateTermCollection(vocabulary, List.of());
        collection.setMembers(Set.of());

        sut.persist(collection, vocabulary);

        final TermCollection managed = readOnlyTransactional(
                () -> sut.findUnordered(collection.getUri(), vocabulary).orElseThrow());
        assertTrue(managed.getMembers().isEmpty());
    }

    @Test
    void persistPersistsOrderedCollectionWithoutMembers() {
        final OrderedTermCollection collection = Generator.generateOrderedTermCollection(vocabulary, List.of());
        collection.setMembers(null);

        sut.persist(collection, vocabulary);

        final OrderedTermCollection managed = readOnlyTransactional(
                () -> sut.findOrdered(collection.getUri(), vocabulary).orElseThrow());
        assertNull(managed.getMembers());
    }

    @Test
    void persistThrowsNotFoundExceptionWhenMemberDoesNotExist() {
        final TermCollection collection = Generator.generateTermCollection(vocabulary, unorderedMembers);
        final URI missingMember = Generator.generateUri();
        collection.addMember(missingMember);

        final NotFoundException ex = assertThrows(NotFoundException.class, () -> sut.persist(collection, vocabulary));
        assertThat(ex.getMessage()).containsIgnoringCase("not found");
        assertThat(ex.getMessage()).contains(missingMember.toString());
    }

    @Test
    void updateThrowsNotFoundExceptionWhenMemberDoesNotExist() {
        unorderedCollection.setMembers(new HashSet<>(unorderedCollection.getMembers()));
        final URI missingMember = Generator.generateUri();
        unorderedCollection.addMember(missingMember);

        final NotFoundException ex =
                assertThrows(NotFoundException.class, () -> sut.update(unorderedCollection, vocabulary));
        assertThat(ex.getMessage()).containsIgnoringCase("not found");
        assertThat(ex.getMessage()).contains(missingMember.toString());
    }

    @Test
    void persistThrowsValidationExceptionWhenCollectionIsMemberOfItself() {
        final TermCollection collection = Generator.generateTermCollection(vocabulary, unorderedMembers);
        collection.addMember(collection.getUri());

        final ValidationException ex =
                assertThrows(ValidationException.class, () -> sut.persist(collection, vocabulary));
        assertThat(ex.getMessage()).containsIgnoringCase("member of itself");
    }

    @Test
    void updateThrowsValidationExceptionWhenUnorderedCollectionIsMemberOfItself() {
        unorderedCollection.addMember(unorderedCollection.getUri());

        final ValidationException ex =
                assertThrows(ValidationException.class, () -> sut.update(unorderedCollection, vocabulary));
        assertThat(ex.getMessage()).containsIgnoringCase("member of itself");
    }

    @Test
    void updateThrowsValidationExceptionWhenOrderedCollectionIsMemberOfItself() {
        orderedCollection.addMember(orderedCollection.getUri());

        final ValidationException ex =
                assertThrows(ValidationException.class, () -> sut.update(orderedCollection, vocabulary));
        assertThat(ex.getMessage()).containsIgnoringCase("member of itself");
    }

    @Test
    void updateUpdatesLabelOfCollection() {
        final String updatedLabel = "Updated Label";
        orderedCollection.getLabel().set(Environment.LANGUAGE, updatedLabel);

        sut.update(orderedCollection, vocabulary);
        em.clear();
        em.getEntityManagerFactory().getCache().evictAll();

        final OrderedTermCollection updated =
                sut.findOrdered(orderedCollection.getUri(), vocabulary).orElseThrow();
        assertEquals(updatedLabel, updated.getLabel().get(Environment.LANGUAGE));
    }

    @Test
    void updateRejectsChangeOfCollectionVocabulary() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        transactional(() -> vocabularyDao.persist(anotherVocabulary));
        unorderedCollection.setVocabulary(anotherVocabulary.getUri());

        final ValidationException ex =
                assertThrows(ValidationException.class, () -> sut.update(unorderedCollection, vocabulary));
        assertThat(ex.getMessage()).containsIgnoringCase("vocabulary");
        assertThat(ex.getMessage()).containsIgnoringCase("cannot be changed");
    }

    @Test
    void persistThrowsNotFoundExceptionWhenMemberIsFromAnotherVocabulary() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        transactional(() -> vocabularyDao.persist(anotherVocabulary));
        final Term foreignTerm = Generator.generateTermWithId(anotherVocabulary.getUri());
        transactional(() -> em.persist(foreignTerm, descriptorFactory.vocabularyDescriptor(anotherVocabulary)));
        final TermCollection collection = Generator.generateTermCollection(vocabulary, unorderedMembers);
        collection.addMember(foreignTerm.getUri());

        final NotFoundException ex = assertThrows(NotFoundException.class, () -> sut.persist(collection, vocabulary));
        assertThat(ex.getMessage()).contains(foreignTerm.getUri().toString());
    }

    @Test
    void persistPersistsCollectionWithNestedCollectionMember() {
        final TermCollection collection = Generator.generateTermCollection(vocabulary, unorderedMembers);
        collection.addMember(orderedCollection.getUri());
        collection.addMember(unorderedCollection.getUri());

        sut.persist(collection, vocabulary);

        final TermCollection managed = readOnlyTransactional(
                () -> sut.findUnordered(collection.getUri(), vocabulary).orElseThrow());
        assertEquals(collection.getMembers(), managed.getMembers());
    }

    @Test
    void persistThrowsValidationExceptionWhenLabelInPrimaryLanguageIsMissing() {
        final String otherLanguage = "cs";
        assertNotEquals(Environment.LANGUAGE, otherLanguage);
        final TermCollection collection = Generator.generateTermCollection(vocabulary, unorderedMembers);
        collection.setLabel(MultilingualString.create("Kolekce", otherLanguage));

        final ValidationException ex =
                assertThrows(ValidationException.class, () -> sut.persist(collection, vocabulary));
        assertThat(ex.getMessage()).contains("label");
        assertThat(ex.getMessage()).containsIgnoringCase("primary");
    }

    @Test
    void persistThrowsValidationExceptionWhenPrimaryLanguageIsNull() {
        final TermCollection collection = Generator.generateTermCollection(vocabulary, unorderedMembers);
        collection.setPrimaryLanguage(null);

        final ValidationException ex =
                assertThrows(ValidationException.class, () -> sut.persist(collection, vocabulary));
        assertThat(ex.getMessage()).contains("primaryLanguage");
    }

    @Test
    void findReturnsOrderedCollectionAsOrderedTermCollection() {
        final SkosCollection<?> result =
                sut.find(orderedCollection.getUri(), vocabulary).orElseThrow();

        final OrderedTermCollection ordered = assertInstanceOf(OrderedTermCollection.class, result);
        assertEquals(orderedCollection.getUri(), ordered.getUri());
        assertEquals(orderedCollection.getMembers(), ordered.getMembers());
    }

    @Test
    void findReturnsUnorderedCollectionAsTermCollection() {
        final SkosCollection<?> result =
                sut.find(unorderedCollection.getUri(), vocabulary).orElseThrow();

        final TermCollection unordered = assertInstanceOf(TermCollection.class, result);
        assertEquals(unorderedCollection.getUri(), unordered.getUri());
        assertEquals(unorderedCollection.getMembers(), unordered.getMembers());
    }

    @Test
    void findMembersOfUnorderedCollectionMapsIdentifiersToRespectiveEntities() {
        unorderedCollection.addMember(orderedCollection.getUri());
        transactional(() -> em.merge(unorderedCollection, vocabularyDescriptor));

        final List<HasIdentifier> result =
                sut.findMembers(unorderedCollection, pageable).getContent();

        final SkosCollectionDto expectedDto = dtoMapper.skosCollectionToDto(orderedCollection);

        final Set<HasIdentifier> expected = new HashSet<>();
        unorderedMembers.forEach(t -> expected.add(t.toTermInfo()));
        expected.add(expectedDto);

        assertEquals(expected.size(), result.size());
        assertEquals(expected, new HashSet<>(result));
        final HasIdentifier nestedCollection = result.stream()
                .filter(e -> e.getUri().equals(orderedCollection.getUri()))
                .findFirst()
                .orElseThrow();
        assertInstanceOf(SkosCollectionDto.class, nestedCollection);
        assertEquals(expectedDto, nestedCollection);
    }

    @Test
    void updateMemberPositionThrowsForNegativePosition() {
        final URI member = Generator.generateUri();
        assertThrows(
                ValidationException.class,
                () -> sut.updateMemberPosition(orderedCollection, vocabulary, member, -1, 1));
        assertThrows(
                ValidationException.class,
                () -> sut.updateMemberPosition(orderedCollection, vocabulary, member, 1, -1));
        assertDoesNotThrow(() -> sut.updateMemberPosition(orderedCollection, vocabulary, member, 0, 0));
    }

    @Test
    void updateMemberPositionMovesMemberToLastPositionWhenNewPositionIsOutOfBounds() {
        final int originalPos = 3;
        final int newPos = 999;
        final URI member = orderedCollection.getMembers().get(originalPos);
        final URI lastMember = orderedCollection.getMembers().getLast();

        sut.updateMemberPosition(orderedCollection, vocabulary, member, originalPos, newPos);

        assertEquals(member, orderedCollection.getMembers().getLast());
        assertEquals(lastMember, orderedCollection.getMembers().get(orderedMembers.size() - 2));
        assertEquals(
                orderedMembers.get(originalPos + 1).getUri(),
                orderedCollection.getMembers().get(originalPos));
        assertEquals(orderedMembers.size(), orderedCollection.getMembers().size());
    }

    @Test
    void updateMemberPositionMovesMemberToFirstPosition() {
        final int originalPos = 2;
        final int newPos = 0;
        final URI member = orderedCollection.getMembers().get(originalPos);
        final URI firstMember = orderedCollection.getMembers().getFirst();

        sut.updateMemberPosition(orderedCollection, vocabulary, member, originalPos, newPos);

        assertEquals(member, orderedCollection.getMembers().getFirst());
        assertEquals(firstMember, orderedCollection.getMembers().get(1));
        assertEquals(orderedMembers.size(), orderedCollection.getMembers().size());
    }

    @ParameterizedTest
    @CsvSource({"2, 1", "1, 2", "0,1", "4, 3"})
    void updateMemberPositionMovesMemberToAdjacentPosition(int originalPos, int newPos) {
        assertEquals(1, Math.abs(originalPos - newPos));

        final URI member = orderedCollection.getMembers().get(originalPos);
        final URI curMember = orderedCollection.getMembers().get(newPos);
        final List<URI> expectedOrder = new ArrayList<>(orderedCollection.getMembers());
        Collections.swap(expectedOrder, originalPos, newPos);

        sut.updateMemberPosition(orderedCollection, vocabulary, member, originalPos, newPos);

        assertEquals(member, orderedCollection.getMembers().get(newPos));
        assertEquals(curMember, orderedCollection.getMembers().get(originalPos));
        assertOrder(expectedOrder, orderedCollection.getMembers());
    }

    @Test
    void updateMemberPositionUpdatesTheCollection() {
        final int originalPos = 1;
        final int newPos = 4;
        final URI member = orderedCollection.getMembers().get(originalPos);
        final List<URI> expectedOrder = new ArrayList<>(orderedCollection.getMembers());
        expectedOrder.remove(originalPos);
        expectedOrder.add(newPos, member);

        sut.updateMemberPosition(orderedCollection, vocabulary, member, originalPos, newPos);
        em.clear();
        em.getEntityManagerFactory().getCache().evictAll();

        final List<URI> updatedMembers = sut.findOrdered(orderedCollection.getUri(), vocabulary)
                .orElseThrow()
                .getMembers();
        assertEquals(member, updatedMembers.get(newPos));
        assertOrder(expectedOrder, updatedMembers);
    }

    @Test
    void removeRemovesUnorderedCollection() {
        transactional(() -> {
            SkosCollection<?> collection =
                    sut.find(unorderedCollection.getUri(), vocabulary).orElseThrow();
            sut.remove(collection, vocabulary);
        });

        assertTrue(sut.find(unorderedCollection.getUri(), vocabulary).isEmpty());
        assertFalse(sut.exists(unorderedCollection.getUri()));
    }

    @Test
    void removeRemovesOrderedCollection() {
        transactional(() -> {
            SkosCollection<?> collection =
                    sut.find(orderedCollection.getUri(), vocabulary).orElseThrow();
            sut.remove(collection, vocabulary);
        });

        assertTrue(sut.find(orderedCollection.getUri(), vocabulary).isEmpty());
        assertFalse(sut.exists(orderedCollection.getUri()));
    }

    @Test
    void findAddMemberOptionsMapsIdentifiersToRespectiveEntities() {
        final List<HasIdentifier> result = sut.findAddMemberOptions(
                        unorderedCollection.getUri(), vocabulary, null, pageable)
                .getContent();

        final Set<HasIdentifier> expected = new HashSet<>();
        orderedMembers.forEach(t -> expected.add(t.toTermInfo()));

        final SkosCollectionDto expectedDto = dtoMapper.skosCollectionToDto(orderedCollection);
        expected.add(expectedDto);

        assertEquals(expected.size(), result.size());
        assertEquals(expected, new HashSet<>(result));

        final HasIdentifier collectionOption = result.stream()
                .filter(e -> e.getUri().equals(orderedCollection.getUri()))
                .findFirst()
                .orElseThrow();
        assertInstanceOf(SkosCollectionDto.class, collectionOption);
        assertEquals(expectedDto, collectionOption);

        result.stream()
                .filter(e -> !e.getUri().equals(orderedCollection.getUri()))
                .forEach(e -> assertInstanceOf(TermInfo.class, e));
    }

    @Test
    void findAddMemberOptionsReturnsOnlyOptionsMatchingSearchString() {
        final Term matching = orderedMembers.get(2);
        final String searchString = matching.getLabel().get(Environment.LANGUAGE);

        final Page<HasIdentifier> result =
                sut.findAddMemberOptions(unorderedCollection.getUri(), vocabulary, searchString, pageable);

        assertEquals(List.of(matching.toTermInfo()), result.getContent());
        assertEquals(1, result.getTotalElements());
    }

    private void loadAndAssertOrder(OrderedTermCollection collection) {
        final List<URI> initialOrder = new ArrayList<>(collection.getMembers());

        final OrderedTermCollection managedOrdered =
                sut.findOrdered(collection.getUri(), vocabulary).orElseThrow();
        assertOrder(initialOrder, managedOrdered.getMembers());
    }

    private <T> void assertOrder(List<T> expectedOrder, List<T> actualOrder) {
        assertEquals(expectedOrder.size(), actualOrder.size());
        for (int i = 0; i < actualOrder.size(); i++) {
            final T expected = expectedOrder.get(i);
            final T actual = actualOrder.get(i);

            assertEquals(expected, actual, "Order differs at index " + i);
        }
    }

    @Test
    void addMemberAddsMemberToUnorderedCollection() {
        final Term newMember = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> em.persist(newMember, vocabularyDescriptor));

        sut.addMember(unorderedCollection, vocabulary, newMember.getUri());

        TermCollection updated =
                sut.findUnordered(unorderedCollection.getUri(), vocabulary).orElseThrow();
        assertThat(updated.getMembers()).contains(newMember.getUri());
    }

    @Test
    void addMemberAddsMemberToFirstPositionInOrderedCollection() {
        final Term newMember = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> em.persist(newMember, vocabularyDescriptor));

        sut.addMember(orderedCollection, vocabulary, newMember.getUri());

        OrderedTermCollection updated =
                sut.findOrdered(orderedCollection.getUri(), vocabulary).orElseThrow();
        assertEquals(updated.getMembers().getFirst(), newMember.getUri());
    }

    @Test
    void removeMemberRemovesMemberFromUnorderedCollection() {
        final URI member = unorderedMembers.get(2).getUri();
        final Set<URI> expectedMembers = new HashSet<>(unorderedCollection.getMembers());
        expectedMembers.remove(member);

        sut.removeMember(unorderedCollection, vocabulary, member);

        TermCollection updated =
                sut.findUnordered(unorderedCollection.getUri(), vocabulary).orElseThrow();
        assertThat(updated.getMembers()).doesNotContain(member);
        assertThat(updated.getMembers()).containsExactlyInAnyOrderElementsOf(expectedMembers);
    }

    @Test
    void removeMemberRemovesMemberFromOrderedCollection() {
        final URI member = orderedMembers.get(2).getUri();
        final List<URI> expectedMembers = new ArrayList<>(orderedCollection.getMembers());
        expectedMembers.remove(2);

        sut.removeMember(orderedCollection, vocabulary, member);

        OrderedTermCollection updated =
                sut.findOrdered(orderedCollection.getUri(), vocabulary).orElseThrow();
        assertThat(updated.getMembers()).doesNotContain(member);
        assertThat(updated.getMembers()).containsExactlyElementsOf(expectedMembers);
    }

    @Test
    void removeMemberDoesNothingWhenMemberIsNotInCollection() {
        final List<URI> expectedMembers = new ArrayList<>(orderedCollection.getMembers());

        sut.removeMember(orderedCollection, vocabulary, Generator.generateUri());

        OrderedTermCollection updated =
                sut.findOrdered(orderedCollection.getUri(), vocabulary).orElseThrow();
        assertThat(updated.getMembers()).containsExactlyElementsOf(expectedMembers);
    }

    @Test
    void findCollectionsWithMemberReturnsOrderedCollectionContainingMember() {
        final Term member = orderedMembers.get(2);

        final Set<SkosCollection<?>> collections = sut.findCollectionsWithMember(member.getUri());
        assertEquals(1, collections.size());
        final OrderedTermCollection ordered = assertInstanceOf(
                OrderedTermCollection.class, collections.iterator().next());
        assertEquals(orderedCollection.getUri(), ordered.getUri());
        assertEquals(orderedCollection.getVocabulary(), ordered.getVocabulary());
        assertEquals(orderedCollection.getTypes(), ordered.getTypes());
    }

    @Test
    void findCollectionsWithMemberReturnsUnorderedCollectionContainingMember() {
        final Term member = unorderedMembers.get(2);

        final Set<SkosCollection<?>> collections = sut.findCollectionsWithMember(member.getUri());
        assertEquals(1, collections.size());
        final TermCollection unordered =
                assertInstanceOf(TermCollection.class, collections.iterator().next());
        assertEquals(unorderedCollection.getUri(), unordered.getUri());
        assertEquals(unorderedCollection.getVocabulary(), unordered.getVocabulary());
        assertThat(unordered.getTypes()).isEmpty();
    }

    @Test
    void findCollectionsWithMemberReturnsAllCollectionsWithTheMember() {
        final Term member = Generator.generateTermWithId(vocabulary.getUri());
        orderedCollection.getMembers().add(member.getUri());
        unorderedCollection.getMembers().add(member.getUri());
        transactional(() -> Stream.of(member, orderedCollection, unorderedCollection)
                .forEach(e -> em.merge(e, vocabularyDescriptor)));

        final Set<SkosCollection<?>> collections = sut.findCollectionsWithMember(member.getUri());
        assertEquals(2, collections.size());

        TermCollection unordered = collections.stream()
                .filter(e -> e.getUri().equals(unorderedCollection.getUri()))
                .map(TermCollection.class::cast)
                .findAny()
                .orElseThrow();
        OrderedTermCollection ordered = collections.stream()
                .filter(e -> e.getUri().equals(orderedCollection.getUri()))
                .map(OrderedTermCollection.class::cast)
                .findAny()
                .orElseThrow();

        assertEquals(unorderedCollection.getMembers(), unordered.getMembers());
        assertEquals(orderedCollection.getMembers(), ordered.getMembers());
        assertThat(unordered.getMembers()).contains(member.getUri());
        assertThat(ordered.getMembers()).contains(member.getUri());
    }

    @Test
    void removeMemberFromAllCollectionsRemovesMemberFromOrderedCollection() {
        final Term member = orderedMembers.get(2);

        sut.removeMemberFromAllCollections(member.getUri());

        final OrderedTermCollection updated =
                sut.findOrdered(orderedCollection.getUri(), vocabulary).orElseThrow();
        assertThat(updated.getMembers()).doesNotContain(member.getUri());
    }

    @Test
    void removeMemberFromAllCollectionsRemovesMemberFromUnorderedCollection() {
        final Term member = unorderedMembers.get(2);

        sut.removeMemberFromAllCollections(member.getUri());

        final TermCollection updated =
                sut.findUnordered(unorderedCollection.getUri(), vocabulary).orElseThrow();
        assertThat(updated.getMembers()).doesNotContain(member.getUri());
    }

    @Test
    void removeRemovesCollectionFromOtherCollections() {
        unorderedCollection.getMembers().add(orderedCollection.getUri());
        sut.update(unorderedCollection, vocabulary);

        sut.remove(orderedCollection, vocabulary);

        final TermCollection updated =
                sut.findUnordered(unorderedCollection.getUri(), vocabulary).orElseThrow();
        assertThat(updated.getMembers()).doesNotContain(orderedCollection.getUri());
    }

    @Test
    void removeRemovesCollectionFromOrderedCollectionKeepingOrderOfOtherMembers() {
        final List<URI> expectedMembers = new ArrayList<>(orderedCollection.getMembers());
        orderedCollection.getMembers().add(2, unorderedCollection.getUri());
        transactional(() -> em.merge(orderedCollection, vocabularyDescriptor));

        sut.remove(unorderedCollection, vocabulary);

        final OrderedTermCollection updated =
                sut.findOrdered(orderedCollection.getUri(), vocabulary).orElseThrow();
        assertEquals(expectedMembers, updated.getMembers());
    }

    @Test
    void updateThrowsValidationExceptionWhenLabelInPrimaryLanguageIsMissing() {
        final String otherLanguage = "cs";
        assertNotEquals(Environment.LANGUAGE, otherLanguage);
        unorderedCollection.setLabel(MultilingualString.create("Kolekce", otherLanguage));

        final ValidationException ex =
                assertThrows(ValidationException.class, () -> sut.update(unorderedCollection, vocabulary));
        assertThat(ex.getMessage()).contains("label");
        assertThat(ex.getMessage()).containsIgnoringCase("primary");
    }

    @Test
    void updateSetsPrimaryLanguageForCollectionWhenMissing() {
        unorderedCollection.setPrimaryLanguage(null);
        sut.update(unorderedCollection, vocabulary);
        assertEquals(vocabulary.getPrimaryLanguage(), unorderedCollection.getPrimaryLanguage());
    }

    @Test
    void addMemberDoesNothingWhenMemberIsAlreadyPresentInUnorderedCollection() {
        final URI member = unorderedMembers.get(2).getUri();

        sut.addMember(unorderedCollection, vocabulary, member);

        final TermCollection updated =
                sut.findUnordered(unorderedCollection.getUri(), vocabulary).orElseThrow();
        assertThat(updated.getMembers()).containsOnlyOnce(member);
    }

    @Test
    void addMemberDoesNothingWhenMemberIsAlreadyPresentInOrderedCollection() {
        final URI member = orderedMembers.get(2).getUri();
        final List<URI> expectedMembers = new ArrayList<>(orderedCollection.getMembers());

        sut.addMember(orderedCollection, vocabulary, member);

        final OrderedTermCollection updated =
                sut.findOrdered(orderedCollection.getUri(), vocabulary).orElseThrow();
        assertThat(updated.getMembers()).containsOnlyOnce(member);
        assertEquals(expectedMembers, updated.getMembers());
    }
}
