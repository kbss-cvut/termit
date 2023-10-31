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
package cz.cvut.kbss.termit.validation;

import jakarta.validation.ConstraintViolation;
import java.io.Serializable;
import java.util.Collection;

/**
 * Represents a result of validation using a JSR 380 {@link jakarta.validation.Validator}.
 * <p>
 * The main reason for the existence of this class is that Java generics are not able to cope with the set of constraint
 * violations of some type produced by the validator.
 *
 * @param <T> The validated type
 */
public class ValidationResult<T> implements Serializable {

    private final Collection<ConstraintViolation<T>> violations;

    private ValidationResult(Collection<ConstraintViolation<T>> violations) {
        this.violations = violations;
    }

    public Collection<ConstraintViolation<T>> getViolations() {
        return violations;
    }

    public boolean isValid() {
        return violations.isEmpty();
    }

    public static <T> ValidationResult<T> of(Collection<ConstraintViolation<T>> violations) {
        return new ValidationResult<>(violations);
    }
}
