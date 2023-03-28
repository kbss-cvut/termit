package cz.cvut.kbss.termit.service.config;

import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
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

    @Test
    void getConfigurationAddsAvailableAccessLevelsToResult() {
        final ConfigurationDto result = sut.getConfiguration();
        assertEquals(result.getAccessLevels().size(), AccessLevel.values().length);
        assertThat(result.getAccessLevels(), hasItems(AccessLevel.values()));
    }
}
