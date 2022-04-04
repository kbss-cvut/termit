package cz.cvut.kbss.termit.persistence.dao.util;

import cz.cvut.kbss.termit.event.EvictCacheEvent;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.EventListener;
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

    @EventListener
    public void onEvictCache(EvictCacheEvent evt) {
        evictAll();
    }
}
