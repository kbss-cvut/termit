package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.SkosCollectionDto;
import cz.cvut.kbss.termit.dto.TypeAwareEntity;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.collection.OrderedTermCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import org.hamcrest.Matcher;
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
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
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

    private List<Term> membersA;
    private TermCollection unorderedCollectionA;

    private List<Term> membersB;
    private OrderedTermCollection orderedCollectionB;

    @BeforeEach
    void setUp() {
        enableRdfsInference(em);
        vocabulary = Generator.generateVocabularyWithId();
        vocabularyDescriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> vocabularyDao.persist(vocabulary));

        membersA = Generator.generateTermsWithIds(5);
        unorderedCollectionA = Generator.generateTermCollection(vocabulary, membersA);

        membersB = Generator.generateTermsWithIds(5);
        orderedCollectionB = Generator.generateOrderedTermCollection(vocabulary, membersB);

        transactional(() -> {
            Stream.of(membersA, membersB, Set.of(unorderedCollectionA, orderedCollectionB))
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
        assertTrue(identifiers.contains(unorderedCollectionA.getUri()));
        assertTrue(identifiers.contains(orderedCollectionB.getUri()));
    }

    @Test
    void findMembersReturnsStreamOfTypeAwareEntitiesWithLoadedTypes() {
        orderedCollectionB.getMembers().add(1, unorderedCollectionA.getUri());
        orderedCollectionB.getMembers().sort(Comparator.comparing(URI::toString));

        transactional(() -> {
            em.merge(orderedCollectionB, vocabularyDescriptor);
        });

        final List<TypeAwareEntity> result =
                sut.findMembers(orderedCollectionB, Pageable.ofSize(100)).toList();

        assertEquals(orderedCollectionB.getMembers().size(), result.size());
        final List<URI> resultIdentifiers = identifiers(result);
        resultIdentifiers.sort(Comparator.comparing(URI::toString));
        assertEquals(orderedCollectionB.getMembers(), resultIdentifiers);

        final List<URI> orderedMemberUris = identifiersFromEntity(membersB);

        result.forEach(e -> {
            assertFalse(e.getTypes().isEmpty());
            if (e.getUri().equals(unorderedCollectionA.getUri())) {
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
        try (final Stream<TypeAwareEntity> stream = sut.findMembers(orderedCollectionB, PageRequest.of(1, 2))) {
            result = stream.toList();
        }

        final Set<URI> expected = new HashSet<>(orderedCollectionB.getMembers().subList(2, 4));
        assertEquals(2, result.size());
        assertEquals(expected, result.stream().map(TypeAwareEntity::getUri).collect(Collectors.toSet()));
        result.forEach(e -> assertTrue(e.isTerm()));
    }

    @Test
    void orderedCollectionHasCollectionTypeWhenRetrievedFromRepository() {
        final OrderedTermCollection collection = sut.findOrdered(orderedCollectionB.getUri(), vocabulary);
        assertThat(collection.getTypes(), hasItem(SKOS.COLLECTION));

        final SkosCollectionDto dto = sut.findDto(orderedCollectionB.getUri());
        assertThat(dto.getTypes(), hasItems(SKOS.ORDERED_COLLECTION));
    }

    @Test
    void unorderedCollectionHasCollectionTypeWhenRetrievedFromRepository() {
        final Matcher<Iterable<? super String>> typeMatcher =
                allOf(hasItem(SKOS.COLLECTION), not(hasItem(SKOS.ORDERED_COLLECTION)));

        final TermCollection collection = sut.findUnordered(unorderedCollectionA.getUri(), vocabulary);
        assertThat(collection.getTypes(), typeMatcher);

        final SkosCollectionDto dto = sut.findDto(unorderedCollectionA.getUri());
        // DTO is SKOS Collection, the type of the entity is not included in types
        assertTrue(dto.getTypes().isEmpty());
    }
}
