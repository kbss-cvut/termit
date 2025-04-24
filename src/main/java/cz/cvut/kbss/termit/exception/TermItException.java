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
package cz.cvut.kbss.termit.exception;

import cz.cvut.kbss.termit.util.Utils;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Application-specific exception.
 * <p>
 * All exceptions related to the application should be subclasses of this one.
 */
public class TermItException extends RuntimeException {

    /**
     * Error message identifier.
     * <p>
     * This identifier can be used by the UI to display a corresponding localized error message.
     */
    protected String messageId;

    /**
     * Exception related information
     */
    protected final Map<String, String> parameters = new HashMap<>();

    protected TermItException() {
        messageId = null;
    }

    public TermItException(String message) {
        super(message);
        messageId = null;
    }

    public TermItException(String message, Throwable cause) {
        super(message, cause);
        this.messageId = null;
    }

    public TermItException(Throwable cause) {
        super(cause);
        this.messageId = null;
    }

    public TermItException(String message, String messageId) {
        super(message);
        this.messageId = messageId;
    }

    public TermItException(String message, Throwable cause, String messageId) {
        super(message, cause);
        this.messageId = messageId;
    }

    public TermItException(String message, Throwable cause, String messageId, @Nonnull Map<String, String> parameters) {
        super(message, cause);
        this.messageId = messageId;
        addParameters(parameters);
    }

    public TermItException addParameters(@Nonnull Map<String, String> parameters) {
        this.parameters.putAll(parameters);
        return this;
    }

    public TermItException addParameter(@Nonnull String key, @Nonnull String value) {
        this.parameters.put(key, value);
        return this;
    }

    @Nullable
    public String getMessageId() {
        return messageId;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        String params = Utils.mapToString(parameters);
        return super.toString() +
                (messageId == null ? "" : ", messageId=" + messageId) +
                (params.isBlank() ? "" : ", parameters=" + params);
    }
}
