package cz.cvut.kbss.termit.websocket.util;

import org.jetbrains.annotations.NotNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.core.MessagePostProcessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;
import java.util.UUID;

public class ReturnValueCollectingSimpMessagingTemplate extends SimpMessagingTemplate {

    public static final String MESSAGE_IDENTIFIER_HEADER = "test-message-id";

    private final Map<UUID, Object> returnedValuesMap;

    public ReturnValueCollectingSimpMessagingTemplate(MessageChannel messageChannel,
                                                      Map<UUID, Object> returnedValuesMap) {
        super(messageChannel);
        this.returnedValuesMap = returnedValuesMap;
    }

    @Override
    protected @NotNull Message<?> doConvert(@NotNull Object payload, Map<String, Object> headers,
                                            MessagePostProcessor postProcessor) {
        final Message<?> converted = super.doConvert(payload, headers, postProcessor);

        UUID id = converted.getHeaders().getId();
        if (id == null) {
            id = UUID.randomUUID();
        }

        returnedValuesMap.put(id, payload);
        return MessageBuilder.fromMessage(converted).copyHeaders(Map.of(MESSAGE_IDENTIFIER_HEADER, id)).build();
    }
}
