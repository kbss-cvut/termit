package cz.cvut.kbss.termit.util.throttle;

import cz.cvut.kbss.termit.exception.TermItException;
import jakarta.annotation.Nullable;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * A future which can provide a cached result before its completion.
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
            throw new TermItException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TermItException(e);
        }

        return getCachedResult();
    }
}
