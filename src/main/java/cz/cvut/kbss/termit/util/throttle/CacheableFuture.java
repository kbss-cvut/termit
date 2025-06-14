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

import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import jakarta.annotation.Nullable;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * A future which can provide a cached result before its completion.
 *
 * @see Future
 */
public interface CacheableFuture<T> extends Future<T> {

    /**
     * @return the cached result when available
     */
    Optional<T> getCachedResult();

    /**
     * Sets possible cached result
     *
     * @param cachedResult the result to set, or null to clear the cache
     * @return self
     */
    CacheableFuture<T> setCachedResult(@Nullable final T cachedResult);

    /**
     * @return the future result if it is available, cached result otherwise.
     */
    default Optional<T> getNow() {
        try {
            if (isDone() && !isCancelled()) {
                return Optional.of(get());
            }
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UnsupportedOperationException) {
                return Optional.empty();
            }
            throw new TermItException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TermItException(e);
        }

        return getCachedResult();
    }
}
