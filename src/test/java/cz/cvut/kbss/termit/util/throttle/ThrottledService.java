package cz.cvut.kbss.termit.util.throttle;

import java.util.concurrent.Future;

public class ThrottledService {

    @Throttle
    public Future<Boolean> annotatedMethod() {
        return ThrottledFuture.of(() -> true);
    }
}
