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
package cz.cvut.kbss.termit.util;

import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class ExceptionUtils {
    private ExceptionUtils() {
        throw new AssertionError();
    }

    /**
     * Resolves all nested causes of the {@code throwable}
     * @return any cause of the {@code throwable} matching the {@code cause} class, or empty when not found
     */
    public static <T extends Throwable> Optional<T> findCause(final Throwable throwable, @Nonnull final Class<T> cause) {
        Throwable t = throwable;
        final Set<Throwable> visited = new HashSet<>();
        while (t != null) {
            if(visited.add(t)) {
                if (cause.isInstance(t)){
                    return Optional.of((T) t);
                }
                t = t.getCause();
                continue;
            }
            break;
        }
        return Optional.empty();
    }
}
