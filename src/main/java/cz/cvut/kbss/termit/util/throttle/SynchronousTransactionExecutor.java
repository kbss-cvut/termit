package cz.cvut.kbss.termit.util.throttle;

import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.Executor;

/**
 * Executes the runnable in a transaction synchronously.
 *
 * @see Transactional
 */
@Component
public class SynchronousTransactionExecutor implements Executor {

    @Transactional
    @Override
    public void execute(@Nonnull Runnable command) {
        command.run();
    }
}
