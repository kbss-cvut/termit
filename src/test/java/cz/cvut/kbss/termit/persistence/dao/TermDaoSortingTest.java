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

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.listing.FlatTermDto;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TermDaoSortingTest extends BaseTermDaoTestRunner {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        super.setUp();
        sut.setApplicationEventPublisher(eventPublisher);
    }

    private Term createTerm(String label, String lang) {
        final Term term = Generator.generateTermWithId();
        term.setLabel(MultilingualString.create(label, lang));
        term.setVocabulary(vocabulary.getUri());
        return term;
    }

    private void addTermsAndSave(List<Term> terms) {
        vocabulary.setRootTerms(terms.stream().map(Term::getUri).collect(Collectors.toSet()));
        transactional(() -> {
            em.merge(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            terms.forEach(t -> em.persist(t, descriptorFactory.termDescriptor(vocabulary)));
        });
    }

    @Test
    void findAllFlatPrioritizesTermsInRequestedLanguageRegardlessOfSortDirection() {
        Term cs1 = createTerm("Auto", "cs");
        Term cs2 = createTerm("Zebra", "cs");
        Term en1 = createTerm("Apple", "en");
        Term en2 = createTerm("Zoo", "en");
        addTermsAndSave(Arrays.asList(cs1, cs2, en1, en2));

        List<FlatTermDto> asc =
                sut.findAllFlat(vocabulary, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "label")), "cs");
        assertEquals(
                Arrays.asList("Auto", "Zebra", "Apple", "Zoo"),
                asc.stream()
                        .map(t -> t.getLabel().getLanguages().contains("cs")
                                ? t.getLabel().get("cs")
                                : t.getLabel().get("en"))
                        .toList());

        List<FlatTermDto> desc =
                sut.findAllFlat(vocabulary, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "label")), "cs");
        assertEquals(
                Arrays.asList("Zebra", "Auto", "Zoo", "Apple"),
                desc.stream()
                        .map(t -> t.getLabel().getLanguages().contains("cs")
                                ? t.getLabel().get("cs")
                                : t.getLabel().get("en"))
                        .toList());
    }

    @Test
    void findAllFlatSortsTermsUsingCzechCollationRules() {
        Term t1 = createTerm("Cibule", "cs");
        Term t2 = createTerm("Česnek", "cs");
        Term t3 = createTerm("Dýně", "cs");
        Term t4 = createTerm("Celer", "cs");
        addTermsAndSave(Arrays.asList(t1, t2, t3, t4));

        List<FlatTermDto> result =
                sut.findAllFlat(vocabulary, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "label")), "cs");

        assertEquals(
                Arrays.asList("Celer", "Cibule", "Česnek", "Dýně"),
                result.stream().map(t -> t.getLabel().get("cs")).toList());
    }

    @Test
    void findAllFlatSortsIdenticalLabelsUsingTermUriAsTieBreaker() {
        Term t1 = createTerm("Kolo", "cs");
        Term t2 = createTerm("Kolo", "cs");
        Term t3 = createTerm("Kolo", "cs");
        addTermsAndSave(Arrays.asList(t1, t2, t3));

        List<Term> expected = Arrays.asList(t1, t2, t3);
        expected.sort(Comparator.comparing(t -> t.getUri().toString()));

        List<FlatTermDto> result =
                sut.findAllFlat(vocabulary, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "label")), "cs");

        assertEquals(expected.get(0).getUri(), result.get(0).getUri());
        assertEquals(expected.get(1).getUri(), result.get(1).getUri());
        assertEquals(expected.get(2).getUri(), result.get(2).getUri());
    }
}
