package cz.cvut.kbss.termit.util.throttle;

import cz.cvut.kbss.termit.util.Configuration;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.mockito.Answers.RETURNS_SMART_NULLS;

@TestConfiguration
@EnableAspectJAutoProxy
@ImportResource("classpath*:spring-aop.xml")
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

    @Bean
    public Configuration configuration() {
        return new Configuration();
    }
}
