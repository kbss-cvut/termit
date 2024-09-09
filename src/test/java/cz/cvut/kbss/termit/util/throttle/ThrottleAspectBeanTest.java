package cz.cvut.kbss.termit.util.throttle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// intentionally not enabling test profile
@ExtendWith(MockitoExtension.class)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ThrottleAspectTestContextConfig.class},
                      initializers = {ConfigDataApplicationContextInitializer.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ThrottleAspectBeanTest {

    @Autowired
    ThreadPoolTaskScheduler longRunningTaskScheduler;

    @SpyBean
    ThrottleAspect throttleAspect;

    @Autowired
    ThrottledService throttledService;

    @BeforeEach
    void beforeEach() {
        reset(longRunningTaskScheduler);
        when(longRunningTaskScheduler.schedule(any(Runnable.class), any(Instant.class))).then(invocation -> {
            Runnable task = invocation.getArgument(0, Runnable.class);
            return new ScheduledFutureTask<>(task, null);
        });
    }

    @Test
    void throttleAspectIsCreated() {
        assertNotNull(throttleAspect);
    }

    @Test
    void aspectIsCalledWhenThrottleAnnotationIsPresent() throws Throwable {
        throttledService.annotatedMethod();

        final Method method = ThrottledService.class.getMethod("annotatedMethod");
        final Throttle annotation = method.getAnnotation(Throttle.class);
        assertNotNull(annotation);

        verify(throttleAspect).throttleMethodCall(any(), eq(annotation));
    }

}
