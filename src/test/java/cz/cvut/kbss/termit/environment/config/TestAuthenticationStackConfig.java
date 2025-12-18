package cz.cvut.kbss.termit.environment.config;

import cz.cvut.kbss.termit.TermItApplication;
import cz.cvut.kbss.termit.rest.HealthController;
import cz.cvut.kbss.termit.service.config.ConfigurationProvider;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTasksRegistry;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.InputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This config initializes the whole application,
 * since we want to test the security stack as it is configured for production
 */
@Import({TermItApplication.class})
@EnableAutoConfiguration
@TestConfiguration
public class TestAuthenticationStackConfig {
    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSender sender = mock(JavaMailSenderImpl.class);
        when(sender.createMimeMessage()).thenCallRealMethod();
        when(sender.createMimeMessage(any(InputStream.class))).thenCallRealMethod();
        return sender;
    }

    @Bean
    public ConfigurationProvider configurationProvider() {
        return mock(ConfigurationProvider.class);
    }

    @Bean
    public LongRunningTasksRegistry longRunningTasksRegistry() {
        return mock(LongRunningTasksRegistry.class);
    }

    @Bean
    public HealthController healthController() {
        return mock(HealthController.class);
    }
}
