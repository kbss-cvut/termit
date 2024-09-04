package cz.cvut.kbss.termit.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.util.Utils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.verify;

class IntegrationWebSocketSecurityTest extends BaseWebSocketIntegrationTestRunner {

    /**
     * The number of seconds after which some operations will time out.
     */
    private static final int OPERATION_TIMEOUT = 15;

    @Autowired
    ObjectMapper objectMapper;

    /**
     * @return Stream of argument pairs with StompCommand (CONNECT & DISCONNECT excluded) and true + false value for each command
     */
    public static Stream<Arguments> stompCommands() {
        return Arrays.stream(StompCommand.values()).filter(c -> c != StompCommand.CONNECT && c != StompCommand.DISCONNECT)
                     .map(Enum::name)
                     .flatMap(name -> Stream.of(Arguments.of(name, true), Arguments.of(name, false)));
    }

    /**
     * Ensures that connection is closed on receiving any message other than CONNECT
     * (even with valid authorization token)
     */
    @ParameterizedTest
    @MethodSource("stompCommands")
    void connectionIsClosedOnAnyMessageBeforeConnect(String stompCommand, Boolean withAuth) throws Exception {
        final AtomicBoolean receivedReply = new AtomicBoolean(false);
        final AtomicBoolean receivedError = new AtomicBoolean(false);

        final String auth = withAuth ? HttpHeaders.AUTHORIZATION + ":" + SecurityConstants.JWT_TOKEN_PREFIX + generateToken() + "\n" : "";
        final TextMessage message = new TextMessage(stompCommand + "\n" + auth + "\n\0");

        final WebSocketClient wsClient = new StandardWebSocketClient();
        Future<WebSocketSession> connectFuture = wsClient.execute(makeWebSocketHandler(receivedReply, receivedError), url);

        WebSocketSession session = connectFuture.get(OPERATION_TIMEOUT, TimeUnit.SECONDS);

        assertTrue(session.isOpen());

        session.sendMessage(message);

        await().atMost(OPERATION_TIMEOUT, TimeUnit.SECONDS).until(() -> !session.isOpen());

        assertTrue(receivedError.get());
        assertFalse(session.isOpen());
        assertFalse(receivedReply.get());
        verify(webSocketExceptionHandler).messageDeliveryException(notNull(), notNull());
    }

    WebSocketHandler makeWebSocketHandler(AtomicBoolean receivedReply, AtomicBoolean receivedError) {
        return new TestWebSocketSessionHandler() {
            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                super.handleMessage(session, message);
                if (message instanceof TextMessage textMessage) {
                    final String command = textMessage.getPayload().split("\n")[0];
                    if (command.equals(StompCommand.ERROR.name())) {
                        receivedError.set(true);
                        return;
                    }
                }
                receivedReply.set(true);
                session.close();
            }
        };
    }

    /**
     * STOMP CONNECT message is rejected with invalid auth token
     */
    @Test
    void connectWithInvalidAuthorizationIsRejected() throws Throwable {
        final AtomicBoolean receivedReply = new AtomicBoolean(false);
        final AtomicBoolean receivedError = new AtomicBoolean(false);

        final TextMessage message = new TextMessage(StompCommand.CONNECT + "\n" + HttpHeaders.AUTHORIZATION + ":" + SecurityConstants.JWT_TOKEN_PREFIX + "DefinitelyNotValidToken\n\n\0");

        final WebSocketClient wsClient = new StandardWebSocketClient();
        Future<WebSocketSession> connectFuture = wsClient.execute(makeWebSocketHandler(receivedReply, receivedError), url);

        WebSocketSession session = connectFuture.get(OPERATION_TIMEOUT, TimeUnit.SECONDS);

        assertTrue(session.isOpen());

        session.sendMessage(message);

        await().atMost(OPERATION_TIMEOUT, TimeUnit.SECONDS).until(() -> !session.isOpen());

        assertTrue(receivedError.get());
        assertFalse(session.isOpen());
        assertFalse(receivedReply.get());
        verify(webSocketExceptionHandler).messageDeliveryException(notNull(), notNull());
    }

    /**
     * STOMP CONNECT message is rejected with invalid JWT token
     */
    @Test
    void connectWithInvalidJwtAuthorizationIsRejected() throws Throwable {
        final AtomicBoolean receivedReply = new AtomicBoolean(false);
        final AtomicBoolean receivedError = new AtomicBoolean(false);

        final Instant issued = Utils.timestamp();
        // creates "valid" JWT token but with invalid signature
        final String token = Jwts.builder().setSubject(userDetails.getUser().getUsername())
                                 .setId(userDetails.getUser().getUri().toString()).setIssuedAt(Date.from(issued))
                                 .setExpiration(Date.from(issued.plusMillis(SecurityConstants.SESSION_TIMEOUT)))
                                 .signWith(Keys.hmacShaKeyFor("my very secure and really private key".getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                                 .serializeToJsonWith(new JacksonSerializer<>(objectMapper)).compact();

        final TextMessage message = new TextMessage(StompCommand.CONNECT + "\n" + HttpHeaders.AUTHORIZATION + ":" + SecurityConstants.JWT_TOKEN_PREFIX + token + "\n\n\0");

        final WebSocketClient wsClient = new StandardWebSocketClient();
        Future<WebSocketSession> connectFuture = wsClient.execute(makeWebSocketHandler(receivedReply, receivedError), url);

        WebSocketSession session = connectFuture.get(OPERATION_TIMEOUT, TimeUnit.SECONDS);

        assertTrue(session.isOpen());

        session.sendMessage(message);

        await().atMost(OPERATION_TIMEOUT, TimeUnit.SECONDS).until(() -> !session.isOpen());

        assertTrue(receivedError.get());
        assertFalse(session.isOpen());
        assertFalse(receivedReply.get());

        verify(webSocketExceptionHandler).messageDeliveryException(notNull(), notNull());
    }

    /**
     * Checks that it is possible to establish STOMP connection with valid authorization
     */
    @Test
    void connectionIsNotClosedWhenConnectMessageIsSent() throws Throwable {
        final StompSessionHandler handler = new TestStompSessionHandler();

        final StompHeaders headers = new StompHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, SecurityConstants.JWT_TOKEN_PREFIX + generateToken());

        Future<StompSession> connectFuture = stompClient.connectAsync(URI.create(url), null, headers, handler);

        StompSession session = connectFuture.get(OPERATION_TIMEOUT, TimeUnit.SECONDS);
        assertTrue(session.isConnected());
        session.disconnect();
        await().atMost(OPERATION_TIMEOUT, TimeUnit.SECONDS).until(() -> !session.isConnected());
    }
}
