package cz.cvut.kbss.termit.util;

import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.HashMap;
import java.util.Map;

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
    public void handleReturnValue(Object returnValue, @NotNull MethodParameter returnType, @NotNull Message<?> message)
            throws Exception {
        final MessageHeaderAccessor originalHeadersAccessor = MessageHeaderAccessor.getAccessor(message);
        if (originalHeadersAccessor != null && returnValue instanceof ResultWithHeaders<?> resultWithHeaders) {
            final Map<String, Object> headers = new HashMap<>();
            headers.putAll(originalHeadersAccessor.toMap());
            headers.putAll(resultWithHeaders.headers());

            simpMessagingTemplate.convertAndSend(resultWithHeaders.destination(), resultWithHeaders.payload(), headers);
            return;
        }
        throw new UnsupportedOperationException("Unable to process returned value: " + returnValue + " of type " + returnType.getParameterType() + " from " + returnType.getMethod());
    }
}
