package cz.cvut.kbss.termit.service.notification;

import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.service.MessageFormatter;
import cz.cvut.kbss.termit.service.mail.Message;
import cz.cvut.kbss.termit.service.mail.MessageComposer;
import cz.cvut.kbss.termit.service.mail.Postman;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class PasswordChangeNotifier {
    private static final String PASSWORD_RESET_TEMPLATE = "password-change.vm";

    private final Configuration config;
    private final MessageComposer messageComposer;
    private final Postman postman;

    public PasswordChangeNotifier(Configuration configuration, MessageComposer messageComposer, Postman postman) {
        this.config = configuration;
        this.messageComposer = messageComposer;
        this.postman = postman;
    }

    private String buildResetLink(PasswordChangeRequest request) {
        return UriComponentsBuilder.fromHttpUrl(config.getUrl())
                                   .fragment("/reset-password/" +
                                                     request.getToken() + "/" +
                                                     URLEncoder.encode(request.getUri().toString(),
                                                                       StandardCharsets.UTF_8)
                                   ).toUriString();
    }

    /**
     * Creates message from ${@link #PASSWORD_RESET_TEMPLATE} template.
     *
     * @param request Password reset request
     * @return message
     */
    private Message createMessage(PasswordChangeRequest request) {
        Map<String, Object> variables = Map.of(
                "resetLink", buildResetLink(request),
                "username", request.getUserAccount().getUsername(),
                "validity", config.getSecurity().getPasswordChangeRequestValidity()
        );
        return Message.to(request.getUserAccount().getUsername())
                      .content(messageComposer.composeMessage(PASSWORD_RESET_TEMPLATE, variables))
                      .subject(new MessageFormatter(config.getPersistence().getLanguage()).formatMessage(
                              "password-change.email.subject"))
                      .build();
    }

    public void sendEmail(PasswordChangeRequest request) {
        Message message = createMessage(request);
        postman.sendMessage(message);
    }


}
