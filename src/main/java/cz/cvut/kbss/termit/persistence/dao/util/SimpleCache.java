package cz.cvut.kbss.termit.persistence.dao.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A simple cache implementation useful for caching frequently accessed data.
 *
 * @param <K> Cache key type
 * @param <V> Cache value type
 */
public class SimpleCache<K, V> {

    private final Map<K, V> cache = new ConcurrentHashMap<>();

    /**
     * Gets the value associated with the specified key or computes it using the specified function.
     * <p>
     * If the value is not present in the cache, it is computed, stored in the cache, and returned.
     *
     * @param key      Cache key
     * @param supplier Value calculator
     * @return Value for the specified key, either existing or computed using the specified supplier
     */
    public V getOrCompute(K key, Function<K, V> supplier) {
        return cache.computeIfAbsent(key, supplier);
    }

    /**
     * Evicts value for the specified key.
     *
     * @param key Key to evict
     */
    public void evict(K key) {
        cache.remove(key);
    }

    /**
     * Evicts the whole cache.
     */
    public void evictAll() {
        cache.clear();
    }
}
