package cz.cvut.kbss.termit.service.mail;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Representation of an email message used for email composition.
 */
public class Message {
    private final List<String> recipients;

    private final String subject;

    private final String content;

    private final List<File> attachments;

    private Message(MessageBuilder builder) {
        assert !builder.recipients.isEmpty();
        this.recipients = new ArrayList<>(builder.recipients);
        this.subject = builder.subject;
        this.content = builder.content;
        this.attachments = builder.attachments;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public String getSubject() {
        return subject;
    }

    public String getContent() {
        return content;
    }

    public List<File> getAttachments() {
        return attachments;
    }

    /**
     * Creates a {@link MessageBuilder} initialized with the specified recipient.
     *
     * @param recipient Message recipient
     * @return MessageBuilder instance
     */
    public static MessageBuilder to(String recipient) {
        return new MessageBuilder().recipient(recipient);
    }

    @Override
    public String toString() {
        return "Message{" +
                "recipients='" + recipients + '\'' +
                ", subject='" + subject + '\'' +
                '}';
    }

    public static class MessageBuilder {
        private final List<String> recipients = new ArrayList<>();
        private String subject;
        private String content;
        private final List<File> attachments = new ArrayList<>();

        public MessageBuilder recipient(String recipient) {
            recipients.add(Objects.requireNonNull(recipient));
            return this;
        }

        public MessageBuilder subject(String subject) {
            this.subject = Objects.requireNonNull(subject);
            return this;
        }

        public MessageBuilder content(String content) {
            this.content = Objects.requireNonNull(content);
            return this;
        }

        public MessageBuilder attach(File file) {
            attachments.add(Objects.requireNonNull(file));
            return this;
        }

        public Message build() {
            return new Message(this);
        }
    }
}
