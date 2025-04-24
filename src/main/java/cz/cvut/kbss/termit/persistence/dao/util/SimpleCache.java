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

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A simple {@link ConcurrentHashMap}-based cache implementation useful for caching frequently accessed data.
 *
 * @param <K> Cache key type
 * @param <V> Cache value type
 */
@Primary
@Component
@Profile("!no-cache")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE) // Everyone will get their own cache instance
public class SimpleCache<K, V> implements Cache<K, V> {

    private final Map<K, V> cache = new ConcurrentHashMap<>();

    @Override
    public V getOrCompute(K key, Function<K, V> supplier) {
        return cache.computeIfAbsent(key, supplier);
    }

    @Override
    public void evict(K key) {
        cache.remove(key);
    }

    @Override
    public void evictAll() {
        cache.clear();
    }
}
