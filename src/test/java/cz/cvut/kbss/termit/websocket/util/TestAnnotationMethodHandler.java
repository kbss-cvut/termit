package cz.cvut.kbss.termit.websocket.util;

import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;

/**
 * An extension of SimpAnnotationMethodMessageHandler that exposes a (public)
 * method for manually registering a controller, rather than having it
 * auto-discovered in the Spring ApplicationContext.
 * @author Rossen Stoyanchev
 */
public class TestAnnotationMethodHandler extends SimpAnnotationMethodMessageHandler {

    public TestAnnotationMethodHandler(SubscribableChannel inChannel, MessageChannel outChannel,
                                       SimpMessageSendingOperations brokerTemplate) {

        super(inChannel, outChannel, brokerTemplate);
    }

    public void registerHandler(Object handler) {
        super.detectHandlerMethods(handler);
    }
}
