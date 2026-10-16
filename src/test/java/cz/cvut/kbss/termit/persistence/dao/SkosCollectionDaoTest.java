package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.dto.TypeAwareEntity;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkosCollectionDaoTest extends BaseDaoTestRunner {
    @Autowired
    private EntityManager em;

    @Autowired
    private SkosCollectionDao sut;

    @Autowired
    private VocabularyDao vocabularyDao;

    @Autowired
    private DescriptorFactory descriptorFactory;

    private Vocabulary vocabulary;
    private Descriptor vocabularyDescriptor;

    private List<Term> unorderedMembers;
    private TermCollection unorderedCollection;

    private List<Term> orderedMembers;
    private OrderedTermCollection orderedCollection;

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

    private static List<URI> identifiers(Collection<TypeAwareEntity> collection) {
        return new ArrayList<>(collection.stream().map(TypeAwareEntity::getUri).toList());
    }

    private static List<URI> identifiersFromEntity(Collection<? extends HasIdentifier> collection) {
        return new ArrayList<>(collection.stream().map(HasIdentifier::getUri).toList());
    }

    @Test
    void findAllReturnsAllCollectionsInVocabulary() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final TermCollection unrelated = Generator.generateTermCollection(anotherVocabulary);
        transactional(() -> {
            em.persist(unrelated, descriptorFactory.vocabularyDescriptor(anotherVocabulary));
        });

        final List<SkosCollectionDto> result =
                sut.findAll(vocabulary, Pageable.ofSize(10)).getContent();

        assertEquals(2, result.size());
        final List<URI> identifiers = result.stream().map(HasIdentifier::getUri).toList();
        assertTrue(identifiers.contains(unorderedCollection.getUri()));
        assertTrue(identifiers.contains(orderedCollection.getUri()));
    }

    @Test
    void findAllRespectsPagination() {
        final List<TermCollection> additional = Stream.generate(() -> Generator.generateTermCollection(vocabulary))
                .limit(3)
                .toList();

        transactional(() -> additional.forEach(c -> em.persist(c, vocabularyDescriptor)));

        final Set<URI> allCollections = new HashSet<>(identifiersFromEntity(additional));
        allCollections.add(unorderedCollection.getUri());
        allCollections.add(orderedCollection.getUri());

        final List<SkosCollectionDto> result =
                sut.findAll(vocabulary, PageRequest.of(1, 2)).getContent();

        assertEquals(2, result.size());
        result.forEach(dto -> assertThat(allCollections, hasItem(dto.getUri())));
    }

    @Test
    void findAllReturnsDisjointPagesCoveringAllCollections() {
        final List<TermCollection> additional = Stream.generate(() -> Generator.generateTermCollection(vocabulary))
                .limit(3)
                .toList();
        transactional(() -> additional.forEach(c -> em.persist(c, vocabularyDescriptor)));

        final Set<URI> allCollections = new HashSet<>(identifiersFromEntity(additional));
        allCollections.add(unorderedCollection.getUri());
        allCollections.add(orderedCollection.getUri());

        final int pageSize = 2;
        final List<URI> found = new ArrayList<>();
        for (int page = 0; page * pageSize < allCollections.size(); page++) {
            sut.findAll(vocabulary, PageRequest.of(page, pageSize)).forEach(dto -> found.add(dto.getUri()));
        }

        assertEquals(allCollections.size(), found.size());
        assertEquals(allCollections, new HashSet<>(found));
    }

    @Test
    void findAllReturnsEmptyListForPageBeyondLastCollection() {
        final Page<SkosCollectionDto> result = sut.findAll(vocabulary, PageRequest.of(1, 10));
        assertTrue(result.isEmpty());
    }

    @Test
    void findDtoReturnsDtoOfOrderedCollection() {
        final SkosCollectionDto expectedDto = Environment.getDtoMapper().skosCollectionToDto(orderedCollection);
        // DTO represents SKOS Collection, the only expected additional type is ordered collection
        expectedDto.setTypes(Set.of(SKOS.ORDERED_COLLECTION));

        final SkosCollectionDto result = sut.findDto(orderedCollection.getUri());
        assertEquals(expectedDto, result);
    }

    @Test
    void findDtoReturnsDtoOfUnorderedCollection() {
        final SkosCollectionDto expectedDto = Environment.getDtoMapper().skosCollectionToDto(unorderedCollection);
        expectedDto.setTypes(Set.of());

        final SkosCollectionDto result = sut.findDto(unorderedCollection.getUri());
        assertEquals(expectedDto, result);
    }

    @Test
    void findMembersReturnsStreamOfTypeAwareEntitiesWithLoadedTypes() {
        orderedCollection.getMembers().add(1, unorderedCollection.getUri());
        orderedCollection.getMembers().sort(Comparator.comparing(URI::toString));

        transactional(() -> {
            em.merge(orderedCollection, vocabularyDescriptor);
        });

        final List<TypeAwareEntity> result =
                sut.findMembers(orderedCollection, Pageable.ofSize(100)).toList();

        assertEquals(orderedCollection.getMembers().size(), result.size());
        final List<URI> resultIdentifiers = identifiers(result);
        resultIdentifiers.sort(Comparator.comparing(URI::toString));
        assertEquals(orderedCollection.getMembers(), resultIdentifiers);

        final List<URI> orderedMemberUris = identifiersFromEntity(orderedMembers);

        result.forEach(e -> {
            assertFalse(e.getTypes().isEmpty());
            if (e.getUri().equals(unorderedCollection.getUri())) {
                assertTrue(e.isCollection());
                assertFalse(e.isTerm());
            } else {
                assertTrue(orderedMemberUris.contains(e.getUri()));
                assertTrue(e.isTerm());
                assertFalse(e.isCollection());
            }
        });
    }

    @Test
    void findMembersRespectsPagination() {
        final List<TypeAwareEntity> result;
        try (final Stream<TypeAwareEntity> stream = sut.findMembers(orderedCollection, PageRequest.of(1, 2))) {
            result = stream.toList();
        }

        final Set<URI> expected = new HashSet<>(orderedCollection.getMembers().subList(2, 4));
        assertEquals(2, result.size());
        assertEquals(expected, result.stream().map(TypeAwareEntity::getUri).collect(Collectors.toSet()));
        result.forEach(e -> assertTrue(e.isTerm()));
    }

    @Test
    void findMembersReturnsRemainingMembersOnLastPartialPage() {
        // Request the last page of the ordered collection which is not full (e.g. PageRequest.of(2, 2) for 5 members)
        // and verify that only the remaining members (subList(4, 5)) are returned.
        final List<TypeAwareEntity> result;
        try (final Stream<TypeAwareEntity> stream = sut.findMembers(orderedCollection, PageRequest.of(2, 2))) {
            result = stream.toList();
        }

        final List<URI> expected = orderedCollection.getMembers().subList(4, 5);
        assertEquals(expected, identifiers(result));
    }

    @Test
    void findMembersReturnsEmptyStreamForPageBeyondLastMember() {
        // Request a page whose offset is greater than the number of collection members
        // and verify that the returned stream is empty.
        final List<TypeAwareEntity> result;
        try (final Stream<TypeAwareEntity> stream = sut.findMembers(orderedCollection, PageRequest.of(1, 10))) {
            result = stream.toList();
        }

        assertTrue(result.isEmpty());
    }

    @Test
    void findMembersOfUnorderedCollectionReturnsDisjointPagesCoveringAllMembers() {
        // Iterate over all pages of the unordered collection with a small page size
        // and verify that no member appears on more than one page
        // and the union of all pages equals all members of the collection.
        final int pageSize = 2;
        final List<URI> found = new ArrayList<>();
        for (int page = 0; page * pageSize < unorderedMembers.size(); page++) {
            final TermCollection collection = sut.find(TermCollection.class, unorderedCollection.getUri(), vocabulary);
            try (final Stream<TypeAwareEntity> stream = sut.findMembers(collection, PageRequest.of(page, pageSize))) {
                stream.forEach(e -> found.add(e.getUri()));
            }
        }

        assertEquals(unorderedMembers.size(), found.size());
        assertEquals(new HashSet<>(identifiersFromEntity(unorderedMembers)), new HashSet<>(found));
    }

    @Test
    void orderedCollectionHasCollectionTypeWhenRetrievedFromRepository() {
        final OrderedTermCollection collection =
                sut.find(OrderedTermCollection.class, orderedCollection.getUri(), vocabulary);
        assertThat(collection.getTypes(), hasItem(SKOS.COLLECTION));

        final SkosCollectionDto dto = sut.findDto(orderedCollection.getUri());
        assertThat(dto.getTypes(), hasItems(SKOS.ORDERED_COLLECTION));
    }

    @Test
    void unorderedCollectionHasNoAdditionalTypeWhenRetrievedFromRepository() {
        // the type of the entity itself is not included in types field

        final TermCollection collection = sut.find(TermCollection.class, unorderedCollection.getUri(), vocabulary);
        assertTrue(collection.getTypes().isEmpty());

        final SkosCollectionDto dto = sut.findDto(unorderedCollection.getUri());
        assertTrue(dto.getTypes().isEmpty());
    }
}
