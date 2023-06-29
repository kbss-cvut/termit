/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FacetedSearchResult;
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.dto.search.MatchType;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.util.Constants;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.environment.util.ContainsSameEntities.containsSameEntities;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This class tests the default full text search functionality.
 * <p>
 * Repository-tailored queries stored in corresponding profiles should be used in production.
 */
class SearchDaoTest extends BaseDaoTestRunner {

    private static final String[] TYPES = {"http://onto.fel.cvut.cz/ontologies/ufo/event",
                                           "http://onto.fel.cvut.cz/ontologies/ufo/object",
                                           "http://onto.fel.cvut.cz/ontologies/ufo/relator"};

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private SearchDao sut;

    private static boolean initialized = false;

    private static User user;

    private static Vocabulary vocabulary;
    private static List<Term> terms;
    private static List<Vocabulary> vocabularies;

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
                vocabularies = generateVocabularies();
                vocabularies.forEach(v -> em.persist(v, descriptorFactory.vocabularyDescriptor(v)));
            });
            initialized = true;
        }
        Environment.setCurrentUser(user);
    }

    @Test
    void defaultFullTextSearchFindsTermsWithMatchingLabel() {
        final Collection<Term> matching = terms.stream().filter(t -> t.getPrimaryLabel().contains("Matching"))
                                               .collect(Collectors.toList());

        final List<FullTextSearchResult> allResults = sut.fullTextSearch("matching");
        final List<FullTextSearchResult> termResults = allResults.stream().filter(r -> r.hasType(SKOS.CONCEPT)).collect(
                Collectors.toList());
        assertEquals(matching.size(), termResults.size());
        assertThat(termResults, containsSameEntities(matching));
    }

    private List<Term> generateTerms() {
        final List<Term> terms = new ArrayList<>(10);
        for (int i = 0; i < Generator.randomInt(5, 10); i++) {
            final boolean b = Generator.randomBoolean();
            final Term term = new Term();
            term.setUri(Generator.generateUri());
            term.setPrimaryLabel(b ? "Matching label " + i : "Unknown label " + i);
            vocabulary.getGlossary().addRootTerm(term);
            term.setVocabulary(vocabulary.getUri());
            term.setDraft(b);
            term.addType(TYPES[Generator.randomIndex(TYPES)]);
            term.setNotations(Set.of(String.valueOf(
                    Constants.LETTERS.charAt(Generator.randomInt(0, Constants.LETTERS.length())))));
            term.setExamples(Set.of(MultilingualString.create(b ? "Matching" : "Unknown" + " example " + i,
                                                              Environment.LANGUAGE)));
            terms.add(term);
        }
        terms.sort(Comparator.comparing(Term::getPrimaryLabel));
        return terms;
    }

    @Test
    void defaultFullTextSearchFindsVocabulariesWithMatchingLabel() {
        final Collection<Vocabulary> matching = vocabularies.stream().filter(v -> v.getLabel().contains("Matching"))
                                                            .collect(Collectors.toList());
        final List<FullTextSearchResult> allResults = sut.fullTextSearch("matching");
        final List<FullTextSearchResult> vocabularyResults = allResults.stream()
                                                                       .filter(r -> r.hasType(
                                                                               cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik))
                                                                       .collect(Collectors.toList());
        assertEquals(matching.size(), vocabularyResults.size());
        assertThat(vocabularyResults, containsSameEntities(matching));
    }

    private List<Vocabulary> generateVocabularies() {
        final List<Vocabulary> vocabularies = IntStream.range(0, 10).mapToObj(i -> Generator.generateVocabulary())
                                                       .collect(Collectors.toList());
        vocabularies.forEach(v -> {
            v.setUri(Generator.generateUri());
            if (Generator.randomBoolean()) {
                v.setLabel("Matching label " + Generator.randomInt());
            }
        });
        return vocabularies;
    }

    @Test
    void defaultFullTextSearchFindsVocabulariesAndTermsWithMatchingLabel() {
        final Collection<Term> matchingTerms = terms.stream().filter(t -> t.getPrimaryLabel().contains("Matching"))
                                                    .collect(Collectors.toList());
        final Collection<Vocabulary> matchingVocabularies = vocabularies.stream()
                                                                        .filter(v -> v.getLabel().contains("Matching"))
                                                                        .collect(Collectors.toList());
        final List<FullTextSearchResult> result = sut.fullTextSearch("matching");
        assertEquals(matchingTerms.size() + matchingVocabularies.size(), result.size());
        for (FullTextSearchResult item : result) {
            if (item.hasType(SKOS.CONCEPT)) {
                assertTrue(matchingTerms.stream().anyMatch(t -> t.getUri().equals(item.getUri())));
            } else {
                assertTrue(matchingVocabularies.stream().anyMatch(v -> v.getUri().equals(item.getUri())));
            }
        }
    }

    @Test
    void defaultFullTextSearchIncludesDraftStatusInResult() {
        final Collection<Term> matching = terms.stream().filter(t -> t.getPrimaryLabel().contains("Matching"))
                                               .collect(Collectors.toList());

        final List<FullTextSearchResult> allResults = sut.fullTextSearch("matching");
        final List<FullTextSearchResult> termResults = allResults.stream()
                                                                 .filter(r -> r.hasType(SKOS.CONCEPT))
                                                                 .collect(Collectors.toList());
        for (FullTextSearchResult ftsResult : termResults) {
            final Optional<Term> term = matching.stream().filter(t -> t.getUri().equals(ftsResult.getUri()))
                                                .findFirst();
            assertTrue(term.isPresent());
            assertEquals(term.get().isDraft(), ftsResult.isDraft());
        }
    }

    @Test
    void defaultFullTextSearchReturnsEmptyListForEmptyInputString() {
        final List<FullTextSearchResult> result = sut.fullTextSearch("");
        assertTrue(result.isEmpty());
    }

    @Test
    void defaultFullTextSearchSkipsSnapshots() {
        final String matchingLabel = "Snapshot";
        final Vocabulary v = Generator.generateVocabularyWithId();
        v.setLabel(matchingLabel + " 0");
        final Vocabulary snapshot = Generator.generateVocabularyWithId();
        snapshot.setLabel(matchingLabel + " 1");
        transactional(() -> {
            em.persist(v);
            em.persist(snapshot);
            insertSnapshotType(snapshot);
        });

        final List<FullTextSearchResult> result = sut.fullTextSearch(matchingLabel);
        assertEquals(1, result.size());
        assertEquals(v.getUri(), result.get(0).getUri());
    }

    private void insertSnapshotType(HasIdentifier asset) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection connection = repo.getConnection()) {
            final ValueFactory vf = connection.getValueFactory();
            connection.add(vf.createIRI(asset.getUri().toString()), RDF.TYPE, vf.createIRI(
                    cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_objektu));
        }
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
        final SearchParam param = new SearchParam(URI.create(SKOS.EXAMPLE),
                                                  Set.of("matching"),
                                                  MatchType.SUBSTRING);
        final List<Term> matchingTerms = terms.stream().filter(t -> t.getExamples().iterator().next().get()
                                                                     .startsWith("Matching"))
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
