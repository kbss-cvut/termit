package cz.cvut.kbss.termit.environment.config;

import cz.cvut.kbss.termit.aspect.ChangeTrackingAspect;
import cz.cvut.kbss.termit.service.changetracking.ChangeTracker;
import org.aspectj.lang.Aspects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;

@Configuration
public class TestPersistenceAspectsConfig {
    @Bean
    public ChangeTrackingAspect changeTrackingAspect() {
        return Aspects.aspectOf(ChangeTrackingAspect.class);
    }

    @Bean
    public ChangeTracker changeTracker() {
        return mock(ChangeTracker.class);
    }
}
