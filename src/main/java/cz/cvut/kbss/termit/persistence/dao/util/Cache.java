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
