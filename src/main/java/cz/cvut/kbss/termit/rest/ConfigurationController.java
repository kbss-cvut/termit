package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.config.ConfigurationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("hasRole('" + SecurityConstants.ROLE_USER + "')")
@RestController
@RequestMapping("/configuration")
public class ConfigurationController {

    private final ConfigurationProvider configProvider;

    @Autowired
    public ConfigurationController(ConfigurationProvider configProvider) {
        this.configProvider = configProvider;
    }

    @GetMapping(produces = { MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE })
    public ConfigurationDto getConfiguration() {
        return configProvider.getConfiguration();
    }
}
