package cz.cvut.kbss.termit.service.mail;

import cz.cvut.kbss.termit.exception.PostmanException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Service
public class Postman {

    private static final Logger LOG = LoggerFactory.getLogger(Postman.class);

    @Value("${spring.mail.username:#{null}}")
    private String senderUsername;

    @Value("${termit.mail.sender:#{null}}")
    private String sender;

    /**
     * Name used as sender of the emails.
     */
    public static final String FROM_NICKNAME = "TermIt Postman";

    private final JavaMailSender mailSender;

    public Postman(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
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
