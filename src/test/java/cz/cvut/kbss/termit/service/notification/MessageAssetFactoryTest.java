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
package cz.cvut.kbss.termit.service.notification;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.mail.ApplicationLinkBuilder;
import cz.cvut.kbss.termit.service.repository.DataRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static cz.cvut.kbss.termit.environment.Environment.getPrimaryLabel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageAssetFactoryTest {

    @Mock
    private ApplicationLinkBuilder linkBuilder;

    @Mock
    private DataRepositoryService dataService;

    /**
     * Used for injection in {@link #sut}
     */
    @Spy
    private Configuration configuration = new Configuration();

    @InjectMocks
    private MessageAssetFactory sut;

    @Test
    void createUsesVocabularyTitleAndLinkBuilderGeneratedLinkToConstructMessageAsset() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(linkBuilder.linkTo(vocabulary)).thenReturn(vocabulary.getUri().toString());

        final MessageAssetFactory.MessageAsset result = sut.create(vocabulary);
        assertEquals(vocabulary.getLabel().get(vocabulary.getPrimaryLanguage()), result.getLabel());
        assertEquals(vocabulary.getUri().toString(), result.getLink());
    }

    @Test
    void createAddsTermVocabularyLabelInParenthesesAsMessageAssetLabel() {
        final String vocabularyLabel = "Vocabulary " + Generator.randomInt(0, 1000);
        final Term term = Generator.generateTermWithId();
        when(linkBuilder.linkTo(term)).thenReturn(term.getUri().toString());
        when(dataService.getLabel(term.getUri(), null)).thenReturn(Optional.of(getPrimaryLabel(term)));
        when(dataService.getLabel(term.getVocabulary(), null)).thenReturn(Optional.of(vocabularyLabel));

        final MessageAssetFactory.MessageAsset result = sut.create(term);
        assertEquals(getPrimaryLabel(term) + " (" + vocabularyLabel + ")", result.getLabel());
        assertEquals(term.getUri().toString(), result.getLink());
    }
}
