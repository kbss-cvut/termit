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
package cz.cvut.kbss.termit.service.config;

import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.service.repository.UserRoleRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.HashSet;

/**
 * Provides access to selected configuration values.
 */
@Service
public class ConfigurationProvider {

    private final Configuration config;

    private final UserRoleRepositoryService service;

    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxFileUploadSize;

    @Autowired
    public ConfigurationProvider(Configuration config, UserRoleRepositoryService service) {
        this.config = config;
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
        result.setLanguage(config.getPersistence().getLanguage());
        result.setRoles(new HashSet<>(service.findAll()));
        result.setMaxFileUploadSize(maxFileUploadSize);
        result.setVersionSeparator(config.getNamespace().getSnapshot().getSeparator());
        result.setModelingToolUrl(config.getModelingToolUrl());
        return result;
    }
}
