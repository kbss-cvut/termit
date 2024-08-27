package cz.cvut.kbss.termit.util.throttle;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.Future;

public interface CachableFuture<T> extends Future<T> {

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
    CachableFuture<T> setCachedResult(@Nullable final T cachedResult);
}
