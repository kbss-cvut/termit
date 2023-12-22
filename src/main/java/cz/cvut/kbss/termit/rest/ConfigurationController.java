/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.service.config.ConfigurationProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cz.cvut.kbss.termit.rest.ConfigurationController.PATH;

@Tag(name = "Configuration", description = "Application configuration API")
@RestController
@RequestMapping(PATH)
public class ConfigurationController {

    public static final String PATH = "/configuration";

    private final ConfigurationProvider configProvider;

    @Autowired
    public ConfigurationController(ConfigurationProvider configProvider) {
        this.configProvider = configProvider;
    }

    @Operation(description = "Gets information about application configuration relevant to the client.")
    @ApiResponse(responseCode = "200", description = "Application configuration data.")
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ConfigurationDto getConfiguration(Authentication auth) {
        final ConfigurationDto result = configProvider.getConfiguration();
        if (auth == null || !auth.isAuthenticated()) {
            result.setRoles(null);
        }
        return result;
    }
}
