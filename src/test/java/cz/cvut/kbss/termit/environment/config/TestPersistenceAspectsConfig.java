package cz.cvut.kbss.termit.environment.config;

import cz.cvut.kbss.termit.aspect.ChangeTrackingAspect;
import cz.cvut.kbss.termit.aspect.VocabularyContentModificationAspect;
import cz.cvut.kbss.termit.service.changetracking.ChangeTracker;
import org.aspectj.lang.Aspects;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestPersistenceAspectsConfig {

    @Bean
    public ChangeTrackingAspect changeTrackingAspect() {
        return Aspects.aspectOf(ChangeTrackingAspect.class);
    }

    @Bean
    @Primary
    public ChangeTracker changeTracker() {
        return mock(ChangeTracker.class);
    }

    @Bean
    VocabularyContentModificationAspect vocabularyContentModificationAspect() {
        return Aspects.aspectOf(VocabularyContentModificationAspect.class);
    }

    @Bean
    public ApplicationEventPublisher eventPublisher() {
        return mock(ApplicationEventPublisher.class);
    }
}
