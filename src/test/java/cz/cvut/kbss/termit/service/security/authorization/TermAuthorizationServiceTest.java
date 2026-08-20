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
package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.TermInfoWithParents;
import cz.cvut.kbss.termit.model.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermAuthorizationServiceTest {

    @Mock
    private VocabularyAuthorizationService vocabularyAuthorizationService;

    @InjectMocks
    private TermAuthorizationService sut;

    @Test
    void canCreateInChecksIfUserCanModifyTargetVocabulary() {
        final Vocabulary target = Generator.generateVocabularyWithId();
        when(vocabularyAuthorizationService.canModify(target)).thenReturn(true);

        assertTrue(sut.canCreateIn(target));
        verify(vocabularyAuthorizationService).canModify(target);
    }

    @Test
    void canCreateChildChecksIfTermCanBeCreatedInParentTermVocabulary() {
        final Vocabulary target = Generator.generateVocabularyWithId();
        final Term parent = Generator.generateTermWithId(target.getUri());
        when(vocabularyAuthorizationService.canModify(target)).thenReturn(true);

        assertTrue(sut.canCreateChild(parent));
        verify(vocabularyAuthorizationService).canModify(target);
    }

    @Test
    void canReadChecksIfUserCanReadTermVocabulary() {
        when(vocabularyAuthorizationService.canRead(any(Vocabulary.class))).thenReturn(true);
        final Term term = Generator.generateTermWithId();
        final Vocabulary v = Generator.generateVocabularyWithId();
        term.setVocabulary(v.getUri());

        assertTrue(sut.canRead(term));
        verify(vocabularyAuthorizationService).canRead(v);
    }

    @Test
    void canModifyTermChecksIfUserCanModifyTermVocabulary() {
        final Term term = Generator.generateTermWithId(Generator.generateUri());
        when(vocabularyAuthorizationService.canModify(any(Vocabulary.class))).thenReturn(true);

        assertTrue(sut.canModify(term));
        verify(vocabularyAuthorizationService).canModify(new Vocabulary(term.getVocabulary()));
    }

    @Test
    void canRemoveChecksIfUserCanRemoveTermVocabulary() {
        when(vocabularyAuthorizationService.canRemove(any(Vocabulary.class))).thenReturn(true);
        final Term term = Generator.generateTermWithId();
        final Vocabulary v = Generator.generateVocabularyWithId();
        term.setVocabulary(v.getUri());

        assertTrue(sut.canRemove(term));
        verify(vocabularyAuthorizationService).canRemove(v);
    }


    @Test
    void removeUnauthorizedTermsAndAncestorsRemovesTermsFromUnauthorizedVocabulary() {
        final Vocabulary authorizedVoc = Generator.generateVocabularyWithId();
        final Vocabulary unauthorizedVoc = Generator.generateVocabularyWithId();

        final TermInfoWithParents term = new TermInfoWithParents();
        term.setUri(Generator.generateUri());
        term.setVocabulary(authorizedVoc.getUri());
        final TermInfoWithParents term2 = new TermInfoWithParents();
        term2.setUri(Generator.generateUri());
        term2.setVocabulary(unauthorizedVoc.getUri());

        doAnswer(inv ->
            inv.getArgument(0, Vocabulary.class).getUri().equals(authorizedVoc.getUri())
        ).when(vocabularyAuthorizationService).canRead(any(Vocabulary.class));

        Set<TermInfoWithParents> terms = new HashSet<>(Set.of(term, term2));

        sut.removeUnauthorizedTermsAndAncestors(terms);

        assertEquals(1, terms.size());
        assertTrue(terms.contains(term));
        assertFalse(terms.contains(term2));
    }

    @Test
    void removeUnauthorizedTermsAndAncestorsRemovesAncestorsFromUnauthorizedVocabulary() {
        final Vocabulary authorizedVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary unauthorizedVocabulary = Generator.generateVocabularyWithId();

        final TermInfoWithParents term = new TermInfoWithParents();
        term.setUri(Generator.generateUri());
        term.setVocabulary(authorizedVocabulary.getUri());
        final TermInfoWithParents term2 = new TermInfoWithParents();
        term2.setUri(Generator.generateUri());
        term2.setVocabulary(authorizedVocabulary.getUri());
        final TermInfoWithParents term3 = new TermInfoWithParents();
        term3.setUri(Generator.generateUri());
        term3.setVocabulary(unauthorizedVocabulary.getUri());

        term.setParentTerms(Set.of(term2));
        term2.setParentTerms(Set.of(term3));

        doAnswer(inv ->
                inv.getArgument(0, Vocabulary.class).getUri().equals(authorizedVocabulary.getUri())
        ).when(vocabularyAuthorizationService).canRead(any(Vocabulary.class));

        Set<TermInfoWithParents> terms = new HashSet<>(Set.of(term));

        sut.removeUnauthorizedTermsAndAncestors(terms);

        assertEquals(1, terms.size());
        assertTrue(terms.contains(term));
        assertEquals(1, term.getParentTerms().size());
        assertTrue(term.getParentTerms().contains(term2));
        assertTrue(term2.getParentTerms().isEmpty());
    }
}
