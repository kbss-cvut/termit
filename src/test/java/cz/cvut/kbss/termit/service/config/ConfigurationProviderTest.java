package cz.cvut.kbss.termit.service.config;

import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigurationProviderTest extends BaseServiceTestRunner {

    @Autowired
    private Configuration config;

    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxFileSize;

    @Autowired
    private ConfigurationProvider sut;

    @Test
    void getConfigurationReturnsConfigurationDtoWithRelevantConfigurationValues() {
        final ConfigurationDto result = sut.getConfiguration();
        assertNotNull(result);
        assertEquals(config.getPersistence().getLanguage(), result.getLanguage());
        assertEquals(maxFileSize, result.getMaxFileUploadSize());
    }
}
