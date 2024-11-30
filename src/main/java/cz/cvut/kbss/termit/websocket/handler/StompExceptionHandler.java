package cz.cvut.kbss.termit.websocket.handler;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * calls {@link WebSocketExceptionHandler} when possible, otherwise logs exception as error
 */
public class StompExceptionHandler extends StompSubProtocolErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(StompExceptionHandler.class);

    private final WebSocketExceptionHandler webSocketExceptionHandler;

    public StompExceptionHandler(WebSocketExceptionHandler webSocketExceptionHandler) {
        this.webSocketExceptionHandler = webSocketExceptionHandler;
    }

    @Override
    protected @Nonnull Message<byte[]> handleInternal(@Nonnull StompHeaderAccessor errorHeaderAccessor,
                                                      @Nonnull byte[] errorPayload, Throwable cause,
                                                      StompHeaderAccessor clientHeaderAccessor) {
        final Message<?> message = MessageBuilder.withPayload(errorPayload).setHeaders(errorHeaderAccessor).build();
        final boolean handled = webSocketExceptionHandler.delegate(message, cause);

        if (!handled) {
            LOG.error("STOMP sub-protocol exception", cause);
        }

        return super.handleInternal(errorHeaderAccessor, errorPayload, cause, clientHeaderAccessor);
    }
}
