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
package cz.cvut.kbss.termit.environment.config;

import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.dto.mapper.DtoMapperImpl;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.model.selector.Selector;
import cz.cvut.kbss.termit.service.document.html.DummySelectorGenerator;
import cz.cvut.kbss.termit.service.document.html.HtmlSelectorGenerators;
import cz.cvut.kbss.termit.util.Configuration;
import org.jsoup.nodes.Element;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
@ComponentScan(basePackages = "cz.cvut.kbss.termit.service")
public class TestServiceConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RestTemplate restTemplate() {
        final RestTemplate client = new RestTemplate();
        final MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(Environment.getObjectMapper());
        final StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        client.setMessageConverters(
                Arrays.asList(jacksonConverter, stringConverter, new ResourceHttpMessageConverter()));
        return client;
    }

    @Bean
    public LocalValidatorFactoryBean validatorFactoryBean() {
        return new LocalValidatorFactoryBean();
    }

    @Bean
    @Primary
    public HtmlSelectorGenerators htmlSelectorGenerators(Configuration configuration) {
        return new HtmlSelectorGenerators(configuration) {
            @Override
            public Set<Selector> generateSelectors(Element... elements) {
                return Collections.singleton(new DummySelectorGenerator().generateSelector(elements));
            }
        };
    }

    @Bean("termTypesLanguage")
    public ClassPathResource termTypesLanguageFile() {
        return new ClassPathResource("languages/types.ttl");
    }

    @Bean("termStatesLanguage")
    public ClassPathResource termStatesLanguageFile() {
        return new ClassPathResource("languages/states.ttl");
    }

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSender sender = mock(JavaMailSenderImpl.class);
        when(sender.createMimeMessage()).thenCallRealMethod();
        when(sender.createMimeMessage(any(InputStream.class))).thenCallRealMethod();
        return sender;
    }

    @Bean
    public DtoMapper dtoMapper() {
        return new DtoMapperImpl();
    }
}
