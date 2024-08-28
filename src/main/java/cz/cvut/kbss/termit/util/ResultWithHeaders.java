package cz.cvut.kbss.termit.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.messaging.handler.annotation.SendTo;

import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper carrying a result from WebSocket controller
 * including the {@link #payload}, {@link #destination} and {@link #headers} for the resulting message.
 * <p>
 * Do not combine with other method-return-value handlers (like {@link SendTo @SendTo})
 *
 * @param payload     The actual result of the method
 * @param destination The destination channel where the message will be sent
 * @param headers     Headers that will overwrite headers in the message.
 * @param <T>         The type of the payload
 * @see WebSocketMessageWithHeadersValueHandler processes results from methods
 */
public record ResultWithHeaders<T>(T payload, @NotNull String destination, @NotNull Map<String, Object> headers) {

    public static <T> ResultWithHeadersBuilder<T> result(T payload) {
        return new ResultWithHeadersBuilder<>(payload);
    }

    public static class ResultWithHeadersBuilder<T> {

        private final T payload;

        private @Nullable Map<String, Object> headers = null;

        private ResultWithHeadersBuilder(T payload) {
            this.payload = payload;
        }

        public ResultWithHeadersBuilder<T> withHeaders(@NotNull Map<String, Object> headers) {
            this.headers = headers;
            return this;
        }

        public ResultWithHeaders<T> sendTo(String destination) {
            return new ResultWithHeaders<>(payload, destination, headers == null ? new HashMap<>() : headers);
        }
    }
}
