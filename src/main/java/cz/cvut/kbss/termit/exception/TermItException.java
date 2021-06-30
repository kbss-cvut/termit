/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.exception;

/**
 * Application-specific exception.
 * <p>
 * All exceptions related to the application should be subclasses of this one.
 */
public class TermItException extends RuntimeException {

    protected final boolean suppressLogging;

    protected TermItException() {
        this.suppressLogging = false;
    }

    public TermItException(String message) {
        this(message, false);
    }

    public TermItException(String message, boolean suppressLogging) {
        super(message);
        this.suppressLogging = suppressLogging;
    }

    public TermItException(String message, Throwable cause) {
        super(message, cause);
        this.suppressLogging = false;
    }

    public TermItException(Throwable cause) {
        super(cause);
        this.suppressLogging = false;
    }

    /**
     * Whether logging of this exception should be suppressed.
     *
     * @return True if exception should not be logged
     */
    public boolean shouldSuppressLogging() {
        return suppressLogging;
    }
}
