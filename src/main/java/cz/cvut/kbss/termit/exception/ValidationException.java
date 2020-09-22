/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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

import cz.cvut.kbss.termit.validation.ValidationResult;

import java.util.stream.Collectors;

/**
 * Indicates that invalid data have been passed to the application.
 * <p>
 * The exception message should provide information as to what data are invalid and why.
 */
public class ValidationException extends TermItException {

    private final ValidationResult<?> validationResult;

    public ValidationException(String message) {
        super(message);
        this.validationResult = null;
    }

    public ValidationException(ValidationResult<?> validationResult) {
        assert !validationResult.isValid();
        this.validationResult = validationResult;
    }

    @Override
    public String getMessage() {
        if (validationResult == null) {
            return super.getMessage();
        }
        return String.join("\n",
                validationResult.getViolations().stream()
                                .map(cv -> "Value of " + cv.getRootBeanClass().getSimpleName() + "." +
                                        cv.getPropertyPath() + " " + cv.getMessage())
                                .collect(Collectors.toSet()));
    }
}
