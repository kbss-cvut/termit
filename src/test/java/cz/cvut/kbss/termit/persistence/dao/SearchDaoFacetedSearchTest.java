package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FacetedSearchResult;
import cz.cvut.kbss.termit.dto.search.MatchType;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.util.Constants;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.environment.Environment.setPrimaryLabel;
import static cz.cvut.kbss.termit.environment.util.ContainsSameEntities.containsSameEntities;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SearchDaoFacetedSearchTest extends BaseDaoTestRunner{
    private static final String[] TYPES = {"http://onto.fel.cvut.cz/ontologies/ufo/event",
                                           "http://onto.fel.cvut.cz/ontologies/ufo/object",
                                           "http://onto.fel.cvut.cz/ontologies/ufo/relator"};
    private static boolean initialized = false;
    private static User user;

    private static Vocabulary vocabulary;
    private static List<Term> terms;
    private static List<Vocabulary> vocabularies;

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    private LuceneSearchDao sut;

    @BeforeEach
    void setUp() {
        sut = new LuceneSearchDao(em);

        if (!initialized) {
            user = Generator.generateUserWithId();
            vocabulary = Generator.generateVocabularyWithId();
            transactional(() -> {
                em.persist(user);
                em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
                terms = generateTerms();
                terms.forEach(t -> em.persist(t, descriptorFactory.termDescriptor(t)));
                vocabularies = generateVocabularies();
                vocabularies.forEach(v -> em.persist(v, descriptorFactory.vocabularyDescriptor(v)));
            });
            initialized = true;
        }
        Environment.setCurrentUser(user);
    }


    private List<Term> generateTerms() {
        final List<Term> terms = new ArrayList<>(10);
        for (int i = 0; i < Generator.randomInt(5, 10); i++) {
            final boolean b = Generator.randomBoolean();
            final Term term = new Term();
            term.setUri(Generator.generateUri());
            setPrimaryLabel(term, b ? "Matching label " + i : "Unknown label " + i);
            vocabulary.getGlossary().addRootTerm(term);
            term.setVocabulary(vocabulary.getUri());
            if (b) {
                term.setState(Generator.generateUri());
            }
            term.addType(TYPES[Generator.randomIndex(TYPES)]);
            term.setNotations(Set.of(String.valueOf(
                    Constants.LETTERS.charAt(Generator.randomInt(0, Constants.LETTERS.length())))));
            term.setExamples(Set.of(MultilingualString.create(b ? "Matching" : "Unknown" + " example " + i,
                    Environment.LANGUAGE)));
            terms.add(term);
        }
        terms.sort(Comparator.comparing(Environment::getPrimaryLabel));
        return terms;
    }

    private List<Vocabulary> generateVocabularies() {
        final List<Vocabulary> vocabularies = IntStream.range(0, 10).mapToObj(i -> Generator.generateVocabulary())
                                                       .collect(Collectors.toList());
        vocabularies.forEach(v -> {
            v.setUri(Generator.generateUri());
            if (Generator.randomBoolean()) {
                setPrimaryLabel(v, "Matching label " + Generator.randomInt());
            }
        });
        return vocabularies;
    }


    @Test
    void facetedTermSearchReturnsTermsMatchingIriSearchParamWithSpecifiedTypes() {
        final SearchParam param = new SearchParam(URI.create(RDF.TYPE.stringValue()), Set.of(TYPES[0], TYPES[1]),
                MatchType.IRI);
        final List<FacetedSearchResult> result = sut.facetedTermSearch(Set.of(param), Constants.DEFAULT_PAGE_SPEC);
        assertFalse(result.isEmpty());
        result.forEach(r -> assertThat(r.getTypes(), anyOf(hasItem(TYPES[0]), hasItem(TYPES[1]))));
    }

    @Test
    void facetedTermSearchReturnsTermsMatchingExactMatchSearchParamWithSpecifiedValue() {
        final SearchParam param = new SearchParam(URI.create(SKOS.NOTATION),
                Set.of(terms.get(0).getNotations().iterator().next()),
                MatchType.EXACT_MATCH);
        final List<Term> matchingTerms = terms.stream()
                                              .filter(t -> !Collections.disjoint(t.getNotations(), param.getValue()))
                                              .collect(Collectors.toList());
        final List<FacetedSearchResult> result = sut.facetedTermSearch(Set.of(param), Constants.DEFAULT_PAGE_SPEC);
        assertFalse(result.isEmpty());
        assertThat(result, containsSameEntities(matchingTerms));
    }

    @Test
    void facetedTermSearchReturnsTermsMatchingSubstringSearchParamWithSpecifiedValue() {
        final Term sample = Generator.randomElement(terms);
        final String sampleValue = sample.getExamples().iterator().next().get().substring(0, 4);
        final SearchParam param = new SearchParam(URI.create(SKOS.EXAMPLE),
                Set.of(sampleValue),
                MatchType.SUBSTRING);
        final List<Term> matchingTerms = terms.stream().filter(t -> t.getExamples().iterator().next().get()
                                                                     .startsWith(sampleValue))
                                              .collect(Collectors.toList());
        final List<FacetedSearchResult> result = sut.facetedTermSearch(Set.of(param), Constants.DEFAULT_PAGE_SPEC);
        assertFalse(result.isEmpty());
        assertThat(result, containsSameEntities(matchingTerms));
    }

    @Test
    void facetedTermSearchReturnsResultsMatchingMultipleSearchParameters() {
        final SearchParam typeParam = new SearchParam(URI.create(RDF.TYPE.stringValue()), Set.of(TYPES[0], TYPES[1]),
                MatchType.IRI);
        final SearchParam substringParam = new SearchParam(URI.create(SKOS.EXAMPLE),
                Set.of("matching"),
                MatchType.SUBSTRING);
        final List<Term> matchingTerms = terms.stream().filter(t -> t.getExamples().iterator().next().get()
                                                                     .startsWith("Matching") && (t.hasType(
                                                      TYPES[0]) || t.hasType(TYPES[1])))
                                              .collect(Collectors.toList());
        final List<FacetedSearchResult> result = sut.facetedTermSearch(Set.of(typeParam, substringParam),
                Constants.DEFAULT_PAGE_SPEC);
        assertThat(result, containsSameEntities(matchingTerms));
    }

    @Test
    void facetedTermSearchReturnsMatchingPage() {
        final Pageable pageOne = PageRequest.of(0, terms.size() / 2);
        final Pageable pageTwo = PageRequest.of(1, terms.size() / 2);
        final SearchParam searchParam = new SearchParam(
                URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku),
                Set.of(vocabulary.getUri().toString()), MatchType.IRI);

        final List<FacetedSearchResult> resultOne = sut.facetedTermSearch(Set.of(searchParam), pageOne);
        assertEquals(terms.size() / 2, resultOne.size());
        final List<FacetedSearchResult> resultTwo = sut.facetedTermSearch(Set.of(searchParam), pageTwo);
        assertEquals(terms.size() / 2, resultTwo.size());
        assertThat(resultOne, not(containsSameEntities(resultTwo)));
    }
}
