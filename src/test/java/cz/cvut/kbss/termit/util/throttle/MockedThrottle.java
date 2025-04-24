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
package cz.cvut.kbss.termit.util.throttle;

import jakarta.annotation.Nonnull;

import java.lang.annotation.Annotation;

/**
 * Implementation of annotation interface allowing instancing for testing purposes
 */
public class MockedThrottle implements Throttle {

    private String value;

    private String group;

    public MockedThrottle(@Nonnull String value, @Nonnull String group) {
        this.value = value;
        this.group = group;
    }

    @Override
    public @Nonnull String value() {
        return value;
    }

    @Override
    public @Nonnull String group() {
        return group;
    }

    @Override
    public String name() {
        return "NameOfMockedThrottle"+group+value;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Throttle.class;
    }

    public void setValue(@Nonnull String value) {
        this.value = value;
    }

    public void setGroup(@Nonnull String group) {
        this.group = group;
    }
}
