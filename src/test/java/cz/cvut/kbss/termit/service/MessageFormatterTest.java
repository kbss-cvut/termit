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
package cz.cvut.kbss.termit.service;

import cz.cvut.kbss.termit.environment.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageFormatterTest {

    @ParameterizedTest
    @MethodSource("i18nFormattingValues")
    void formatsMessageWithSpecifiedIdUsingSpecifiedValues(String language, String expectedMessage) {
        final MessageFormatter sut = new MessageFormatter(language);
        final String vocabularyLabel = "Test";
        assertEquals(expectedMessage, sut.formatMessage("vocabulary.document.label", vocabularyLabel));
    }

    static Stream<Arguments> i18nFormattingValues() {
        return Stream.of(
                Arguments.of("en", "Document for Test"),
                Arguments.of("cs", "Dokument pro Test")
        );
    }

    @Test
    void formatMessageReturnsMessageIdAndLogsErrorWhenMessageIsNotFound() {
        final String unknownMessageId = "unknownMessage";
        final MessageFormatter sut = new MessageFormatter(Environment.LANGUAGE);
        assertDoesNotThrow(() -> {
            final String result = sut.formatMessage(unknownMessageId, "12345");
            assertEquals(unknownMessageId, result);
        });
    }
}
