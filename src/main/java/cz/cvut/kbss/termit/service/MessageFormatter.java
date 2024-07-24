package cz.cvut.kbss.termit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Formats internationalized messages.
 * <p>
 * The message bundles should be located in {@code src/main/resources/i18n} and be called {@code messages}.
 */
public class MessageFormatter {

    private static final Logger LOG = LoggerFactory.getLogger(MessageFormatter.class);

    private final ResourceBundle messages;

    public MessageFormatter(@NonNull String lang) {
        Objects.requireNonNull(lang);
        final Locale locale = new Locale(lang);
        this.messages = ResourceBundle.getBundle("i18n/messages", locale);
        LOG.info("Loaded message bundle '{}_{}'.", messages.getBaseBundleName(), locale);
    }

    /**
     * Formats message with the specified identifier using the specified parameters.
     *
     * @param messageId Message identifier
     * @param params    Parameters to substitute into the message string
     * @return Formatted message
     */
    public String formatMessage(@NonNull String messageId, Object... params) {
        Objects.requireNonNull(messageId);
        try {
            final MessageFormat formatter = new MessageFormat(messages.getString(messageId));
            return formatter.format(params);
        } catch (MissingResourceException e) {
            LOG.error("No message found for message id '{}'. Returning the message id.", messageId, e);
            return messageId;
        }
    }
}
