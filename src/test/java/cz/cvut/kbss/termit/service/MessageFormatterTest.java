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
