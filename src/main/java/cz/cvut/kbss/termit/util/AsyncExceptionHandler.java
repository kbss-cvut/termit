package cz.cvut.kbss.termit.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Handles uncaught exceptions thrown by asynchronously running code.
 */
public class AsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncExceptionHandler.class);

    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... args) {
        LOG.error("Caught exception when running asynchronous code.", throwable);
        LOG.error("Method: {}", method.getName());
        LOG.error("Arguments: {}", Arrays.stream(args).map(Object::toString).collect(Collectors.joining(",")));
    }
}
