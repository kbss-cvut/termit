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
package cz.cvut.kbss.termit.persistence.dao.util;

import java.util.function.Function;

/**
 * A general purpose cache useful for caching frequently accessed data.
 *
 * @param <K> Cache key type
 * @param <V> Cache value type
 */
public interface Cache<K, V> {

    /**
     * Gets the value associated with the specified key or computes it using the specified function.
     * <p>
     * If the value is not present in the cache, it is computed, stored in the cache, and returned.
     *
     * @param key      Cache key
     * @param supplier Value calculator
     * @return Value for the specified key, either existing or computed using the specified supplier
     */
    V getOrCompute(K key, Function<K, V> supplier);

    /**
     * Evicts value for the specified key.
     *
     * @param key Key to evict
     */
    void evict(K key);

    /**
     * Evicts the whole cache.
     */
    void evictAll();
}
