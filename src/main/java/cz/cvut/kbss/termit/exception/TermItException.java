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
package cz.cvut.kbss.termit.exception;

/**
 * Application-specific exception.
 * <p>
 * All exceptions related to the application should be subclasses of this one.
 */
public class TermItException extends RuntimeException {

    /**
     * Identifier of localized frontend message.
     */
    private String messageId;

    protected TermItException() {
    }

    public TermItException(String message) {
        super(message);
    }

    public TermItException(String message, String messageId) {
        super(message);
        this.messageId = messageId;
    }

    public TermItException(String message, Throwable cause) {
        super(message, cause);
    }

    public TermItException(String message, String messageId, Throwable cause) {
        super(message, cause);
        this.messageId = messageId;
    }

    public TermItException(Throwable cause) {
        super(cause);
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
