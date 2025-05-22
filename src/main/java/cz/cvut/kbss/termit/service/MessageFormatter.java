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
package cz.cvut.kbss.termit.service;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public MessageFormatter(@Nonnull String lang) {
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
    public String formatMessage(@Nonnull String messageId, Object... params) {
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
