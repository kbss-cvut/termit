package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
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
import org.springframework.data.domain.Sort;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

        final Page<SkosCollectionDto> result = sut.findAll(vocabulary, PageRequest.of(1, 2));

        assertEquals(2, result.getSize());
        result.forEach(dto -> assertThat(allCollections, hasItem(dto.getUri())));
        assertEquals(allCollections.size(), result.getTotalElements());
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

        final List<TypeAwareEntity> result = sut.findMembers(orderedCollection, Pageable.ofSize(100));

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
        final List<TypeAwareEntity> result = sut.findMembers(orderedCollection, PageRequest.of(1, 2));

        final Set<URI> expected = new HashSet<>(orderedCollection.getMembers().subList(2, 4));
        assertEquals(2, result.size());
        assertEquals(expected, result.stream().map(TypeAwareEntity::getUri).collect(Collectors.toSet()));
        result.forEach(e -> assertTrue(e.isTerm()));
    }

    @Test
    void findMembersReturnsRemainingMembersOnLastPartialPage() {
        final List<TypeAwareEntity> result = sut.findMembers(orderedCollection, PageRequest.of(2, 2));

        final List<URI> expected = orderedCollection.getMembers().subList(4, 5);
        assertEquals(expected, identifiers(result));
    }

    @Test
    void findMembersReturnsEmptyStreamForPageBeyondLastMember() {
        final List<TypeAwareEntity> result = sut.findMembers(orderedCollection, PageRequest.of(1, 10));

        assertTrue(result.isEmpty());
    }

    @Test
    void findMembersOfUnorderedCollectionReturnsDisjointPagesCoveringAllMembers() {
        final int pageSize = 2;
        final List<URI> found = new ArrayList<>();
        for (int page = 0; page * pageSize < unorderedMembers.size(); page++) {
            final TermCollection collection = sut.find(TermCollection.class, unorderedCollection.getUri(), vocabulary);
            sut.findMembers(collection, PageRequest.of(page, pageSize), Environment.LANGUAGE)
                    .forEach(e -> found.add(e.getUri()));
        }

        assertEquals(unorderedMembers.size(), found.size());
        assertEquals(new HashSet<>(identifiersFromEntity(unorderedMembers)), new HashSet<>(found));
    }

    private Term termWithLabel(String label) {
        return termWithLabel(MultilingualString.create(label, Environment.LANGUAGE));
    }

    private Term termWithLabel(MultilingualString label) {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        term.setLabel(label);
        return term;
    }

    private TermCollection persistUnorderedCollection(Collection<? extends HasIdentifier> members) {
        final TermCollection collection = Generator.generateTermCollection(vocabulary, List.of());
        members.forEach(m -> collection.addMember(m.getUri()));
        transactional(() -> {
            members.forEach(m -> em.persist(m, vocabularyDescriptor));
            em.persist(collection, vocabularyDescriptor);
        });
        return collection;
    }

    @Test
    void findMembersOfUnorderedCollectionSortsMembersByLabelAscendingByDefault() {
        final Term delta = termWithLabel("Delta");
        final Term alpha = termWithLabel("alpha");
        final Term ccep = termWithLabel("Čep");
        final Term cep = termWithLabel("Cep");
        final TermCollection bravo = Generator.generateTermCollection(vocabulary, List.of());
        bravo.setLabel(MultilingualString.create("Bravo", Environment.LANGUAGE));

        final TermCollection collection = persistUnorderedCollection(List.of(delta, alpha, ccep, bravo, cep));

        final List<TypeAwareEntity> result = sut.findMembers(collection, PageRequest.of(0, 10), Environment.LANGUAGE);

        assertEquals(identifiersFromEntity(List.of(alpha, bravo, cep, ccep, delta)), identifiers(result));
    }

    @Test
    void findMembersOfUnorderedCollectionSortsMembersByLabelDescendingWhenRequested() {
        final Term alpha = termWithLabel("Alpha");
        final Term beta = termWithLabel("Beta");
        final Term gamma = termWithLabel("Gamma");
        final TermCollection collection = persistUnorderedCollection(List.of(beta, gamma, alpha));

        final List<TypeAwareEntity> result = sut.findMembers(
                collection, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "label")), Environment.LANGUAGE);

        assertEquals(identifiersFromEntity(List.of(gamma, beta, alpha)), identifiers(result));
    }

    @Test
    void findMembersOfUnorderedCollectionPrioritizesMembersWithLabelInRequestedLanguage() {
        final String otherLanguage = "cs";
        assertNotEquals(Environment.LANGUAGE, otherLanguage);

        final Term alphaOther = termWithLabel(MultilingualString.create("Alpha", otherLanguage));
        final Term betaOther = termWithLabel(MultilingualString.create("Beta", otherLanguage));
        final Term yankee = termWithLabel("Yankee");
        final Term zulu = termWithLabel("Zulu");
        final TermCollection collection = persistUnorderedCollection(List.of(alphaOther, zulu, betaOther, yankee));

        final List<TypeAwareEntity> ascending =
                sut.findMembers(collection, PageRequest.of(0, 10), Environment.LANGUAGE);
        assertEquals(identifiersFromEntity(List.of(yankee, zulu, alphaOther, betaOther)), identifiers(ascending));

        final List<TypeAwareEntity> descending = sut.findMembers(
                collection, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "label")), Environment.LANGUAGE);
        assertEquals(identifiersFromEntity(List.of(zulu, yankee, betaOther, alphaOther)), identifiers(descending));
    }

    @Test
    void findMembersOfUnorderedCollectionUsesLabelInRequestedLanguageForSorting() {
        // the label in the requested language sorts the opposite way than the label in the other language
        final MultilingualString firstLabel = MultilingualString.create("Alpha", Environment.LANGUAGE);
        firstLabel.set("cs", "Zulu");
        final MultilingualString secondLabel = MultilingualString.create("Zulu", Environment.LANGUAGE);
        secondLabel.set("cs", "Alpha");
        final Term first = termWithLabel(firstLabel);
        final Term second = termWithLabel(secondLabel);
        final TermCollection collection = persistUnorderedCollection(List.of(second, first));

        assertEquals(
                identifiersFromEntity(List.of(first, second)),
                identifiers(sut.findMembers(collection, PageRequest.of(0, 10), Environment.LANGUAGE)));
        assertEquals(
                identifiersFromEntity(List.of(second, first)),
                identifiers(sut.findMembers(collection, PageRequest.of(0, 10), "cs")));
    }

    @Test
    void findMembersOfUnorderedCollectionReturnsSortedPage() {
        final List<Term> terms = Stream.of("Echo", "Alpha", "Delta", "Bravo", "Charlie")
                .map(this::termWithLabel)
                .toList();
        final TermCollection collection = persistUnorderedCollection(terms);

        final List<TypeAwareEntity> result = sut.findMembers(collection, PageRequest.of(1, 2), Environment.LANGUAGE);

        // Charlie, Delta
        assertEquals(identifiersFromEntity(List.of(terms.get(4), terms.get(2))), identifiers(result));
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
