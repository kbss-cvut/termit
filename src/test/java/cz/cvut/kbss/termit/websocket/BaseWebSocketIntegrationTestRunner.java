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
import cz.cvut.kbss.termit.websocket.handler.StompExceptionHandler;
import cz.cvut.kbss.termit.websocket.handler.WebSocketExceptionHandler;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.aspectj.EnableSpringConfigured;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
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

@ActiveProfiles("test")
@EnableSpringConfigured
@EnableAutoConfiguration
@EnableTransactionManagement
@ExtendWith(MockitoExtension.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
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

    @SpyBean
    protected WebSocketExceptionHandler webSocketExceptionHandler;

    @SpyBean
    protected StompExceptionHandler stompExceptionHandler;

    @MockBean
    protected LongRunningTasksRegistry longRunningTasksRegistry;

    protected WebSocketStompClient stompClient;

    @Value("ws://localhost:${local.server.port}/ws")
    protected String url;

    @SpyBean
    protected TermItUserDetailsService userDetailsService;

    @SpyBean
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
        verifyNoMoreInteractions(webSocketExceptionHandler, stompExceptionHandler);
    }

    protected class TestWebSocketSessionHandler implements WebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            LOG.info("WebSocket connection established");
        }

        @Override
        public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
            LOG.info("WebSocket message received: {}", message.getPayload());
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            LOG.error("WebSocket transport error", exception);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
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
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            super.afterConnected(session, connectedHeaders);
            LOG.info("STOMP session connected");
        }

        @Override
        public void handleFrame(@NotNull StompHeaders headers, Object payload) {
            super.handleFrame(headers, payload);
            exception.set(new Exception(headers.toString()));
            LOG.error("STOMP frame: {}", headers);
        }

        @Override
        public void handleException(@NotNull StompSession session, StompCommand command, @NotNull StompHeaders headers,
                                    byte @NotNull [] payload, @NotNull Throwable exception) {
            super.handleException(session, command, headers, payload, exception);
            this.exception.set(exception);
            LOG.error("STOMP exception", exception);
        }

        @Override
        public void handleTransportError(@NotNull StompSession session, @NotNull Throwable exception) {
            super.handleTransportError(session, exception);
            this.exception.set(exception);
            LOG.error("STOMP transport error", exception);
        }

        public Throwable getException() {
            return exception.get();
        }
    }
}
