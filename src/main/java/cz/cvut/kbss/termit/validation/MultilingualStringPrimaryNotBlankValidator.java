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
package cz.cvut.kbss.termit.validation;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Configuration.Persistence;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Validates that a {@link MultilingualString} contains translation in the primary language.
 * <p>
 * Primary language is given by the application's configuration ({@link Persistence#getLanguage()}).
 */
public class MultilingualStringPrimaryNotBlankValidator
        implements ConstraintValidator<PrimaryNotBlank, MultilingualString> {

    @Autowired
    private Configuration config;

    @Override
    public boolean isValid(MultilingualString multilingualString,
                           ConstraintValidatorContext constraintValidatorContext) {
        if (multilingualString == null) {
            return false;
        }
        return multilingualString.contains(config.getPersistence().getLanguage()) &&
                !multilingualString.get(config.getPersistence().getLanguage()).trim().isEmpty();
    }
}
