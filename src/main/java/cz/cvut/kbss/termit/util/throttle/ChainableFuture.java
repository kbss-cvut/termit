package cz.cvut.kbss.termit.util.throttle;

import java.util.concurrent.Future;
import java.util.function.Consumer;

public interface ChainableFuture<T> extends Future<T> {

    /**
     * Executes this action once the future is completed normally.
     * Action is not executed on exceptional completion.
     * <p>
     * If the future is already completed, action is executed synchronously.
     * @param action action to be executed
     */
    ChainableFuture then(Consumer<T> action);
}
