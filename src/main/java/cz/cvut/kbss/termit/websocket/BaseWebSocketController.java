package cz.cvut.kbss.termit.websocket;

import cz.cvut.kbss.termit.rest.BaseController;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import jakarta.annotation.Nonnull;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.DestinationUserNameProvider;
import org.springframework.util.LinkedMultiValueMap;

import java.security.Principal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.springframework.messaging.support.NativeMessageHeaderAccessor.NATIVE_HEADERS;

public class BaseWebSocketController extends BaseController {

    protected final SimpMessagingTemplate messagingTemplate;

    protected BaseWebSocketController(IdentifierResolver idResolver, Configuration config,
                                      SimpMessagingTemplate messagingTemplate) {
        super(idResolver, config);
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Resolves session id, when present, and sends to the specific session.
     * When session id is not present, sends it to all sessions of specific user.
     *
     * @param destination   the destination (without user prefix)
     * @param payload       payload to send
     * @param replyHeaders  native headers for the reply
     * @param sourceHeaders original headers containing session id or name of the user
     */
    protected void sendToSession(@Nonnull String destination, @Nonnull Object payload,
                                 @Nonnull Map<String, Object> replyHeaders, @Nonnull MessageHeaders sourceHeaders) {
        getSessionId(sourceHeaders)
                .ifPresentOrElse(sessionId -> { // session id present
                            StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
                            // add reply headers as native headers
                            headerAccessor.setHeader(NATIVE_HEADERS, new LinkedMultiValueMap<>(replyHeaders.size()));
                            replyHeaders.forEach((name, value) -> headerAccessor.addNativeHeader(name, Objects.toString(value)));
                            headerAccessor.setSessionId(sessionId); // pass session id to new headers
                            // send to user session
                            messagingTemplate.convertAndSendToUser(sessionId, destination, payload, headerAccessor.toMessageHeaders());
                        },
                        // session id not present, send to all user sessions
                        () -> getUser(sourceHeaders).ifPresent(user -> messagingTemplate.convertAndSendToUser(user, destination, payload, replyHeaders))
                );
    }

    /**
     * Resolves name which can be used to send a message to the user with {@link SimpMessagingTemplate#convertAndSendToUser}.
     *
     * @return name or session id, or empty when information is not available.
     */
    protected @Nonnull Optional<String> getUser(@Nonnull MessageHeaders messageHeaders) {
        return getUserName(messageHeaders).or(() -> getSessionId(messageHeaders));
    }

    private @Nonnull Optional<String> getSessionId(@Nonnull MessageHeaders messageHeaders) {
        return Optional.ofNullable(SimpMessageHeaderAccessor.getSessionId(messageHeaders));
    }

    /**
     * Resolves the name of the user
     *
     * @return the name or null
     */
    private @Nonnull Optional<String> getUserName(MessageHeaders headers) {
        Principal principal = SimpMessageHeaderAccessor.getUser(headers);
        if (principal != null) {
            final String name = (principal instanceof DestinationUserNameProvider provider ?
                    provider.getDestinationUserName() : principal.getName());
            return Optional.ofNullable(name);
        }
        return Optional.empty();
    }
}
