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
import cz.cvut.kbss.termit.model.collection.SkosCollection;
import cz.cvut.kbss.termit.model.collection.SkosCollection_;
import cz.cvut.kbss.termit.model.collection.TermCollection;
import cz.cvut.kbss.termit.model.collection.TermCollection_;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkosCollectionDaoTest extends BaseDaoTestRunner {
    private static final Pageable PAGEABLE_ALL = PageRequest.ofSize(Integer.MAX_VALUE);

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

    private static List<URI> identifiers(Collection<? extends HasIdentifier> collection) {
        return new ArrayList<>(collection.stream().map(HasIdentifier::getUri).toList());
    }

    /** Creates a term in the {@link #vocabulary} with the label in the vocabulary primary language. */
    private Term termWithLabel(String label) {
        return termWithLabel(vocabulary, label);
    }

    /** Creates a term in the {@link #vocabulary} with the specified label. */
    private Term termWithLabel(MultilingualString label) {
        return termWithLabel(vocabulary, label);
    }

    /** Creates a term in the vocabulary with the label in the vocabulary primary language. */
    private static Term termWithLabel(Vocabulary vocabulary, String label) {
        return termWithLabel(vocabulary, MultilingualString.create(label, vocabulary.getPrimaryLanguage()));
    }

    private static Term termWithLabel(Vocabulary vocabulary, MultilingualString label) {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        term.setLabel(label);
        return term;
    }

    /** Creates terms in the vocabulary with the labels in the vocabulary primary language, in the order of labels. */
    private static List<Term> termsWithLabels(Vocabulary vocabulary, String... labels) {
        return Stream.of(labels).map(label -> termWithLabel(vocabulary, label)).toList();
    }

    /** Creates an empty collection in the vocabulary with the label in the vocabulary primary language. */
    private static TermCollection collectionWithLabel(Vocabulary vocabulary, String label) {
        return collectionWithLabel(vocabulary, MultilingualString.create(label, vocabulary.getPrimaryLanguage()));
    }

    private static TermCollection collectionWithLabel(Vocabulary vocabulary, MultilingualString label) {
        final TermCollection collection = Generator.generateTermCollection(vocabulary, List.of());
        collection.setLabel(label);
        return collection;
    }

    /** Persists the vocabulary along with the specified entities into the vocabulary context. */
    private void persistVocabulary(Vocabulary vocabulary, Collection<? extends HasIdentifier> entities) {
        final Descriptor descriptor = descriptorFactory.vocabularyDescriptor(vocabulary);
        transactional(() -> {
            vocabularyDao.persist(vocabulary);
            entities.forEach(e -> em.persist(e, descriptor));
        });
    }

    @Test
    void findAllReturnsAllCollectionsInVocabulary() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final TermCollection unrelated = Generator.generateTermCollection(anotherVocabulary);
        persistVocabulary(anotherVocabulary, List.of(unrelated));

        final List<SkosCollectionDto> result =
                sut.findAll(vocabulary, Pageable.ofSize(10)).getContent();

        assertEquals(2, result.size());
        final List<URI> identifiers = identifiers(result);
        assertTrue(identifiers.contains(unorderedCollection.getUri()));
        assertTrue(identifiers.contains(orderedCollection.getUri()));
    }

    @Test
    void findAllRespectsPagination() {
        final List<TermCollection> additional = Stream.generate(() -> Generator.generateTermCollection(vocabulary))
                .limit(3)
                .toList();

        transactional(() -> additional.forEach(c -> em.persist(c, vocabularyDescriptor)));

        final Set<URI> allCollections = new HashSet<>(identifiers(additional));
        allCollections.add(unorderedCollection.getUri());
        allCollections.add(orderedCollection.getUri());

        final Page<SkosCollectionDto> result = sut.findAll(vocabulary, PageRequest.of(1, 2));

        assertEquals(2, result.getSize());
        assertThat(result).map(SkosCollectionDto::getUri).isSubsetOf(allCollections);
        assertEquals(allCollections.size(), result.getTotalElements());
    }

    @Test
    void findAllReturnsDisjointPagesCoveringAllCollections() {
        final List<TermCollection> additional = Stream.generate(() -> Generator.generateTermCollection(vocabulary))
                .limit(3)
                .toList();
        transactional(() -> additional.forEach(c -> em.persist(c, vocabularyDescriptor)));

        final Set<URI> allCollections = new HashSet<>(identifiers(additional));
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

    private List<SkosCollectionDto> generateCollectionsWithLabelsAndFindAll(
            Pageable pageable, MultilingualString... labels) {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final List<TermCollection> collections = Stream.of(labels)
                .map(label -> collectionWithLabel(anotherVocabulary, label))
                .toList();
        persistVocabulary(anotherVocabulary, collections);
        return sut.findAll(anotherVocabulary, pageable).getContent();
    }

    private static List<String> labels(List<SkosCollectionDto> collections) {
        return collections.stream()
                .map(c -> c.getLabel().get(Environment.LANGUAGE))
                .toList();
    }

    @Test
    void findAllSortsCollectionsByLabelInVocabularyPrimaryLanguage() {
        final String otherLanguage = "cs";
        assertNotEquals(Environment.LANGUAGE, otherLanguage);

        // label in the primary language sorts the opposite way than the label in the other language
        final MultilingualString zulu = MultilingualString.create("Zulu", Environment.LANGUAGE);
        zulu.set(otherLanguage, "Alpha");
        final List<SkosCollectionDto> result = generateCollectionsWithLabelsAndFindAll(
                PageRequest.of(0, 10),
                MultilingualString.create("Delta", Environment.LANGUAGE),
                zulu,
                MultilingualString.create("alpha", Environment.LANGUAGE),
                MultilingualString.create("Čep", Environment.LANGUAGE),
                MultilingualString.create("Cep", Environment.LANGUAGE));

        assertEquals(List.of("alpha", "Cep", "Čep", "Delta", "Zulu"), labels(result));
    }

    @Test
    void findAllSortsCollectionsByLabelDescendingWhenRequested() {
        final List<SkosCollectionDto> result = generateCollectionsWithLabelsAndFindAll(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "label")),
                MultilingualString.create("Beta", Environment.LANGUAGE),
                MultilingualString.create("Gamma", Environment.LANGUAGE),
                MultilingualString.create("Alpha", Environment.LANGUAGE));

        assertEquals(List.of("Gamma", "Beta", "Alpha"), labels(result));
    }

    @Test
    void findDtoReturnsDtoOfOrderedCollection() {
        final SkosCollectionDto expectedDto = Environment.getDtoMapper().skosCollectionToDto(orderedCollection);

        final SkosCollectionDto result = sut.findDto(orderedCollection.getUri());
        assertEquals(expectedDto, result);
    }

    @Test
    void findDtoReturnsDtoOfUnorderedCollection() {
        final SkosCollectionDto expectedDto = Environment.getDtoMapper().skosCollectionToDto(unorderedCollection);

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

        result.forEach(e -> {
            assertFalse(e.getTypes().isEmpty());
            if (e.getUri().equals(unorderedCollection.getUri())) {
                assertTrue(e.isCollection());
                assertFalse(e.isTerm());
            } else {
                assertThat(e.getUri()).isIn(orderedCollection.getMembers());
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
        assertThat(identifiers(result)).containsAll(expected);
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
        assertThat(found).containsAll(unorderedCollection.getMembers());
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
        final TermCollection bravo = collectionWithLabel(vocabulary, "Bravo");

        final TermCollection collection = persistUnorderedCollection(List.of(delta, alpha, ccep, bravo, cep));

        final List<TypeAwareEntity> result = sut.findMembers(collection, PageRequest.of(0, 10), Environment.LANGUAGE);

        assertEquals(identifiers(List.of(alpha, bravo, cep, ccep, delta)), identifiers(result));
    }

    @Test
    void findMembersOfUnorderedCollectionSortsMembersByLabelDescendingWhenRequested() {
        final Term alpha = termWithLabel("Alpha");
        final Term beta = termWithLabel("Beta");
        final Term gamma = termWithLabel("Gamma");
        final TermCollection collection = persistUnorderedCollection(List.of(beta, gamma, alpha));

        final List<TypeAwareEntity> result = sut.findMembers(
                collection, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "label")), Environment.LANGUAGE);

        assertEquals(identifiers(List.of(gamma, beta, alpha)), identifiers(result));
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
        assertEquals(identifiers(List.of(yankee, zulu, alphaOther, betaOther)), identifiers(ascending));

        final List<TypeAwareEntity> descending = sut.findMembers(
                collection, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "label")), Environment.LANGUAGE);
        assertEquals(identifiers(List.of(zulu, yankee, betaOther, alphaOther)), identifiers(descending));
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
                identifiers(List.of(first, second)),
                identifiers(sut.findMembers(collection, PageRequest.of(0, 10), Environment.LANGUAGE)));
        assertEquals(
                identifiers(List.of(second, first)),
                identifiers(sut.findMembers(collection, PageRequest.of(0, 10), "cs")));
    }

    @Test
    void findMembersOfUnorderedCollectionReturnsSortedPage() {
        final List<Term> terms = termsWithLabels(vocabulary, "Alpha", "Bravo", "Charlie", "Delta", "Echo");
        final TermCollection collection = persistUnorderedCollection(terms.reversed());

        final List<TypeAwareEntity> result = sut.findMembers(collection, PageRequest.of(1, 2), Environment.LANGUAGE);

        assertEquals(identifiers(terms.subList(2, 4)), identifiers(result));
    }

    @Test
    void orderedCollectionHasCollectionTypeWhenRetrievedFromRepository() {
        final OrderedTermCollection collection =
                sut.find(OrderedTermCollection.class, orderedCollection.getUri(), vocabulary);
        assertThat(collection.getTypes()).contains(SKOS.COLLECTION);

        final SkosCollectionDto dto = sut.findDto(orderedCollection.getUri());
        assertThat(dto.getTypes()).contains(SKOS.ORDERED_COLLECTION);
    }

    @Test
    void unorderedCollectionHasNoAdditionalTypeWhenRetrievedFromRepository() {
        // the type of the entity itself is not included in types field

        final TermCollection collection = sut.find(TermCollection.class, unorderedCollection.getUri(), vocabulary);
        assertTrue(collection.getTypes().isEmpty());

        final SkosCollectionDto dto = sut.findDto(unorderedCollection.getUri());
        assertTrue(dto.getTypes().isEmpty());
    }

    @Test
    void findReturnsNullWhenCollectionBelongsToDifferentVocabulary() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        transactional(() -> vocabularyDao.persist(anotherVocabulary));

        assertNull(sut.find(TermCollection.class, unorderedCollection.getUri(), anotherVocabulary));
    }

    @Test
    void persistPersistsCollectionIntoVocabularyContext() {
        final TermCollection collection = Generator.generateTermCollection(vocabulary, unorderedMembers);

        transactional(() -> sut.persist(collection, vocabulary));

        assertTrue(em.createNativeQuery("ASK { GRAPH ?ctx { ?x a ?type ; ?hasMember ?member . } }", Boolean.class)
                .setParameter("ctx", vocabulary.getUri())
                .setParameter("x", collection.getUri())
                .setParameter("type", URI.create(SKOS.COLLECTION))
                .setParameter("hasMember", TermCollection_.membersPropertyIRI.toURI())
                .setParameter("member", unorderedMembers.getFirst().getUri())
                .getSingleResult());
    }

    @Test
    void updateMergesCollectionIntoVocabularyContext() {
        final String oldLabel = orderedCollection.getLabel().get(Environment.LANGUAGE);
        final String updatedLabel = "Updated label";
        orderedCollection.getLabel().set(Environment.LANGUAGE, updatedLabel);

        transactional(() -> sut.update(orderedCollection, vocabulary));

        // assert updated label in correct graph
        assertTrue(em.createNativeQuery("ASK { GRAPH ?ctx { ?x ?hasLabel ?label . } }", Boolean.class)
                .setParameter("ctx", vocabulary.getUri())
                .setParameter("x", orderedCollection.getUri())
                .setParameter("hasLabel", SkosCollection_.labelPropertyIRI.toURI())
                .setParameter("label", updatedLabel, Environment.LANGUAGE)
                .getSingleResult());

        // assert old label removed
        assertFalse(em.createNativeQuery("ASK { ?x ?hasLabel ?label . }", Boolean.class)
                .setParameter("x", orderedCollection.getUri())
                .setParameter("hasLabel", SkosCollection_.labelPropertyIRI.toURI())
                .setParameter("label", oldLabel, Environment.LANGUAGE)
                .getSingleResult());
    }

    @Test
    void existsReturnsTrueWhenUnorderedCollectionExists() {
        assertTrue(sut.exists(unorderedCollection.getUri()));
    }

    @Test
    void existsReturnsTrueWhenOrderedCollectionExists() {
        assertTrue(sut.exists(orderedCollection.getUri()));
    }

    @Test
    void existsReturnsFalseWhenCollectionDoesNotExist() {
        assertFalse(sut.exists(Generator.generateUri()));
    }

    @Test
    void updateUpdatesCollectionWithNewLabel() {
        final String updatedLabel = "Updated label";
        orderedCollection.getLabel().set(Environment.LANGUAGE, updatedLabel);

        transactional(() -> sut.update(orderedCollection, vocabulary));

        OrderedTermCollection updated = readOnlyTransactional(
                () -> sut.find(OrderedTermCollection.class, orderedCollection.getUri(), vocabulary));
        assertEquals(updatedLabel, updated.getLabel().get(Environment.LANGUAGE));
    }

    @Test
    void updateUpdatesCollectionDtoWithNewLabel() {
        final String updatedLabel = "Updated label";
        orderedCollection.getLabel().set(Environment.LANGUAGE, updatedLabel);

        transactional(() -> sut.update(orderedCollection, vocabulary));

        SkosCollectionDto updated = readOnlyTransactional(() -> sut.findDto(orderedCollection.getUri()));
        assertEquals(updatedLabel, updated.getLabel().get(Environment.LANGUAGE));
    }

    @Test
    void removeRemovesUnorderedCollectionFromVocabularyContext() {
        transactional(() -> {
            SkosCollection<?> collection = sut.find(TermCollection.class, unorderedCollection.getUri(), vocabulary);
            sut.remove(collection, vocabulary);
        });

        assertFalse(sut.exists(unorderedCollection.getUri()));
        assertFalse(em.createNativeQuery("ASK { GRAPH ?ctx { ?x ?y ?z . } }", Boolean.class)
                .setParameter("ctx", vocabulary.getUri())
                .setParameter("x", unorderedCollection.getUri())
                .getSingleResult());
    }

    @Test
    void removeRemovesOrderedCollectionFromVocabularyContext() {
        transactional(() -> {
            SkosCollection<?> collection =
                    sut.find(OrderedTermCollection.class, orderedCollection.getUri(), vocabulary);
            sut.remove(collection, vocabulary);
        });

        assertFalse(sut.exists(orderedCollection.getUri()));
        assertFalse(em.createNativeQuery("ASK { GRAPH ?ctx { ?x ?y ?z . } }", Boolean.class)
                .setParameter("ctx", vocabulary.getUri())
                .setParameter("x", orderedCollection.getUri())
                .getSingleResult());
    }

    @Test
    void removeEvictsCollectionDtoFromCache() {
        // load the DTO into the cache
        final SkosCollectionDto cached = readOnlyTransactional(() -> sut.findDto(orderedCollection.getUri()));
        assertEquals(orderedCollection.getUri(), cached.getUri());

        transactional(() -> {
            SkosCollection<?> collection =
                    sut.find(OrderedTermCollection.class, orderedCollection.getUri(), vocabulary);
            sut.remove(collection, vocabulary);
        });

        assertNull(readOnlyTransactional(() -> sut.findDto(orderedCollection.getUri())));
    }

    private Page<TypeAwareEntity> findAddMemberOptions(
            SkosCollection<?> collection, Vocabulary vocabulary, Pageable pageable) {
        return findAddMemberOptions(collection, vocabulary, null, pageable);
    }

    private Page<TypeAwareEntity> findAddMemberOptions(
            SkosCollection<?> collection, Vocabulary vocabulary, String searchString, Pageable pageable) {
        return readOnlyTransactional(
                () -> sut.findAddMemberOptions(collection.getUri(), vocabulary, searchString, pageable));
    }

    private List<URI> findAddMemberOptionIdentifiers(
            SkosCollection<?> collection, Vocabulary vocabulary, Pageable pageable) {
        return identifiers(
                findAddMemberOptions(collection, vocabulary, pageable).getContent());
    }

    /** Members that are not contained in {@link #unorderedCollection} */
    private Set<URI> membersNotInUnorderedCollection() {
        final Set<URI> options = new HashSet<>(orderedCollection.getMembers());
        options.add(orderedCollection.getUri());
        return options;
    }

    /**
     * Persists the vocabulary with the specified entities and an empty collection into the vocabulary context.
     *
     * @return the empty collection
     */
    private TermCollection persistVocabularyWithEmptyCollection(
            Vocabulary vocabulary, Collection<? extends HasIdentifier> entities) {
        final TermCollection collection = Generator.generateTermCollection(vocabulary, List.of());
        persistVocabulary(
                vocabulary,
                Stream.concat(entities.stream(), Stream.of(collection)).toList());
        return collection;
    }

    @Test
    void findAddMemberOptionsReturnsTermsAndCollectionsFromTheSameVocabulary() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final List<Term> anotherTerms = termsWithLabels(anotherVocabulary, "Alpha", "Bravo", "Charlie");
        final TermCollection anotherCollection = collectionWithLabel(anotherVocabulary, "Delta");
        persistVocabulary(
                anotherVocabulary,
                Stream.concat(anotherTerms.stream(), Stream.of(anotherCollection))
                        .toList());

        final List<Term> additionalTerms = termsWithLabels(vocabulary, "Echo", "Foxtrot");
        transactional(() -> additionalTerms.forEach(t -> em.persist(t, vocabularyDescriptor)));

        final Page<TypeAwareEntity> result = findAddMemberOptions(orderedCollection, vocabulary, PAGEABLE_ALL);

        final List<URI> expectedAddableMembers = Stream.of(
                        Set.of(unorderedCollection.getUri()),
                        unorderedCollection.getMembers(),
                        identifiers(additionalTerms))
                .flatMap(Collection::stream)
                .toList();

        assertEquals(expectedAddableMembers.size(), result.getNumberOfElements());
        assertThat(identifiers(result.getContent())).isSubsetOf(expectedAddableMembers);
    }

    @Test
    void findAddMemberOptionsExcludesMembersOfUnorderedCollection() {
        final List<URI> result = findAddMemberOptionIdentifiers(unorderedCollection, vocabulary, PAGEABLE_ALL);

        assertThat(result).doesNotContainAnyElementsOf(unorderedCollection.getMembers());
        assertTrue(result.containsAll(orderedCollection.getMembers()));
    }

    @Test
    void findAddMemberOptionsExcludesMembersOfOrderedCollection() {
        final List<URI> result = findAddMemberOptionIdentifiers(orderedCollection, vocabulary, PAGEABLE_ALL);

        assertThat(result).doesNotContainAnyElementsOf(orderedCollection.getMembers());
        assertTrue(result.containsAll(unorderedCollection.getMembers()));
    }

    @Test
    void findAddMemberOptionsExcludesCollectionItself() {
        final List<URI> result = findAddMemberOptionIdentifiers(unorderedCollection, vocabulary, PAGEABLE_ALL);

        assertThat(result).doesNotContain(unorderedCollection.getUri()).contains(orderedCollection.getUri());
    }

    @Test
    void findAddMemberOptionsExcludesCollectionAlreadyBeingMember() {
        final TermCollection nested = Generator.generateTermCollection(vocabulary, List.of());
        final TermCollection collection = persistUnorderedCollection(List.of(nested));

        final List<URI> result = findAddMemberOptionIdentifiers(collection, vocabulary, PAGEABLE_ALL);

        assertThat(result)
                .doesNotContain(nested.getUri())
                .contains(unorderedCollection.getUri(), orderedCollection.getUri());
    }

    @Test
    void findAddMemberOptionsSortsTermsByLabelAscendingByDefault() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = termsWithLabels(anotherVocabulary, "Alpha", "Bravo", "Charlie", "Delta");
        final TermCollection collection = persistVocabularyWithEmptyCollection(anotherVocabulary, terms.reversed());

        final List<URI> result = findAddMemberOptionIdentifiers(collection, anotherVocabulary, PAGEABLE_ALL);

        assertEquals(identifiers(terms), result);
    }

    @Test
    void findAddMemberOptionsSortsTermsByLabelDescendingWhenRequested() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final Term alpha = termWithLabel(anotherVocabulary, "Alpha");
        final Term bravo = termWithLabel(anotherVocabulary, "Bravo");
        final Term charlie = termWithLabel(anotherVocabulary, "Charlie");
        // would be the last one if it was not placed before terms
        final TermCollection alphaCollection = collectionWithLabel(anotherVocabulary, "Alpha");
        final TermCollection collection = persistVocabularyWithEmptyCollection(
                anotherVocabulary, List.of(alpha, bravo, alphaCollection, charlie));

        final List<URI> result = findAddMemberOptionIdentifiers(
                collection, anotherVocabulary, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "label")));

        assertEquals(identifiers(List.of(alphaCollection, charlie, bravo, alpha)), result);
    }

    @Test
    void findAddMemberOptionsSortsCollectionsByLabel() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final Term alpha = termWithLabel(anotherVocabulary, "Alpha");
        final Term bravo = termWithLabel(anotherVocabulary, "Bravo");
        // collection labels sort after term labels
        final TermCollection yankee = collectionWithLabel(anotherVocabulary, "Yankee");
        final TermCollection zulu = collectionWithLabel(anotherVocabulary, "Zulu");
        final TermCollection collection =
                persistVocabularyWithEmptyCollection(anotherVocabulary, List.of(bravo, zulu, alpha, yankee));

        final List<URI> result = findAddMemberOptionIdentifiers(collection, anotherVocabulary, PAGEABLE_ALL);

        assertEquals(identifiers(List.of(yankee, zulu, alpha, bravo)), result);
    }

    @Test
    void findAddMemberOptionsUsesLabelInVocabularyPrimaryLanguage() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final String primaryLanguage = anotherVocabulary.getPrimaryLanguage();
        final String otherLanguage = "cs";
        assertNotEquals(otherLanguage, primaryLanguage);

        // the label in the primary language sorts the opposite way than the label in the other language
        final MultilingualString firstLabel = MultilingualString.create("Alpha", primaryLanguage);
        firstLabel.set(otherLanguage, "Zulu");
        final MultilingualString secondLabel = MultilingualString.create("Zulu", primaryLanguage);
        secondLabel.set(otherLanguage, "Alpha");
        final Term first = termWithLabel(anotherVocabulary, firstLabel);
        final Term second = termWithLabel(anotherVocabulary, secondLabel);
        final TermCollection collection =
                persistVocabularyWithEmptyCollection(anotherVocabulary, List.of(second, first));

        final List<URI> result = findAddMemberOptionIdentifiers(collection, anotherVocabulary, PAGEABLE_ALL);

        assertEquals(identifiers(List.of(first, second)), result);
    }

    @Test
    void findAddMemberOptionsReturnsEntitiesWithResolvedTypes() {
        final List<TypeAwareEntity> result = findAddMemberOptions(unorderedCollection, vocabulary, PAGEABLE_ALL)
                .getContent();

        assertEquals(membersNotInUnorderedCollection(), new HashSet<>(identifiers(result)));
        result.forEach(e -> {
            if (e.getUri().equals(orderedCollection.getUri())) {
                assertTrue(e.isCollection());
                assertTrue(e.isOrderedCollection());
                assertFalse(e.isTerm());
            } else {
                assertTrue(e.isTerm());
                assertFalse(e.isCollection());
            }
        });
    }

    @Test
    void findAddMemberOptionsRespectsPagination() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = termsWithLabels(anotherVocabulary, "Alpha", "Bravo", "Charlie", "Delta", "Echo");
        final TermCollection collection = persistVocabularyWithEmptyCollection(anotherVocabulary, terms.reversed());

        final List<URI> result = findAddMemberOptionIdentifiers(collection, anotherVocabulary, PageRequest.of(1, 2));

        assertEquals(identifiers(terms.subList(2, 4)), result);
    }

    @Test
    void findAddMemberOptionsReturnsDisjointPagesCoveringAllOptions() {
        final Set<URI> options = membersNotInUnorderedCollection();

        final int pageSize = 4;
        final List<URI> found = new ArrayList<>();
        for (int page = 0; page * pageSize < options.size(); page++) {
            found.addAll(
                    findAddMemberOptionIdentifiers(unorderedCollection, vocabulary, PageRequest.of(page, pageSize)));
        }

        assertEquals(options.size(), found.size());
        assertThat(found).containsAll(options);
    }

    @Test
    void findAddMemberOptionsReturnsTotalCountOfAllOptions() {
        final int pageSize = 2;

        final Page<TypeAwareEntity> result =
                findAddMemberOptions(unorderedCollection, vocabulary, PageRequest.ofSize(pageSize));

        assertEquals(pageSize, result.getNumberOfElements());
        assertEquals(membersNotInUnorderedCollection().size(), result.getTotalElements());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void findAddMemberOptionsReturnsEmptyPageWhenAllEntitiesAreMembers() {
        unorderedCollection.getMembers().addAll(membersNotInUnorderedCollection());
        transactional(() -> sut.update(unorderedCollection, vocabulary));

        final Page<TypeAwareEntity> result = findAddMemberOptions(unorderedCollection, vocabulary, PAGEABLE_ALL);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void findAddMemberOptionsReturnsOnlyTermsAndCollectionsWithLabelContainingSearchString() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = termsWithLabels(anotherVocabulary, "Main street", "Side street", "Park");
        final TermCollection streetCollection = collectionWithLabel(anotherVocabulary, "Streets");
        final TermCollection parkCollection = collectionWithLabel(anotherVocabulary, "Parks");
        final TermCollection collection = persistVocabularyWithEmptyCollection(
                anotherVocabulary,
                Stream.concat(terms.stream(), Stream.of(streetCollection, parkCollection))
                        .toList());

        final Page<TypeAwareEntity> result =
                findAddMemberOptions(collection, anotherVocabulary, "street", PAGEABLE_ALL);

        assertEquals(
                identifiers(List.of(streetCollection, terms.get(0), terms.get(1))), identifiers(result.getContent()));
    }

    @Test
    void findAddMemberOptionsMatchesSearchStringIgnoringCase() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = termsWithLabels(anotherVocabulary, "Alpha", "ALPHABET", "Bravo");
        final TermCollection collection = persistVocabularyWithEmptyCollection(anotherVocabulary, terms);

        final Page<TypeAwareEntity> result = findAddMemberOptions(collection, anotherVocabulary, "aLpHa", PAGEABLE_ALL);

        // alpha and alphabet
        assertEquals(identifiers(terms.subList(0, 2)), identifiers(result.getContent()));
    }

    @Test
    void findAddMemberOptionsMatchesSearchStringOnlyInVocabularyPrimaryLanguageLabel() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final String primaryLanguage = anotherVocabulary.getPrimaryLanguage();
        final String otherLanguage = "cs";
        assertNotEquals(otherLanguage, primaryLanguage);

        final MultilingualString matchingLabel = MultilingualString.create("Street", primaryLanguage);
        matchingLabel.set(otherLanguage, "Ulice");
        final MultilingualString nonMatchingLabel = MultilingualString.create("Road", primaryLanguage);
        nonMatchingLabel.set(otherLanguage, "Street");
        final Term matching = termWithLabel(anotherVocabulary, matchingLabel);
        final Term nonMatching = termWithLabel(anotherVocabulary, nonMatchingLabel);
        final TermCollection collection =
                persistVocabularyWithEmptyCollection(anotherVocabulary, List.of(matching, nonMatching));

        final Page<TypeAwareEntity> result =
                findAddMemberOptions(collection, anotherVocabulary, "street", PAGEABLE_ALL);

        assertEquals(identifiers(List.of(matching)), identifiers(result.getContent()));
    }

    @Test
    void findAddMemberOptionsReturnsTotalCountOfOptionsMatchingSearchString() {
        final Vocabulary anotherVocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = termsWithLabels(anotherVocabulary, "Street 1", "Street 2", "Street 3", "Park");
        final TermCollection collection = persistVocabularyWithEmptyCollection(anotherVocabulary, terms);
        final int pageSize = 2;

        final Page<TypeAwareEntity> result =
                findAddMemberOptions(collection, anotherVocabulary, "street", PageRequest.ofSize(pageSize));

        assertEquals(pageSize, result.getNumberOfElements());
        assertEquals(3, result.getTotalElements());
    }

    @Test
    void findAddMemberOptionsReturnsAllOptionsWhenSearchStringIsEmpty() {
        final Page<TypeAwareEntity> result = findAddMemberOptions(unorderedCollection, vocabulary, "", PAGEABLE_ALL);

        assertEquals(membersNotInUnorderedCollection(), new HashSet<>(identifiers(result.getContent())));
        assertEquals(membersNotInUnorderedCollection().size(), result.getTotalElements());
    }

    @Test
    void findAddMemberOptionsReturnsEmptyPageWhenNoLabelContainsSearchString() {
        final Page<TypeAwareEntity> result =
                findAddMemberOptions(unorderedCollection, vocabulary, "no label contains this string", PAGEABLE_ALL);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }
}
