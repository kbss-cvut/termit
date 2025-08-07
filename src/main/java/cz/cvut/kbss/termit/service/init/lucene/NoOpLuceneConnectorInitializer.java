package cz.cvut.kbss.termit.service.init.lucene;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * No operation initializer used when {@code lucene} profile is not active
 */
@Service
@Profile("!lucene")
public class NoOpLuceneConnectorInitializer implements LuceneConnectorInitializer {
    @Override
    public void initialize() {
        // No operation
    }
}
