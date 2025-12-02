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

import cz.cvut.kbss.termit.config.AppConfig;
import cz.cvut.kbss.termit.config.SecurityConfig;
import cz.cvut.kbss.termit.config.WebAppConfig;
import cz.cvut.kbss.termit.config.WebSocketConfig;
import cz.cvut.kbss.termit.config.WebSocketMessageBrokerConfig;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestConfig;
import cz.cvut.kbss.termit.environment.config.TestPersistenceConfig;
import cz.cvut.kbss.termit.environment.config.TestServiceConfig;
import cz.cvut.kbss.termit.security.JwtUtils;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.service.security.TermItUserDetailsService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTasksRegistry;
import cz.cvut.kbss.termit.websocket.handler.WebSocketExceptionHandler;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Execution(ExecutionMode.SAME_THREAD)
@ActiveProfiles("test")
@EnableAutoConfiguration
@EnableTransactionManagement
@ExtendWith(MockitoExtension.class)
@EnableAspectJAutoProxy
@EnableConfigurationProperties({Configuration.class})
@ContextConfiguration(
        classes = {TestConfig.class, TestPersistenceConfig.class, TestServiceConfig.class, AppConfig.class,
                   SecurityConfig.class, WebAppConfig.class, WebSocketConfig.class, WebSocketMessageBrokerConfig.class},
        initializers = {ConfigDataApplicationContextInitializer.class})
@ComponentScan(
        {"cz.cvut.kbss.termit.security", "cz.cvut.kbss.termit.websocket", "cz.cvut.kbss.termit.websocket.handler"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseWebSocketIntegrationTestRunner {

    protected Logger LOG = LoggerFactory.getLogger(this.getClass());

    @MockitoSpyBean
    protected WebSocketExceptionHandler webSocketExceptionHandler;

    @MockitoBean
    protected LongRunningTasksRegistry longRunningTasksRegistry;

    protected WebSocketStompClient stompClient;

    @Value("ws://localhost:${local.server.port}/ws")
    protected String url;

    @MockitoSpyBean
    protected TermItUserDetailsService userDetailsService;

    @MockitoSpyBean
    protected JwtUtils jwtUtils;

    protected TermItUserDetails userDetails;

    protected String generateToken() {
        return jwtUtils.generateToken(userDetails.getUser(), userDetails.getAuthorities());
    }

    @BeforeEach
    void runnerSetup() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        userDetails = new TermItUserDetails(Generator.generateUserAccountWithPassword());
        doReturn(userDetails).when(userDetailsService).loadUserByUsername(userDetails.getUsername());
    }

    @AfterEach
    protected void runnerAfterEach() {
        verifyNoMoreInteractions(webSocketExceptionHandler);
    }

    protected class TestWebSocketSessionHandler implements WebSocketHandler {

        @Override
        public void afterConnectionEstablished(@Nonnull WebSocketSession session) {
            LOG.info("WebSocket connection established");
        }

        @Override
        public void handleMessage(@Nonnull WebSocketSession session, WebSocketMessage<?> message) throws Exception {
            LOG.info("WebSocket message received: {}", message.getPayload());
        }

        @Override
        public void handleTransportError(@Nonnull WebSocketSession session, @Nonnull Throwable exception) {
            LOG.error("WebSocket transport error", exception);
        }

        @Override
        public void afterConnectionClosed(@Nonnull WebSocketSession session, @Nonnull CloseStatus closeStatus) {
            LOG.info("WebSocket connection closed");
        }

        @Override
        public boolean supportsPartialMessages() {
            return false;
        }
    }

    protected class TestStompSessionHandler extends StompSessionHandlerAdapter {

        private final AtomicReference<Throwable> exception = new AtomicReference<>();

        @Override
        public void afterConnected(@Nonnull StompSession session, @Nonnull StompHeaders connectedHeaders) {
            super.afterConnected(session, connectedHeaders);
            LOG.info("STOMP session connected");
        }

        @Override
        public void handleFrame(@Nonnull StompHeaders headers, Object payload) {
            super.handleFrame(headers, payload);
            exception.set(new Exception(headers.toString()));
            LOG.error("STOMP frame: {}", headers);
        }

        @Override
        public void handleException(@Nonnull StompSession session, StompCommand command, @Nonnull StompHeaders headers,
                                    @Nonnull byte[] payload, @Nonnull Throwable exception) {
            super.handleException(session, command, headers, payload, exception);
            this.exception.set(exception);
            LOG.error("STOMP exception", exception);
        }

        @Override
        public void handleTransportError(@Nonnull StompSession session, @Nonnull Throwable exception) {
            super.handleTransportError(session, exception);
            this.exception.set(exception);
            LOG.error("STOMP transport error", exception);
        }

        public Throwable getException() {
            return exception.get();
        }
    }
}
