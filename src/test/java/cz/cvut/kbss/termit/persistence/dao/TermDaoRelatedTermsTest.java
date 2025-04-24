/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TermDaoRelatedTermsTest extends BaseTermDaoTestRunner {

    @BeforeEach
    void setUp() {
        super.setUp();
    }

    @Test
    void findLoadsInferredInverseRelatedTerms() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> related = Arrays.asList(Generator.generateTermWithId(vocabulary.getUri()),
                                                 Generator.generateTermWithId(vocabulary.getUri()),
                                                 Generator.generateTermWithId(vocabulary.getUri()));
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            related.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(vocabulary));
                Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
            });
            generateRelatedRelationships(term, related, SKOS.RELATED);
        });

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(related.size(), result.get().getInverseRelated().size());
        related.forEach(rt -> assertThat(result.get().getInverseRelated(), hasItem(new TermInfo(rt))));
    }

    private void generateRelatedRelationships(Term term, Collection<Term> related, String relationship) {
        Generator.simulateInferredSkosRelationship(term, related, relationship, em);
    }

    @Test
    void loadingInferredInverseRelatedExcludesRelatedAssertedFromSubject() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> related = Arrays.asList(Generator.generateTermWithId(vocabulary.getUri()),
                                                 Generator.generateTermWithId(vocabulary.getUri()));
        final List<Term> inverseRelated = new ArrayList<>(
                Arrays.asList(Generator.generateTermWithId(vocabulary.getUri()),
                              Generator.generateTermWithId(vocabulary.getUri())));
        inverseRelated.addAll(related);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            inverseRelated.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(vocabulary));
                Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
            });
            generateRelatedRelationships(term, inverseRelated, SKOS.RELATED);
        });
        term.setRelated(related.stream().map(TermInfo::new).collect(Collectors.toSet()));
        transactional(() -> em.merge(term, descriptorFactory.termDescriptor(term)));

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertFalse(result.get().getInverseRelated().isEmpty());
        related.forEach(r -> assertThat(result.get().getInverseRelated(), not(hasItem(new TermInfo(r)))));
    }

    @Test
    void findLoadsInferredInverseRelatedMatchTerms() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> relatedMatch = Arrays.asList(Generator.generateTermWithId(Generator.generateUri()),
                                                      Generator.generateTermWithId(Generator.generateUri()));
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            relatedMatch.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t));
                Generator.addTermInVocabularyRelationship(t, t.getVocabulary(), em);
            });
            generateRelatedRelationships(term, relatedMatch, SKOS.RELATED_MATCH);
        });

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(relatedMatch.size(), result.get().getInverseRelatedMatch().size());
        relatedMatch.forEach(rt -> assertThat(result.get().getInverseRelatedMatch(), hasItem(new TermInfo(rt))));
    }

    @Test
    void loadingInferredInverseRelatedMatchExcludesRelatedMatchAssertedFromSubject() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> relatedMatch = Arrays.asList(Generator.generateTermWithId(Generator.generateUri()),
                                                      Generator.generateTermWithId(Generator.generateUri()));
        final List<Term> inverseRelatedMatch = new ArrayList<>(
                Collections.singletonList(Generator.generateTermWithId(Generator.generateUri())));
        inverseRelatedMatch.addAll(relatedMatch);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            inverseRelatedMatch.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t.getVocabulary()));
                Generator.addTermInVocabularyRelationship(t, t.getVocabulary(), em);
            });
            generateRelatedRelationships(term, inverseRelatedMatch, SKOS.RELATED_MATCH);
        });
        term.setRelatedMatch(relatedMatch.stream().map(TermInfo::new).collect(Collectors.toSet()));
        transactional(() -> em.merge(term, descriptorFactory.termDescriptor(term)));

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertFalse(result.get().getInverseRelatedMatch().isEmpty());
        relatedMatch.forEach(r -> assertThat(result.get().getInverseRelatedMatch(), not(hasItem(new TermInfo(r)))));
    }

    @Test
    void findAllLoadsInferredInverseRelatedAndRelatedMatchTerms() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> related = Arrays.asList(Generator.generateTermWithId(vocabulary.getUri()), Generator
                .generateTermWithId(vocabulary.getUri()));
        final List<Term> inverseRelated = new ArrayList<>(Arrays
                                                                  .asList(Generator.generateTermWithId(
                                                                          vocabulary.getUri()), Generator
                                                                                  .generateTermWithId(
                                                                                          vocabulary.getUri())));
        final List<Term> relatedMatch = Arrays.asList(Generator.generateTermWithId(Generator.generateUri()), Generator
                .generateTermWithId(Generator.generateUri()));
        final List<Term> inverseRelatedMatch = new ArrayList<>(Collections
                                                                       .singletonList(Generator.generateTermWithId(
                                                                               Generator.generateUri())));
        final Collection<Term> allRelated = Utils.joinCollections(related, inverseRelated);
        final Collection<Term> allRelatedMatch = Utils.joinCollections(relatedMatch, inverseRelatedMatch);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            allRelated.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(vocabulary));
                Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
            });
            generateRelatedRelationships(term, allRelated, SKOS.RELATED);
            allRelatedMatch.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t.getVocabulary()));
                Generator.addTermInVocabularyRelationship(t, t.getVocabulary(), em);
            });
            generateRelatedRelationships(term, allRelatedMatch, SKOS.RELATED_MATCH);
        });
        term.setRelated(related.stream().map(TermInfo::new).collect(Collectors.toSet()));
        term.setRelatedMatch(relatedMatch.stream().map(TermInfo::new).collect(Collectors.toSet()));
        transactional(() -> em.merge(term, descriptorFactory.termDescriptor(term)));

        final List<Term> result = sut.findAllFull(vocabulary);
        final Optional<Term> singleResult = result.stream().filter(t -> t.equals(term)).findFirst();
        assertTrue(singleResult.isPresent());
        inverseRelated.forEach(ir -> assertThat(singleResult.get().getInverseRelated(), hasItem(new TermInfo(ir))));
        inverseRelatedMatch
                .forEach(ir -> assertThat(singleResult.get().getInverseRelatedMatch(), hasItem(new TermInfo(ir))));
    }

    @Test
    void inferredInverseRelatedDoesNotContainRelatedMatch() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> relatedMatch = Arrays.asList(Generator.generateTermWithId(Generator.generateUri()), Generator
                .generateTermWithId(Generator.generateUri()));
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(term));
            relatedMatch.forEach(t -> em.persist(t, descriptorFactory.termDescriptor(t)));
            generateRelatedRelationships(term, relatedMatch, SKOS.RELATED);
        });
        term.setRelatedMatch(relatedMatch.stream().map(TermInfo::new).collect(Collectors.toSet()));
        transactional(() -> em.merge(term, descriptorFactory.termDescriptor(term)));

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertThat(result.get().getInverseRelated(), anyOf(emptyCollectionOf(TermInfo.class), nullValue()));
        assertThat(result.get().getRelatedMatch(), hasItems(relatedMatch.stream().map(TermInfo::new)
                                                                        .toArray(TermInfo[]::new)));
    }
}
