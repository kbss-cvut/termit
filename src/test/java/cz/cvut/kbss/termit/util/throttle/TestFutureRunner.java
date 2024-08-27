package cz.cvut.kbss.termit.util.throttle;

import java.util.concurrent.ExecutionException;

public class TestFutureRunner {

    private TestFutureRunner() {
        throw new AssertionError();
    }

    /**
     * Executes the task inside the future and returns its result.
     *
     * @implNote Note that this method is intended only for testing purposes.
     */
    public static <T> T runFuture(ThrottledFuture<T> future) {
        future.run();
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
