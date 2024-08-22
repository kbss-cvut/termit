package cz.cvut.kbss.termit.util.throttle;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.Executor;

/**
 * Executes the runnable in a transaction
 *
 * @see Transactional
 */
@Component
public class TransactionExecutor implements Executor {

    @Transactional
    @Override
    public void execute(@NotNull Runnable command) {
        command.run();
    }
}
