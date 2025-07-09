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

import cz.cvut.kbss.termit.model.util.validation.HasPrimaryLanguage;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validation constraint ensuring that specified {@link cz.cvut.kbss.jopa.model.MultilingualString MultilingualString}
 * attributes contains a non-empty value in the primary language of the entity.
 * <p>
 * The entity must implement {@link HasPrimaryLanguage} to provide the primary language.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultilingualStringPrimaryNotBlankValidator.class)
@Documented
@Repeatable(PrimaryNotBlank.List.class)
public @interface PrimaryNotBlank {

    String message() default "{jakarta.validation.constraints.PrimaryNotBlank.message}";

    /**
     * @return Array of {@link cz.cvut.kbss.jopa.model.MultilingualString MultilingualString} field names to validate.
     */
    String[] value();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        PrimaryNotBlank[] value();
    }
}
