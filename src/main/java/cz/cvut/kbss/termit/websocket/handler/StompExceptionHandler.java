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
package cz.cvut.kbss.termit.websocket.handler;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
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
                                                      @Nonnull byte[] errorPayload,
                                                      @Nullable Throwable cause,
                                                      @Nullable StompHeaderAccessor clientHeaderAccessor) {
        final Message<?> message = MessageBuilder.withPayload(errorPayload).setHeaders(errorHeaderAccessor).build();
        Throwable causeToHandle = cause;
        if (causeToHandle != null && causeToHandle.getCause() != null) {
            causeToHandle = causeToHandle.getCause();
        }
        final boolean handled = webSocketExceptionHandler.delegate(message, causeToHandle);

        if (!handled) {
            LOG.error("STOMP sub-protocol exception", cause);
        }

        return super.handleInternal(errorHeaderAccessor, errorPayload, cause, clientHeaderAccessor);
    }
}
