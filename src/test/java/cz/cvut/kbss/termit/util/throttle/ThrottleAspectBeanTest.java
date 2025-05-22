/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.util.throttle;

import cz.cvut.kbss.termit.util.longrunning.LongRunningTasksRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean
    LongRunningTasksRegistry longRunningTasksRegistry;

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
