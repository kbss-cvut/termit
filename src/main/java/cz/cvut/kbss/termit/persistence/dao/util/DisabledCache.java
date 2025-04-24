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

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Disabled cache implementation that always retrieves the current value and caches nothing.
 *
 * @param <K> Cache key type
 * @param <V> Cache value type
 */
@Component
@Profile("no-cache")
public class DisabledCache<K, V> implements Cache<K, V> {

    @Override
    public V getOrCompute(K key, Function<K, V> supplier) {
        return supplier.apply(key);
    }

    @Override
    public void evict(K key) {
        // Do nothing
    }

    @Override
    public void evictAll() {
        // Do nothing
    }
}
