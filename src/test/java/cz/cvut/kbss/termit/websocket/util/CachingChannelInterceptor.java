package cz.cvut.kbss.termit.websocket.util;

import org.jetbrains.annotations.NotNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Caches any message sent to the intercepted channel
 */
public class CachingChannelInterceptor implements ChannelInterceptor {

    private final BlockingQueue<Message<?>> messages = new ArrayBlockingQueue<>(100);

    @Override
    public Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel channel) {
        this.messages.add(message);
        return message;
    }

    public void reset() {
        this.messages.clear();
    }

    public List<Message<?>> getMessages() {
        return List.copyOf(messages);
    }
}
