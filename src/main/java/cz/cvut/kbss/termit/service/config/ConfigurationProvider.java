package cz.cvut.kbss.termit.service.config;

import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.service.repository.UserRoleRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import java.net.URI;
import java.util.HashSet;

import cz.cvut.kbss.termit.util.Configuration.Persistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Provides access to selected configuration values.
 */
@Service
public class ConfigurationProvider {

    private final Persistence config;

    private final UserRoleRepositoryService service;

    @Autowired
    public ConfigurationProvider(Configuration config,
                                 UserRoleRepositoryService service) {
        this.config = config.getPersistence();
        this.service = service;
    }

    /**
     * Gets a DTO with selected configuration values, usable by clients.
     *
     * @return Configuration object
     */
    public ConfigurationDto getConfiguration() {
        final ConfigurationDto result = new ConfigurationDto();
        result.setId(URI.create(Vocabulary.s_c_konfigurace + "/default"));
        result.setLanguage(config.getLanguage());
        result.setRoles(new HashSet<>(service.findAll()));
        return result;
    }
}
