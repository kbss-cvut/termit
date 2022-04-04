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
