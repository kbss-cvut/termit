package cz.cvut.kbss.termit.service.config;

import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Provides access to selected configuration values.
 */
@Service
public class ConfigurationProvider {

    private final Configuration config;

    @Autowired
    public ConfigurationProvider(Configuration config) {
        this.config = config;
    }

    /**
     * Gets a DTO with selected configuration values, usable by clients.
     *
     * @return Configuration object
     */
    public ConfigurationDto getConfiguration() {
        final ConfigurationDto result = new ConfigurationDto();
        result.setLanguage(config.get(ConfigParam.LANGUAGE));
        return result;
    }
}
