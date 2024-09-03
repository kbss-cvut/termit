package cz.cvut.kbss.termit.websocket;

import cz.cvut.kbss.termit.websocket.handler.WebSocketMessageWithHeadersValueHandler;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper carrying a result from WebSocket controller
 * including the {@link #payload}, {@link #destination} and {@link #headers} for the resulting message.
 * <p>
 * Do not combine with other method-return-value handlers (like {@link SendTo @SendTo})
 * <p>
 * The {@code ResultWithHeaders} is then handled by {@link WebSocketMessageWithHeadersValueHandler}.
 * Every value returned from a controller method
 * can be handled only by a single {@link HandlerMethodReturnValueHandler}.
 * Annotations like {@link SendTo @SendTo}/{@link SendToUser @SendToUser}
 * are handled by separate return value handlers, so only one can be used simultaneously.
 *
 * @param payload     The actual result of the method
 * @param destination The destination channel where the message will be sent
 * @param headers     Headers that will overwrite headers in the message.
 * @param <T>         The type of the payload
 * @see WebSocketMessageWithHeadersValueHandler
 * @see HandlerMethodReturnValueHandler
 */
public record ResultWithHeaders<T>(T payload, @NotNull String destination, @NotNull Map<String, String> headers,
                                   boolean toUser) {

    public static <T> ResultWithHeadersBuilder<T> result(T payload) {
        return new ResultWithHeadersBuilder<>(payload);
    }

    public static class ResultWithHeadersBuilder<T> {

        private final T payload;

        private @Nullable Map<String, String> headers = null;

        private ResultWithHeadersBuilder(T payload) {
            this.payload = payload;
        }

        /**
         * All values will be mapped to strings with {@link Object#toString()}
         */
        public ResultWithHeadersBuilder<T> withHeaders(@NotNull Map<String, Object> headers) {
            this.headers = new HashMap<>();
            headers.forEach((key, value) -> this.headers.put(key, value.toString()));
            this.headers = Collections.unmodifiableMap(this.headers);
            return this;
        }

        public ResultWithHeaders<T> sendTo(String destination) {
            return new ResultWithHeaders<>(payload, destination, headers == null ? Map.of() : headers, false);
        }

        public ResultWithHeaders<T> sendToUser(String userDestination) {
            return new ResultWithHeaders<>(payload, userDestination, headers == null ? Map.of() : headers, true);
        }
    }
}
