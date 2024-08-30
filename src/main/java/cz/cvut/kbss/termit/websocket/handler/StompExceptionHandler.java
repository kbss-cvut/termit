package cz.cvut.kbss.termit.websocket.handler;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

public class StompExceptionHandler extends StompSubProtocolErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(StompExceptionHandler.class);

    @Override
    protected @NotNull Message<byte[]> handleInternal(@NotNull StompHeaderAccessor errorHeaderAccessor,
                                                      byte @NotNull [] errorPayload,
                                                      Throwable cause, StompHeaderAccessor clientHeaderAccessor) {
        LOG.error("STOMP sub-protocol exception", cause);
        return super.handleInternal(errorHeaderAccessor, errorPayload, cause, clientHeaderAccessor);
    }
}
