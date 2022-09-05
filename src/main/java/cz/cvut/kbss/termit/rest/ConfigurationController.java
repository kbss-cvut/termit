package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.service.config.ConfigurationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cz.cvut.kbss.termit.rest.ConfigurationController.PATH;

@RestController
@RequestMapping(PATH)
public class ConfigurationController {

    public static final String PATH = "/configuration";

    private final ConfigurationProvider configProvider;

    @Autowired
    public ConfigurationController(ConfigurationProvider configProvider) {
        this.configProvider = configProvider;
    }

    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ConfigurationDto getConfiguration(Authentication auth) {
        final ConfigurationDto result = configProvider.getConfiguration();
        if (auth == null || !auth.isAuthenticated()) {
            result.setRoles(null);
        }
        return result;
    }
}
