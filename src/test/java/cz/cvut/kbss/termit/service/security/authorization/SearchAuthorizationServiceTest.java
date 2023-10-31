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
package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FacetedSearchResult;
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchAuthorizationServiceTest {

    @Mock
    private VocabularyAuthorizationService vocabularyAuthorizationService;

    @InjectMocks
    private SearchAuthorizationService sut;

    @Test
    void canReadChecksIfVocabularyIsReadableForTermResult() {
        final FullTextSearchResult res = new FullTextSearchResult(Generator.generateUri(), "test string",
                Generator.generateUri(), Generator.generateUri(),
                SKOS.CONCEPT, "label", "test",
                (double) Generator.randomInt());
        when(vocabularyAuthorizationService.canRead(any(Vocabulary.class))).thenReturn(true);
        assertTrue(sut.canRead(res));
        verify(vocabularyAuthorizationService).canRead(new Vocabulary(res.getVocabulary()));
    }

    @Test
    void canReadChecksIfVocabularyIsReadableForVocabularyResult() {
        final FullTextSearchResult res = new FullTextSearchResult(Generator.generateUri(), "test label",
                null, null,
                cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik,
                "label", "test",
                (double) Generator.randomInt());
        assertFalse(sut.canRead(res));
        verify(vocabularyAuthorizationService).canRead(new Vocabulary(res.getUri()));
    }

    @Test
    void canReadChecksIfVocabularyIsReadableForFacetedSearchTermResult() {
        final FacetedSearchResult res = new FacetedSearchResult();
        res.setUri(Generator.generateUri());
        res.setVocabulary(Generator.generateUri());
        when(vocabularyAuthorizationService.canRead(any(Vocabulary.class))).thenReturn(true);
        assertTrue(sut.canRead(res));
        verify(vocabularyAuthorizationService).canRead(new Vocabulary(res.getVocabulary()));
    }
}
