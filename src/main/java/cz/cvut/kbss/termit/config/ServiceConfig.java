/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.termit.exception.ResourceNotFoundException;
import cz.cvut.kbss.termit.service.validation.ExternalServiceValidator;
import cz.cvut.kbss.termit.service.validation.NoopRepositoryContextValidator;
import cz.cvut.kbss.termit.service.validation.RepositoryContextValidator;
import cz.cvut.kbss.termit.util.Utils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

@Configuration
public class ServiceConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RestTemplate restTemplate(@Qualifier("objectMapper") ObjectMapper objectMapper) {
        final RestTemplate restTemplate = new RestTemplate();

        // HttpClient 5 default redirect strategy automatically follows POST redirects as well
        final HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        final HttpClient httpClient = HttpClientBuilder.create()
                                                       .setRedirectStrategy(new DefaultRedirectStrategy())
                                                       .build();
        factory.setHttpClient(httpClient);
        factory.setReadTimeout(Duration.ofMinutes(10L));
        restTemplate.setRequestFactory(factory);

        final MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);
        final StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        restTemplate.setMessageConverters(
                Arrays.asList(jacksonConverter, stringConverter, new ResourceHttpMessageConverter(),
                              new FormHttpMessageConverter()));
        return restTemplate;
    }

    /**
     * Provides JSR 380 validator for bean validation.
     */
    @Bean
    public LocalValidatorFactoryBean validatorFactoryBean() {
        return new LocalValidatorFactoryBean();
    }

    @Bean("termTypesLanguage")
    public Resource termTypesLanguageFile(cz.cvut.kbss.termit.util.Configuration config) {
        if (!Utils.isBlank(config.getLanguage().getTypes().getSource())) {
            return createFileSystemResource(config.getLanguage().getTypes().getSource(), "types");
        }
        return new ClassPathResource("languages/types.ttl");
    }

    private Resource createFileSystemResource(String path, String type) {
        final FileSystemResource source = new FileSystemResource(path);
        if (!source.exists()) {
            throw new ResourceNotFoundException(type + " language file '" + path + "' not found.");
        }
        LOG.info("Will load term {} from '{}'.", type, path);
        return source;
    }

    @Bean("termStatesLanguage")
    public Resource termStatesLanguageFile(cz.cvut.kbss.termit.util.Configuration config) {
        if (!Utils.isBlank(config.getLanguage().getStates().getSource())) {
            return createFileSystemResource(config.getLanguage().getStates().getSource(), "states");
        }
        return new ClassPathResource("languages/states.ttl");
    }

    @Bean
    public RepositoryContextValidator repositoryContextValidator(RestTemplate restTemplate, Environment env) {
        if (!env.containsProperty("termit.validation-service-url")) {
            LOG.warn("Validation service URL not configured, will use no-op validator.");
            return new NoopRepositoryContextValidator();
        } else {
            final String validationServiceUrl = env.getProperty("termit.validation-service-url");
            LOG.debug("Using validation service at {}.", validationServiceUrl);
            return new ExternalServiceValidator(restTemplate, validationServiceUrl);
        }
    }
}
