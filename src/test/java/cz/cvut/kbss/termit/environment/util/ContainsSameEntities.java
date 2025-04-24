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
package cz.cvut.kbss.termit.environment.util;

import cz.cvut.kbss.termit.model.util.HasIdentifier;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.Collection;
import java.util.Objects;

/**
 * Checks whether the provided collection contains the same entities as the expected one.
 * <p>
 * The membership check is done based on entity URIs.
 */
public class ContainsSameEntities extends TypeSafeMatcher<Collection<? extends HasIdentifier>> {

    private final Collection<? extends HasIdentifier> expected;

    public ContainsSameEntities(Collection<? extends HasIdentifier> expected) {
        this.expected = Objects.requireNonNull(expected);
    }

    @Override
    protected boolean matchesSafely(Collection<? extends HasIdentifier> actual) {
        if (actual == null || actual.size() != expected.size()) {
            return false;
        }
        for (HasIdentifier e : expected) {
            if (actual.stream().noneMatch(ee -> Objects.equals(e.getUri(), ee.getUri()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void describeTo(Description description) {
        description.appendValueList("[", ", ", "]", expected);
    }

    public static ContainsSameEntities containsSameEntities(Collection<? extends HasIdentifier> expected) {
        return new ContainsSameEntities(expected);
    }
}
