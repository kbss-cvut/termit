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

import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.validation.ValidationResult;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidationExceptionTest {

    @Test
    void getMessageReturnsMessageWhenItWasSpecified() {
        final String msg = "I have a bad feeling about this.";
        final ValidationException ex = new ValidationException(msg);
        assertEquals(msg, ex.getMessage());
    }

    @Test
    void getMessageReturnsConcatenatedConstraintViolationMessages() {
        final UserAccount u = new UserAccount();
        u.setFirstName("test");
        final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        final ValidationResult<UserAccount> violations = ValidationResult.of(validator.validate(u));
        final ValidationException ex = new ValidationException(violations);
        final String result = ex.getMessage();
        assertAll(() -> assertThat(result, containsString("username")),
                () -> assertThat(result, containsString("lastName")));
    }
}
