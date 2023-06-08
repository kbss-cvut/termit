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
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.model.util.EntityToOwlClassMapper.getOwlClassForEntity;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This class tests the default full text search functionality.
 * <p>
 * Repository-tailored queries stored in corresponding profiles should be used in production.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SearchDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private SearchDao sut;

    private User user;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserWithId();
        transactional(() -> em.persist(user));
        Environment.setCurrentUser(user);
    }

    @Test
    void defaultFullTextSearchFindsTermsWithMatchingLabel() {
        final List<Term> terms = generateAndPersistTerms();
        final Collection<Term> matching = terms.stream().filter(t -> t.getPrimaryLabel().contains("Matching"))
                                               .collect(Collectors.toList());

        final List<FullTextSearchResult> result = sut.fullTextSearch("matching");
        assertEquals(matching.size(), result.size());
        for (FullTextSearchResult item : result) {
            assertTrue(item.getTypes().contains(getOwlClassForEntity(Term.class)));
            assertTrue(matching.stream().anyMatch(t -> t.getUri().equals(item.getUri())));
        }
    }

    private List<Term> generateAndPersistTerms() {
        final List<Term> terms = generateTerms();
        transactional(() -> {
            em.persist(vocabulary);
            terms.forEach(em::persist);
        });
        return terms;
    }

    private List<Term> generateTerms() {
        this.vocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = new ArrayList<>(10);
        for (int i = 0; i < Generator.randomInt(5, 10); i++) {
            final Term term = new Term();
            term.setUri(Generator.generateUri());
            term.setPrimaryLabel(Generator.randomBoolean() ? "Matching label " + i : "Unknown label " + i);
            vocabulary.getGlossary().addRootTerm(term);
            term.setVocabulary(vocabulary.getUri());
            terms.add(term);
        }
        return terms;
    }

    @Test
    void defaultFullTextSearchFindsVocabulariesWithMatchingLabel() {
        final List<Vocabulary> vocabularies = generateVocabularies();
        transactional(() -> vocabularies.forEach(em::persist));
        final Collection<Vocabulary> matching = vocabularies.stream().filter(v -> v.getLabel().contains("Matching"))
                                                            .collect(Collectors.toList());
        final List<FullTextSearchResult> result = sut.fullTextSearch("matching");
        assertEquals(matching.size(), result.size());
        for (FullTextSearchResult item : result) {
            assertTrue(item.getTypes().contains(getOwlClassForEntity(Vocabulary.class)));
            assertTrue(matching.stream().anyMatch(t -> t.getUri().equals(item.getUri())));
        }
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
        final List<Term> terms = generateTerms();
        final List<Vocabulary> vocabularies = generateVocabularies();
        transactional(() -> {
            em.persist(vocabulary);
            terms.forEach(em::persist);
            vocabularies.forEach(em::persist);
        });
        final Collection<Term> matchingTerms = terms.stream().filter(t -> t.getPrimaryLabel().contains("Matching"))
                                                    .collect(Collectors.toList());
        final Collection<Vocabulary> matchingVocabularies = vocabularies.stream()
                                                                        .filter(v -> v.getLabel().contains("Matching"))
                                                                        .collect(Collectors.toList());
        final List<FullTextSearchResult> result = sut.fullTextSearch("matching");
        assertEquals(matchingTerms.size() + matchingVocabularies.size(), result.size());
        for (FullTextSearchResult item : result) {
            if (item.getTypes().contains(getOwlClassForEntity(Term.class))) {
                assertTrue(matchingTerms.stream().anyMatch(t -> t.getUri().equals(item.getUri())));
            } else {
                assertTrue(matchingVocabularies.stream().anyMatch(v -> v.getUri().equals(item.getUri())));
            }
        }
    }

    @Test
    void defaultFullTextSearchIncludesDraftStatusInResult() {
        final List<Term> terms = generateTerms();
        transactional(() -> {
            em.persist(vocabulary);
            terms.forEach(t -> {
                t.setDraft(Generator.randomBoolean());
                em.persist(t);
            });
        });
        final Collection<Term> matching = terms.stream().filter(t -> t.getPrimaryLabel().contains("Matching"))
                                               .collect(Collectors.toList());

        final List<FullTextSearchResult> result = sut.fullTextSearch("matching");
        assertEquals(matching.size(), result.size());
        for (FullTextSearchResult ftsResult : result) {
            final Optional<Term> term = matching.stream().filter(t -> t.getUri().equals(ftsResult.getUri()))
                                                .findFirst();
            assertTrue(term.isPresent());
            assertEquals(term.get().isDraft(), ftsResult.isDraft());
        }
    }

    @Test
    void defaultFullTextSearchReturnsEmptyListForEmptyInputString() {
        generateAndPersistTerms();
        final List<FullTextSearchResult> result = sut.fullTextSearch("");
        assertTrue(result.isEmpty());
    }

    @Test
    void defaultFullTextSearchSkipsSnapshots() {
        final String matchingLabel = "Matching";
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
}
