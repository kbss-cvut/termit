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

        final List<SkosCollectionDto> result = sut.findAll(vocabulary, Pageable.ofSize(10));

        assertEquals(2, result.size());
        final List<URI> identifiers = result.stream().map(HasIdentifier::getUri).toList();
        assertTrue(identifiers.contains(unorderedCollection.getUri()));
        assertTrue(identifiers.contains(orderedCollection.getUri()));
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
    void orderedCollectionHasCollectionTypeWhenRetrievedFromRepository() {
        final OrderedTermCollection collection = sut.findOrdered(orderedCollection.getUri(), vocabulary);
        assertThat(collection.getTypes(), hasItem(SKOS.COLLECTION));

        final SkosCollectionDto dto = sut.findDto(orderedCollection.getUri());
        assertThat(dto.getTypes(), hasItems(SKOS.ORDERED_COLLECTION));
    }

    @Test
    void unorderedCollectionHasCollectionTypeWhenRetrievedFromRepository() {
        // the type of the entity itself is not included in types field

        final TermCollection collection = sut.findUnordered(unorderedCollection.getUri(), vocabulary);
        assertTrue(collection.getTypes().isEmpty());

        final SkosCollectionDto dto = sut.findDto(unorderedCollection.getUri());
        assertTrue(dto.getTypes().isEmpty());
    }
}
