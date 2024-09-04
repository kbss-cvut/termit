package cz.cvut.kbss.termit.websocket.handler;

import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.websocket.ResultWithHeaders;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.support.MissingSessionUserException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public class WebSocketMessageWithHeadersValueHandler implements HandlerMethodReturnValueHandler {

    private final SimpMessagingTemplate simpMessagingTemplate;

    public WebSocketMessageWithHeadersValueHandler(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return ResultWithHeaders.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public void handleReturnValue(Object returnValue, @NotNull MethodParameter returnType,
                                  @NotNull Message<?> message) {
        if (returnValue == null) return;
        if (returnValue instanceof ResultWithHeaders<?> resultWithHeaders) {
            final StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(message);
            resultWithHeaders.headers().forEach(headerAccessor::setNativeHeader);
            if (resultWithHeaders.toUser()) {
                final String sessionId = SimpMessageHeaderAccessor.getSessionId(headerAccessor.toMessageHeaders());
                if (sessionId == null || sessionId.isBlank()) {
                    throw new MissingSessionUserException(message);
                }
                simpMessagingTemplate.convertAndSendToUser(sessionId, resultWithHeaders.destination(), resultWithHeaders.payload(), headerAccessor.toMessageHeaders());
            } else {
                simpMessagingTemplate.convertAndSend(resultWithHeaders.destination(), resultWithHeaders.payload(), headerAccessor.toMessageHeaders());
            }
            return;
        }
        throw new UnsupportedOperationException("Unable to process returned value: " + returnValue + " of type " + returnType.getParameterType() + " from " + returnType.getMethod());
    }
}
