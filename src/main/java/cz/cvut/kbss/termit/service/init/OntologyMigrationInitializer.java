package cz.cvut.kbss.termit.service.init;

import cz.cvut.kbss.runner.MigrationRunner;
import jakarta.annotation.Nonnull;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Initializes and runs the Ontology Migration Tool, ensuring the underlying data and schema correspond to the current
 * version of the system.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OntologyMigrationInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(@Nonnull ConfigurableApplicationContext applicationContext) {
        final String repoUrl = applicationContext.getEnvironment().getRequiredProperty("termit.repository.url");
        final String repoUsername = applicationContext.getEnvironment().getProperty("termit.repository.username");
        final String repoPassword = applicationContext.getEnvironment().getProperty("termit.repository.password");

        final MigrationRunner runner = MigrationRunner.repository(repoUrl).username(repoUsername).password(repoPassword)
                                                      .changelogFile("migration/changelog.yaml")
                                                      .build();
        runner.run();
    }
}
