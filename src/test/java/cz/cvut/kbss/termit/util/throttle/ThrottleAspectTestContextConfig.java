package cz.cvut.kbss.termit.util.throttle;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.aspectj.EnableSpringConfigured;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.mockito.Answers.RETURNS_SMART_NULLS;

@TestConfiguration
@EnableSpringConfigured
@ImportResource("classpath*:spring-aop.xml")
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan(value = "cz.cvut.kbss.termit.util.throttle")
public class ThrottleAspectTestContextConfig {

    @Bean
    public ThreadPoolTaskScheduler longRunningTaskScheduler() {
        return Mockito.mock(ThreadPoolTaskScheduler.class, RETURNS_SMART_NULLS);
    }

    @Bean
    public ThrottledService throttledService() {
        return new ThrottledService();
    }
}
