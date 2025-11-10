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

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class WebSocketExceptionHandlerTest extends BaseWebSocketControllerTestRunner {

    @MockitoBean
    VocabularySocketController controller;

    StompHeaderAccessor messageHeaders;

    @BeforeEach
    public void setup() {
        messageHeaders = StompHeaderAccessor.create(StompCommand.MESSAGE);
        messageHeaders.setSessionId("0");
        messageHeaders.setSubscriptionId("0");
        Authentication auth = Environment.setCurrentUser(Generator.generateUserAccountWithPassword());
        messageHeaders.setUser(auth);
        messageHeaders.setSessionAttributes(new HashMap<>());
        messageHeaders.setContentLength(0);
        messageHeaders.setHeader("namespace", "namespace");
        messageHeaders.setDestination("/vocabularies/fragment/validate");
    }

    void sendMessage() {
        this.serverInboundChannel.send(MessageBuilder.withPayload("").setHeaders(messageHeaders).build());
    }

    @Test
    void handlerIsCalledForPersistenceException() {
        final PersistenceException e = new PersistenceException(new Exception("mocked exception"));
        doThrow(e).when(controller).validateVocabulary(any(), any(), any());
        sendMessage();
        verify(webSocketExceptionHandler).persistenceException(notNull(), eq(e));
    }

}
