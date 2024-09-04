package cz.cvut.kbss.termit.websocket.handler;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
    protected @NotNull Message<byte[]> handleInternal(@NotNull StompHeaderAccessor errorHeaderAccessor,
                                                      byte @NotNull [] errorPayload, Throwable cause,
                                                      StompHeaderAccessor clientHeaderAccessor) {
        final Message<?> message = MessageBuilder.withPayload(errorPayload).setHeaders(errorHeaderAccessor).build();
        boolean handled = false;
        try {
            handled = delegate(message, cause);
        } catch (InvocationTargetException e) {
            LOG.error("Exception thrown during exception handler invocation", e);
        } catch (IllegalAccessException unexpected) {
            // is checked by delegate
        }

        if (!handled) {
            LOG.error("STOMP sub-protocol exception", cause);
        }

        return super.handleInternal(errorHeaderAccessor, errorPayload, cause, clientHeaderAccessor);
    }

    /**
     * Tries to match method on {@link #webSocketExceptionHandler}
     *
     * @return true when a method was found and called, false otherwise
     * @throws IllegalArgumentException never
     */
    private boolean delegate(Message<?> message, Throwable throwable)
            throws InvocationTargetException, IllegalAccessException {
        if (throwable instanceof Exception exception) {
            Method[] methods = webSocketExceptionHandler.getClass().getMethods();
            for (final Method method : methods) {
                if (!method.canAccess(webSocketExceptionHandler)) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 2) {
                    continue;
                }
                if (params[0].isAssignableFrom(message.getClass()) && params[1].isAssignableFrom(exception.getClass())) {
                    // message, exception
                    method.invoke(webSocketExceptionHandler, message, exception);
                    return true;
                } else if (params[0].isAssignableFrom(exception.getClass()) && params[1].isAssignableFrom(message.getClass())) {
                    // exception, message
                    method.invoke(webSocketExceptionHandler, exception, message);
                    return true;
                }
            }
        }
        return false;
    }
}
