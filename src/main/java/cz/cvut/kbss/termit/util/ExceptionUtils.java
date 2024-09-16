package cz.cvut.kbss.termit.util;

import org.springframework.lang.NonNull;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class ExceptionUtils {
    private ExceptionUtils() {
        throw new AssertionError();
    }

    /**
     * Resolves all nested causes of the {@code throwable}
     * @return any cause of the {@code throwable} matching the {@code cause} class, or empty when not found
     */
    public static <T extends Throwable> Optional<T> isCausedBy(final Throwable throwable, @NonNull final Class<T> cause) {
        Throwable t = throwable;
        final Set<Throwable> visited = new HashSet<>();
        while (t != null) {
            if(visited.add(t)) {
                if (cause.isInstance(t)){
                    return Optional.of((T) t);
                }
                t = t.getCause();
                continue;
            }
            break;
        }
        return Optional.empty();
    }
}
