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
package cz.cvut.kbss.termit.websocket;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.util.throttle.ThrottledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class VocabularySocketControllerTest extends BaseWebSocketControllerTestRunner {

    @MockBean
    VocabularyService vocabularyService;

    @SpyBean
    VocabularySocketController sut;

    Vocabulary vocabulary;

    String fragment;

    String namespace;

    StompHeaderAccessor messageHeaders;

    @BeforeEach
    public void setup() {
        vocabulary = Generator.generateVocabularyWithId();
        fragment = IdentifierResolver.extractIdentifierFragment(vocabulary.getUri()).substring(1);
        namespace = vocabulary.getUri().toString().substring(0, vocabulary.getUri().toString().lastIndexOf('/'));
        when(identifierResolver.resolveIdentifier(namespace, fragment)).thenReturn(vocabulary.getUri());
        when(vocabularyService.getReference(vocabulary.getUri())).thenReturn(vocabulary);
        when(vocabularyService.validateContents(vocabulary.getUri())).thenReturn(ThrottledFuture.done(List.of()));

        messageHeaders = StompHeaderAccessor.create(StompCommand.MESSAGE);
        messageHeaders.setSessionId("0");
        messageHeaders.setSubscriptionId("0");
        Authentication auth = Environment.setCurrentUser(Generator.generateUserAccountWithPassword());
        messageHeaders.setUser(auth);
        messageHeaders.setSessionAttributes(new HashMap<>());
    }

    @Test
    void validateVocabularyValidatesContents() {
        messageHeaders.setContentLength(0);
        messageHeaders.setHeader("namespace", namespace);
        messageHeaders.setDestination("/vocabularies/" + fragment + "/validate");

        this.serverInboundChannel.send(MessageBuilder.withPayload("").setHeaders(messageHeaders).build());

        verify(vocabularyService).validateContents(vocabulary.getUri());
    }

    @Test
    void validateVocabularyReturnsValidationResults() {
        messageHeaders.setContentLength(0);
        messageHeaders.setHeader("namespace", namespace);
        messageHeaders.setDestination("/vocabularies/" + fragment + "/validate");

        final ValidationResult validationResult = new ValidationResult().setTermUri(Generator.generateUri())
                                                                        .setResultPath(Generator.generateUri())
                                                                        .setMessage(MultilingualString.create("message", "en"))
                                                                        .setSeverity(Generator.generateUri())
                                                                        .setIssueCauseUri(Generator.generateUri());
        final List<ValidationResult> validationResults = List.of(validationResult);
        when(vocabularyService.validateContents(vocabulary.getUri())).thenReturn(ThrottledFuture.done(validationResults));

        this.serverInboundChannel.send(MessageBuilder.withPayload("").setHeaders(messageHeaders).build());

        assertEquals(1, this.brokerChannelInterceptor.getMessages().size());
        Message<?> reply = this.brokerChannelInterceptor.getMessages().get(0);

        assertNotNull(reply);
        StompHeaderAccessor replyHeaders = StompHeaderAccessor.wrap(reply);
        // as reply is sent to a common channel for all vocabularies, there must be header with vocabulary uri
        assertEquals(vocabulary.getUri().toString(), replyHeaders.getFirstNativeHeader("vocabulary"), "Invalid or missing vocabulary header in the reply");

        Optional<List<ValidationResult>> payload = readPayload(reply);
        assertTrue(payload.isPresent());
        assertEquals(validationResults, payload.get());
    }
}
