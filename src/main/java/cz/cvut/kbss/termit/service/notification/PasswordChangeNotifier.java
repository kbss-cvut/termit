/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
    private static final String CREATE_PASSWORD_TEMPLATE = "password-create.vm";

    private final Configuration config;
    private final MessageComposer messageComposer;
    private final Postman postman;

    public PasswordChangeNotifier(Configuration configuration, MessageComposer messageComposer, Postman postman) {
        this.config = configuration;
        this.messageComposer = messageComposer;
        this.postman = postman;
    }

    private String buildPasswordResetLink(PasswordChangeRequest request) {
        return UriComponentsBuilder.fromHttpUrl(config.getUrl())
                                   .fragment("/reset-password/" +
                                                     request.getToken() + "/" +
                                                     URLEncoder.encode(request.getUri().toString(),
                                                                       StandardCharsets.UTF_8)
                                   ).toUriString();
    }

    private String buildCreatePasswordLink(PasswordChangeRequest request) {
        return UriComponentsBuilder.fromHttpUrl(config.getUrl())
                                   .fragment("/create-password/" +
                                           request.getToken() + "/" +
                                           URLEncoder.encode(request.getUri().toString(),
                                                   StandardCharsets.UTF_8)
                                   ).toUriString();
    }

    /**
     * Creates a message from specified template and frontend link.
     *
     * @param request Password reset request
     * @param frontendLink a link to the frontend, mapped as {@code resetLink} for the template
     * @param templateName Name of the template
     * @return message
     */
    private Message createMessage(PasswordChangeRequest request, String frontendLink, String templateName) {
        Map<String, Object> variables = Map.of(
                "resetLink", frontendLink,
                "username", request.getUserAccount().getUsername(),
                "validity", config.getSecurity().getPasswordChangeRequestValidity()
        );
        return Message.to(request.getUserAccount().getUsername())
                      .content(messageComposer.composeMessage(templateName, variables))
                      .subject(new MessageFormatter(config.getPersistence().getLanguage()).formatMessage(
                              "password-change.email.subject"))
                      .build();
    }

    public void sendPasswordResetEmail(PasswordChangeRequest request) {
        Message message = createMessage(request, buildPasswordResetLink(request), PASSWORD_RESET_TEMPLATE);
        postman.sendMessage(message);
    }

    public void sendCreatePasswordEmail(PasswordChangeRequest request) {
        Message message = createMessage(request, buildCreatePasswordLink(request), CREATE_PASSWORD_TEMPLATE);
        postman.sendMessage(message);
    }
}
