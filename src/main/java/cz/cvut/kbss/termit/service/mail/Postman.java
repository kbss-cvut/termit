/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.mail;

import cz.cvut.kbss.termit.exception.PostmanException;
import cz.cvut.kbss.termit.exception.ValidationException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Service
public class Postman {

    private static final Logger LOG = LoggerFactory.getLogger(Postman.class);

    private final Environment env;

    @Value("${spring.mail.username:#{null}}")
    private String senderUsername;

    @Value("${termit.mail.sender:#{null}}")
    private String sender;

    /**
     * Name used as sender of the emails.
     */
    public static final String FROM_NICKNAME = "TermIt Postman";

    private final JavaMailSender mailSender;

    @Autowired
    public Postman(Environment env, @Autowired(required = false) JavaMailSender mailSender) {
        this.env = env;
        this.mailSender = mailSender;
    }

    @PostConstruct
    public void postConstruct() {
        if(mailSender == null) {
            throw new ValidationException("Mail server not configured.");
        }
    }

    /**
     * Sends an email message according to the specification.
     *
     * @param message Mail specification
     */
    public void sendMessage(Message message) {
        if (mailSender == null) {
            LOG.warn("Mail server not configured. Cannot send message {}.", message);
            return;
        }
        Objects.requireNonNull(message);
        try {
            LOG.debug("Sending mail: {}", message);

            final MimeMessage mail = mailSender.createMimeMessage();
            final MimeMessageHelper helper = new MimeMessageHelper(mail, true);
            helper.setFrom(new InternetAddress(sender != null ? sender : senderUsername, FROM_NICKNAME, StandardCharsets.UTF_8.toString()));
            helper.setTo(message.getRecipients().toArray(new String[]{}));
            helper.setSubject(message.getSubject());
            helper.setText(message.getContent(), true);

            addAttachments(message, helper);

            mailSender.send(mail);

            LOG.trace("Mail successfully sent.");
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            LOG.error("Unable to send message.", e);
            throw new PostmanException("Unable to send message.", e);
        }
    }

    private void addAttachments(Message message, MimeMessageHelper helper) {
        message.getAttachments().forEach(f -> {
            try {
                helper.addAttachment(f.getName(), f);
            } catch (MessagingException e) {
                throw new PostmanException("Unable to add attachments to message.", e);
            }
        });
    }
}
