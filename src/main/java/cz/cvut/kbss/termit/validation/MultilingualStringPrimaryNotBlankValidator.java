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
import cz.cvut.kbss.termit.model.util.validation.HasPrimaryLanguage;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that a {@link MultilingualString} contains translation in the primary language of the entity.
 * <p>
 * The entity must implement {@link HasPrimaryLanguage} to provide the primary language.
 */
public class MultilingualStringPrimaryNotBlankValidator
        implements ConstraintValidator<PrimaryNotBlank, HasPrimaryLanguage> {

    private FieldConstraintExecutor executor;

    @Override
    public void initialize(PrimaryNotBlank constraintAnnotation) {
        this.executor = new FieldConstraintExecutor(PrimaryNotBlank.class, constraintAnnotation.value());
    }

    @Override
    public boolean isValid(HasPrimaryLanguage bean, ConstraintValidatorContext ctx) {
        if (bean == null) {
            return false;
        }
        return executor.validate(bean, ctx, (fieldName, value, c) -> {
            if (isValid((MultilingualString) value, bean)) {
                return true;
            }
            FieldConstraintExecutor.reportViolation(c, fieldName);
            return false;
        });
    }

    /**
     * Checks if the given {@link MultilingualString} contains a non-blank value for the primary language of the bean.
     *
     * @param value the {@link MultilingualString} to validate
     * @param bean  the bean whose primary language is used for validation
     * @return true if the value is not null and contains a non-blank value for the primary language, false otherwise
     */
    private static boolean isValid(MultilingualString value, HasPrimaryLanguage bean) {
        if (value == null) {
            return false;
        }
        return value.contains(bean.getPrimaryLanguage())
                && !value.get(bean.getPrimaryLanguage()).trim().isEmpty();
    }
}
