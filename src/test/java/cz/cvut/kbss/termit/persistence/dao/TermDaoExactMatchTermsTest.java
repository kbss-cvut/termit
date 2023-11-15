/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.persistence.dao;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TermDaoExactMatchTermsTest extends BaseTermDaoTestRunner {

    @BeforeEach
    void setUp() {
        super.setUp();
    }

    @Test
    void findLoadsInferredInverseExactMatchTerms() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> exactMatchTerms = Arrays.asList(Generator.generateTermWithId(Generator.generateUri()), Generator.generateTermWithId(Generator.generateUri()));
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            exactMatchTerms.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t));
                Generator.addTermInVocabularyRelationship(t, t.getVocabulary(), em);
            });
            generateExactMatchRelationships(term, exactMatchTerms);
        });

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(exactMatchTerms.size(), result.get().getInverseExactMatchTerms().size());
        exactMatchTerms.forEach(rt -> assertThat(result.get().getInverseExactMatchTerms(), hasItem(new TermInfo(rt))));
    }

    private void generateExactMatchRelationships(Term term, Collection<Term> related) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repo.getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            conn.begin();
            for (Term r : related) {
                // Don't put it into any specific context to make it look like inference
                conn.add(vf.createIRI(r.getUri().toString()), vf.createIRI(SKOS.EXACT_MATCH), vf.createIRI(term.getUri().toString()));
            }
            conn.commit();
        }
    }

    @Test
    void loadingInferredInverseExactMatchExcludesExactMatchAssertedFromSubject() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> exactMatchTerms = Arrays.asList(Generator.generateTermWithId(Generator.generateUri()), Generator.generateTermWithId(Generator.generateUri()));
        final List<Term> inverseExactMatchTerms = new ArrayList<>(Collections.singletonList(Generator.generateTermWithId(Generator.generateUri())));
        inverseExactMatchTerms.addAll(exactMatchTerms);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            inverseExactMatchTerms.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t.getVocabulary()));
                Generator.addTermInVocabularyRelationship(t, t.getVocabulary(), em);
            });
            generateExactMatchRelationships(term, inverseExactMatchTerms);
        });
        term.setExactMatchTerms(exactMatchTerms.stream().map(TermInfo::new).collect(Collectors.toSet()));
        transactional(() -> em.merge(term, descriptorFactory.termDescriptor(term)));

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertFalse(result.get().getInverseExactMatchTerms().isEmpty());
        exactMatchTerms.forEach(r -> assertThat(result.get().getInverseExactMatchTerms(), not(hasItem(new TermInfo(r)))));
    }
}
