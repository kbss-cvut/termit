package cz.cvut.kbss.termit.websocket;

import cz.cvut.kbss.termit.util.WebSocketMessageWithHeadersValueHandler;
import cz.cvut.kbss.termit.websocket.util.TestAnnotationMethodHandler;
import cz.cvut.kbss.termit.websocket.util.TestMessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public abstract class BaseWebSocketControllerTestRunner {

    protected TestMessageChannel clientOutboundChannel;

    protected TestAnnotationMethodHandler annotationMethodHandler;

    @BeforeEach
    public void setup() {
        this.clientOutboundChannel = new TestMessageChannel();
        this.annotationMethodHandler = new TestAnnotationMethodHandler(new TestMessageChannel(), clientOutboundChannel, new SimpMessagingTemplate(new TestMessageChannel()));
        this.annotationMethodHandler.setDestinationPrefixes(List.of("/"));
        this.annotationMethodHandler.setMessageConverter(new MappingJackson2MessageConverter());
        this.annotationMethodHandler.setApplicationContext(new StaticApplicationContext());
        this.annotationMethodHandler.setCustomReturnValueHandlers(List.of(new WebSocketMessageWithHeadersValueHandler(new SimpMessagingTemplate(clientOutboundChannel))));
        this.annotationMethodHandler.afterPropertiesSet();
    }

    protected void registerController(Object controller) {
        this.annotationMethodHandler.registerHandler(controller);
    }
}
