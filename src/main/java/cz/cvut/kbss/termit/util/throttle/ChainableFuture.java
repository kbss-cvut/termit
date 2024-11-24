package cz.cvut.kbss.termit.util.throttle;

import java.util.concurrent.Future;
import java.util.function.Consumer;

public interface ChainableFuture<T, F extends ChainableFuture<T, F>> extends Future<T> {

    /**
     * Executes this action once the future is completed.
     * Action is executed no matter if the future is completed successfully, exceptionally or cancelled.
     * <p>
     * If the future is already completed, it is executed synchronously.
     * <p>
     * Note that you <b>must</b> use the future passed as the parameter and not the original future object.
     * @param action action receiving this future after completion
     * @return this future
     */
    ChainableFuture<T, F> then(Consumer<F> action);
}
