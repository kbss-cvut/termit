package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.MatchType;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.dto.search.SearchResult;
import cz.cvut.kbss.termit.dto.search.SearchString;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cz.cvut.kbss.termit.environment.Environment.setPrimaryLabel;
import static cz.cvut.kbss.termit.environment.util.ContainsSameEntities.containsSameEntities;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchDaoAdvancedSearchTest extends BaseDaoTestRunner {
    private static final String[] TYPES = {"http://onto.fel.cvut.cz/ontologies/ufo/event",
                                           "http://onto.fel.cvut.cz/ontologies/ufo/object",
                                           "http://onto.fel.cvut.cz/ontologies/ufo/relator"};
    private static final String INTEGER_PROPERTY = cz.cvut.kbss.termit.util.Vocabulary.ONTOLOGY_IRI_TERMIT + "/custom-attribute/integerProperty";
    private static boolean initialized = false;
    private static User user;

    private static Vocabulary vocabulary;
    private static List<Term> terms;

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private SearchDao sut;

    @BeforeEach
    void setUp() {

        if (!initialized) {
            user = Generator.generateUserWithId();
            vocabulary = Generator.generateVocabularyWithId();
            transactional(() -> {
                em.persist(user);
                em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
                terms = generateTerms();
                terms.forEach(t -> em.persist(t, descriptorFactory.termDescriptor(t)));
            });
            initialized = true;
        }
        Environment.setCurrentUser(user);
    }

    private List<Term> generateTerms() {
        final List<Term> newTerms = new ArrayList<>(10);
        for (int i = 0; i < Generator.randomInt(5, 10); i++) {
            final boolean randomBool = Generator.randomBoolean();
            final Term term = new Term();
            term.setUri(Generator.generateUri());
            setPrimaryLabel(term, randomBool ? "Matching label " + i : "Unknown label " + i);
            vocabulary.getGlossary().addRootTerm(term);
            term.setVocabulary(vocabulary.getUri());
            if (randomBool) {
                term.setState(Generator.generateUri());
            }
            term.addType(TYPES[Generator.randomIndex(TYPES)]);
            term.setNotations(Set.of(String.valueOf(
                    Constants.LETTERS.charAt(Generator.randomInt(0, Constants.LETTERS.length())))));
            term.setExamples(Set.of(MultilingualString.create(randomBool ? "Matching" : "Unknown" + " example " + i,
                                                              Environment.LANGUAGE)));
            term.setProperties(new HashMap<>(Map.of(INTEGER_PROPERTY, Set.of(i))));
            newTerms.add(term);
        }
        newTerms.sort(Comparator.comparing(Environment::getPrimaryLabel));
        return newTerms;
    }


    @Test
    void advancedSearchReturnsTermsMatchingIriSearchParamWithSpecifiedTypes() {
        final SearchParam param = new SearchParam(URI.create(RDF.TYPE), Set.of(TYPES[0], TYPES[1]),
                                                  MatchType.IRI);
        final Page<SearchResult> result = sut.advancedSearch(new SearchString("", null), Set.of(param),
                                                             Constants.DEFAULT_PAGE_SPEC, Set.of(vocabulary.getUri()));
        assertFalse(result.isEmpty());
        assertTrue(result.stream().allMatch(r -> r.hasType(SKOS.CONCEPT)));
        final List<Term> expectedTerms = terms.stream()
                                              .filter(t -> t.hasType(TYPES[0]) || t.hasType(TYPES[1]))
                                              .toList();
        assertThat(result.getContent(), containsSameEntities(expectedTerms));
    }

    @Test
    void advancedSearchReturnsTermsMatchingExactMatchSearchParamWithSpecifiedValue() {
        final SearchParam param = new SearchParam(URI.create(SKOS.NOTATION),
                                                  Set.of(terms.get(0).getNotations().iterator().next()),
                                                  MatchType.EXACT_MATCH);
        final List<Term> matchingTerms = terms.stream()
                                              .filter(t -> !Collections.disjoint(t.getNotations(), param.getValue()))
                                              .toList();
        final Page<SearchResult> result = sut.advancedSearch(new SearchString("", null), Set.of(param),
                                                             Constants.DEFAULT_PAGE_SPEC, Set.of(vocabulary.getUri()));
        assertFalse(result.isEmpty());
        assertThat(result.getContent(), containsSameEntities(matchingTerms));
    }

    @Test
    void advancedSearchReturnsTermsMatchingSubstringSearchParamWithSpecifiedValue() {
        final Term sample = Generator.randomElement(terms);
        final String sampleValue = sample.getExamples().iterator().next().get().substring(0, 4);
        final SearchParam param = new SearchParam(URI.create(SKOS.EXAMPLE),
                                                  Set.of(sampleValue),
                                                  MatchType.SUBSTRING);
        final List<Term> matchingTerms = terms.stream()
                                              .filter(t -> t.getExamples().iterator().next().get()
                                                            .startsWith(sampleValue))
                                              .toList();
        final Page<SearchResult> result = sut.advancedSearch(new SearchString("", null), Set.of(param),
                                                             Constants.DEFAULT_PAGE_SPEC, Set.of(vocabulary.getUri()));
        assertFalse(result.isEmpty());
        assertThat(result.getContent(), containsSameEntities(matchingTerms));
    }

    @Test
    void advancedSearchReturnsResultsMatchingMultipleSearchParameters() {
        final SearchParam typeParam = new SearchParam(URI.create(RDF.TYPE), Set.of(TYPES[0], TYPES[1]),
                                                      MatchType.IRI);
        final SearchParam substringParam = new SearchParam(URI.create(SKOS.EXAMPLE),
                                                           Set.of("matching"),
                                                           MatchType.SUBSTRING);
        final List<Term> matchingTerms = terms.stream()
                                              .filter(t -> t.getExamples().iterator().next().get()
                                                            .startsWith("Matching") && (t.hasType(
                                                      TYPES[0]) || t.hasType(TYPES[1])))
                                              .toList();
        final Page<SearchResult> result = sut.advancedSearch(new SearchString("", null),
                                                             Set.of(typeParam, substringParam),
                                                             Constants.DEFAULT_PAGE_SPEC, Set.of(vocabulary.getUri()));
        assertThat(result.getContent(), containsSameEntities(matchingTerms));
    }

    @Test
    void advancedSearchReturnsMatchingPage() {
        final Pageable pageOne = PageRequest.of(0, terms.size() / 2);
        final Pageable pageTwo = PageRequest.of(1, terms.size() / 2);
        final SearchParam searchParam = new SearchParam(
                URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku),
                Set.of(vocabulary.getUri().toString()), MatchType.IRI);

        final Page<SearchResult> resultOne = sut.advancedSearch(new SearchString("", null), Set.of(searchParam),
                                                                pageOne, Set.of(vocabulary.getUri()));
        assertEquals(terms.size() / 2, resultOne.getNumberOfElements());
        final Page<SearchResult> resultTwo = sut.advancedSearch(new SearchString("", null), Set.of(searchParam),
                                                                pageTwo, Set.of(vocabulary.getUri()));
        assertEquals(terms.size() / 2, resultTwo.getNumberOfElements());
        assertThat(resultOne.getContent(), not(containsSameEntities(resultTwo.getContent())));
    }

    @Test
    void advancedSearchHandlesCorrectlyDatatypesForExactMatch() {
        final Term expected = Generator.randomElement(terms);
        final Integer paramValue = (Integer) expected.getProperties().get(INTEGER_PROPERTY).iterator().next();

        final SearchParam searchParam = new SearchParam(
                URI.create(INTEGER_PROPERTY), Set.of(paramValue), MatchType.EXACT_MATCH
        );

        final Page<SearchResult> result = sut.advancedSearch(new SearchString("", null), Set.of(searchParam),
                                                             Constants.DEFAULT_PAGE_SPEC, Set.of(vocabulary.getUri()));
        assertEquals(1, result.getNumberOfElements());
        assertEquals(expected.getUri(), result.getContent().get(0).getUri());
    }

    @Test
    void advancedSearchReturnsEmptyListWhenSearchStringIsBlankAndNoSearchParams() {
        final Page<SearchResult> result = sut.advancedSearch(new SearchString("", null), Set.of(),
                                                             Constants.DEFAULT_PAGE_SPEC, Set.of(vocabulary.getUri()));
        assertTrue(result.isEmpty());
    }

    @Test
    void advancedSearchHandlesRdfNilAsExplicitEmptyValueCondition() {
        final List<Term> withValue = terms.subList(0, terms.size() / 2);
        final List<Term> withoutValue = terms.subList(terms.size() / 2, terms.size());
        final String property = URI.create(
                                           cz.cvut.kbss.termit.util.Vocabulary.ONTOLOGY_IRI_TERMIT + "/custom-attribute/booleanProperty")
                                   .toString();
        transactional(() -> withValue.forEach(t -> {
            t.getProperties().put(property, Set.of(Generator.randomBoolean()));
            em.merge(t, descriptorFactory.termDescriptor(t));
        }));
        final Page<SearchResult> result = sut.advancedSearch(new SearchString("", null),
                                                             Set.of(new SearchParam(URI.create(property),
                                                                                    Set.of(RDF.NIL),
                                                                                    MatchType.IRI),
                                                                    new SearchParam(URI.create(RDF.TYPE),
                                                                                    Set.of(SKOS.CONCEPT),
                                                                                    MatchType.IRI)),
                                                             Constants.DEFAULT_PAGE_SPEC, Set.of(vocabulary.getUri()));
        assertThat(result.getContent(), containsSameEntities(withoutValue));
    }

    @Test
    void advancedSearchHandlesRdfNilForAssetType() {
        final List<Term> withoutType = terms.subList(0, terms.size() / 2);
        transactional(() -> withoutType.forEach(t -> {
            t.setTypes(Set.of());
            em.merge(t, descriptorFactory.termDescriptor(t));
        }));

        final Page<SearchResult> result = sut.advancedSearch(new SearchString("", null),
                                                             Set.of(new SearchParam(URI.create(RDF.TYPE),
                                                                                    Set.of(RDF.NIL),
                                                                                    MatchType.IRI),
                                                                    new SearchParam(URI.create(RDF.TYPE),
                                                                                    Set.of(SKOS.CONCEPT),
                                                                                    MatchType.IRI)),
                                                             Constants.DEFAULT_PAGE_SPEC, Set.of(vocabulary.getUri()));
        assertThat(result.getContent(), containsSameEntities(withoutType));
    }

    @Test
    void advancedSearchUnifiesMultilingualLabelsIntoOneResult() {
        final Term multilingual = new Term(Generator.generateUri());
        multilingual.setLabel(MultilingualString.create("Matching label of multilingual term", Environment.LANGUAGE));
        multilingual.getLabel().set("cs", "Český název pojmu");
        multilingual.setVocabulary(vocabulary.getUri());
        multilingual.setTypes(Set.of(TYPES[0]));
        transactional(() -> em.persist(multilingual, descriptorFactory.termDescriptor(multilingual)));

        try {
            final SearchParam p = new SearchParam(URI.create(RDF.TYPE), Set.of(TYPES[0]), MatchType.IRI);
            final Page<SearchResult> result = sut.advancedSearch(new SearchString("", null), Set.of(p),
                                                                 Constants.DEFAULT_PAGE_SPEC,
                                                                 Set.of(vocabulary.getUri()));
            final Optional<SearchResult> matching = result.stream()
                                                          .filter(t -> t.getUri().equals(multilingual.getUri()))
                                                          .findFirst();
            assertTrue(matching.isPresent());
            assertEquals(multilingual.getLabel(), matching.get().getLabel());
        } finally {
            transactional(() -> em.remove(em.getReference(Term.class, multilingual.getUri())));
        }
    }

    @Test
    void advancedSearchWithoutSearchStringResolvesTotalNumberOfResults() {
        final Pageable pageSpec = PageRequest.of(0, terms.size() / 2);
        final Page<SearchResult> result = sut.advancedSearch(new SearchString("", null),
                                                             Set.of(new SearchParam(URI.create(RDF.TYPE),
                                                                                    Set.of(SKOS.CONCEPT),
                                                                                    MatchType.IRI)),
                                                             pageSpec,
                                                             Set.of(vocabulary.getUri()));
        assertEquals(pageSpec.getPageSize(), result.getNumberOfElements());
        assertEquals(terms.size(), result.getTotalElements());
    }
}
