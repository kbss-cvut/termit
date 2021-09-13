package cz.cvut.kbss.termit.service.config;

import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
class ConfigurationProviderTest extends BaseServiceTestRunner {

    @Autowired
    private Configuration config;

    @Autowired
    private Environment environment;

    @Autowired
    private ConfigurationProvider sut;

    @Test
    void getConfigurationReturnsConfigurationDtoWithRelevantConfigurationValues() {
        final ConfigurationDto result = sut.getConfiguration();
        assertNotNull(result);
        assertEquals(config.getPersistence().getLanguage(), result.getLanguage());
        assertEquals(environment.getProperty("spring.servlet.multipart.max-file-size"), result.getMaxFileUploadSize());
    }
}
