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
package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TermTest {

    @Test
    void hasParentInSameVocabularyReturnsFalseWhenTermHasNoParent() {
        final Term sut = Generator.generateTermWithId();
        assertFalse(sut.hasParentInSameVocabulary());
    }

    @Test
    void hasParentInSameVocabularyReturnsTrueWhenTermHasParentWithSameVocabulary() {
        final Term sut = Generator.generateTermWithId();
        final URI vocabularyUri = Generator.generateUri();
        sut.setGlossary(vocabularyUri);
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(vocabularyUri);
        sut.addParentTerm(parent);

        assertTrue(sut.hasParentInSameVocabulary());
    }

    @Test
    void hasParentInSameVocabularyReturnsFalseWhenTermHasParentWithDifferentVocabulary() {
        final Term sut = Generator.generateTermWithId();
        sut.setGlossary(Generator.generateUri());
        final Term parent = Generator.generateTermWithId();
        parent.setGlossary(Generator.generateUri());
        sut.addParentTerm(parent);

        assertFalse(sut.hasParentInSameVocabulary());
    }

    @Test
    void consolidateInferredCopiesInverseRelatedTermsToRelated() {
        final Term sut = Generator.generateMultiLingualTerm(Environment.LANGUAGE, "cs");
        sut.setVocabulary(Generator.generateUri());
        sut.setRelated(IntStream.range(0, 5)
                                .mapToObj(i -> new TermInfo(Generator.generateTermWithId(sut.getVocabulary())))
                                .collect(Collectors.toSet()));
        sut.setInverseRelated(IntStream.range(0, 5)
                                       .mapToObj(i -> new TermInfo(Generator.generateTermWithId(sut.getVocabulary())))
                                       .collect(Collectors.toSet()));
        final int originalRelatedSize = sut.getRelated().size();

        sut.consolidateInferred();
        assertEquals(originalRelatedSize + sut.getInverseRelated().size(), sut.getRelated().size());
        sut.getInverseRelated().forEach(ti -> assertThat(sut.getRelated(), hasItem(ti)));
    }

    @Test
    void consolidateInferredCopiesInverseRelatedMatchTermsToRelatedMatch() {
        final Term sut = Generator.generateTermWithId();
        sut.setRelatedMatch(IntStream.range(0, 5)
                                     .mapToObj(i -> new TermInfo(Generator.generateTermWithId(Generator.generateUri())))
                                     .collect(Collectors.toSet()));
        sut.setInverseRelatedMatch(IntStream.range(0, 5)
                                            .mapToObj(i -> new TermInfo(
                                                    Generator.generateTermWithId(Generator.generateUri())))
                                            .collect(Collectors.toSet()));
        final int originalRelatedMatchSize = sut.getRelatedMatch().size();

        sut.consolidateInferred();
        assertEquals(originalRelatedMatchSize + sut.getInverseRelatedMatch().size(), sut.getRelatedMatch().size());
        sut.getInverseRelatedMatch().forEach(ti -> assertThat(sut.getRelatedMatch(), hasItem(ti)));
    }

    @Test
    void consolidateInferredCopiesInverseExactMatchTermsToExactMatch() {
        final Term sut = Generator.generateTermWithId();
        sut.setExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator
                                                                                        .generateTermWithId(
                                                                                                Generator.generateUri())))
                                        .collect(Collectors.toSet()));
        sut.setInverseExactMatchTerms(IntStream.range(0, 5).mapToObj(i -> new TermInfo(Generator
                                                                                               .generateTermWithId(
                                                                                                       Generator.generateUri())))
                                               .collect(Collectors.toSet()));
        final int originalExactMatchSize = sut.getExactMatchTerms().size();

        sut.consolidateInferred();
        assertEquals(originalExactMatchSize + sut.getInverseExactMatchTerms().size(), sut.getExactMatchTerms().size());
        sut.getInverseExactMatchTerms().forEach(ti -> assertThat(sut.getExactMatchTerms(), hasItem(ti)));
    }

    @Test
    void addParentTermAddsSpecifiedTermToParentsWhenItIsFromSameGlossary() {
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.getGlossary().setUri(Generator.generateUri());
        final Term sut = Generator.generateTermWithId();
        sut.setGlossary(vocabulary.getGlossary().getUri());
        final Term parentToAdd = Generator.generateTermWithId();
        parentToAdd.setGlossary(vocabulary.getGlossary().getUri());

        sut.addParentTerm(parentToAdd);
        assertThat(sut.getParentTerms(), hasItem(parentToAdd));
        assertThat(sut.getExternalParentTerms(), anyOf(nullValue(), emptyCollectionOf(Term.class)));
    }

    @Test
    void addParentTermAddsSpecifiedTermToExternalParentsWhenItIsFromDifferentGlossary() {
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.getGlossary().setUri(Generator.generateUri());
        final Term sut = Generator.generateTermWithId();
        sut.setGlossary(vocabulary.getGlossary().getUri());
        final Term parentToAdd = Generator.generateTermWithId();
        parentToAdd.setGlossary(Generator.generateUri());

        sut.addParentTerm(parentToAdd);
        assertThat(sut.getParentTerms(), anyOf(nullValue(), emptyCollectionOf(Term.class)));
        assertThat(sut.getExternalParentTerms(), hasItem(parentToAdd));
    }

    @Test
    void consolidateParentsCopiesExternalParentTermsToParentTerms() {
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.getGlossary().setUri(Generator.generateUri());
        final Term sut = Generator.generateTermWithId();
        final Set<Term> externalParents = IntStream.range(0, 5).mapToObj(i -> {
            final Term t = Generator.generateTermWithId();
            t.setGlossary(Generator.generateUri());
            return t;
        }).collect(Collectors.toSet());
        sut.setExternalParentTerms(externalParents);

        sut.consolidateParents();
        assertThat(sut.getParentTerms(), hasItems(externalParents.toArray(new Term[0])));
    }

    @Test
    void consolidateParentsHandlesNullExternalParentTerms() {
        final Term sut = Generator.generateTermWithId();

        sut.consolidateParents();
        assertThat(sut.getParentTerms(), anyOf(nullValue(), emptyCollectionOf(Term.class)));
    }

    @Test
    void splitExternalAndInternalParentsMovesParentsWithDifferentGlossaryFromParentTermsToExternalParentTerms() {
        final URI glossaryUri = Generator.generateUri();
        final Term sut = Generator.generateTermWithId();
        sut.setGlossary(glossaryUri);
        final Set<Term> externalParents = IntStream.range(0, 5).mapToObj(i -> {
            final Term t = Generator.generateTermWithId();
            t.setGlossary(Generator.generateUri());
            return t;
        }).collect(Collectors.toSet());
        final Set<Term> internalParents = IntStream.range(0, 5).mapToObj(i -> {
            final Term t = Generator.generateTermWithId();
            t.setGlossary(glossaryUri);
            return t;
        }).collect(Collectors.toSet());
        final Set<Term> allParents = new HashSet<>(externalParents);
        allParents.addAll(internalParents);
        sut.setParentTerms(allParents);

        sut.splitExternalAndInternalParents();
        assertEquals(internalParents, sut.getParentTerms());
        assertEquals(externalParents, sut.getExternalParentTerms());
    }

    @Test
    void splitExternalAndInternalParentsDoesNothingWhenTermHasNoParents() {
        final Term sut = Generator.generateTermWithId();
        sut.splitExternalAndInternalParents();
        assertThat(sut.getParentTerms(), anyOf(nullValue(), emptyCollectionOf(Term.class)));
        assertThat(sut.getExternalParentTerms(), anyOf(nullValue(), emptyCollectionOf(Term.class)));
    }

    @Test
    void isSnapshotReturnsTrueWhenInstanceHasSnapshotType() {
        final Term original = Generator.generateTermWithId();
        final Term snapshot = Generator.generateTermWithId();
        snapshot.addType(Vocabulary.s_c_verze_pojmu);
        assertFalse(original.isSnapshot());
        assertTrue(snapshot.isSnapshot());
    }
}
