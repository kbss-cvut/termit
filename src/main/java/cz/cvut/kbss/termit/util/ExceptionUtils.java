package cz.cvut.kbss.termit.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class ExceptionUtils {
    private ExceptionUtils() {
        throw new AssertionError();
    }

    /**
     * Resolves all nested causes of the {@code throwable} and returns true if any is matching the {@code cause}
     */
    public static boolean isCausedBy(@Nullable final Throwable throwable, @NotNull final Class<? extends Throwable> cause) {
        @Nullable Throwable t = throwable;
        final Set<Throwable> visited = new HashSet<>();
        while (t != null) {
            if(visited.add(t)) {
                if (cause.isInstance(t)){
                    return true;
                }
                t = t.getCause();
                continue;
            }
            break;
        }
        return false;
    }
}
