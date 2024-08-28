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
package cz.cvut.kbss.termit.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.jsonld.jackson.JsonLdModule;
import cz.cvut.kbss.jsonld.jackson.serialization.SerializationConstants;
import cz.cvut.kbss.termit.rest.servlet.DiagnosticsContextFilter;
import cz.cvut.kbss.termit.util.AdjustedUriTemplateProxyServlet;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.json.MultilingualStringDeserializer;
import cz.cvut.kbss.termit.util.json.MultilingualStringSerializer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.ServletWrappingController;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

@Configuration
public class WebAppConfig implements WebMvcConfigurer {

    private final cz.cvut.kbss.termit.util.Configuration.Repository config;

    @Value("${application.version:development}")
    private String version;

    public WebAppConfig(cz.cvut.kbss.termit.util.Configuration config) {
        this.config = config.getRepository();
    }

    @Bean(name = "objectMapper")
    @Primary
    public ObjectMapper objectMapper() {
        return createJsonObjectMapper();
    }

    /**
     * Creates an {@link ObjectMapper} for processing regular JSON.
     * <p>
     * This method is public static so that it can be used by the test environment as well.
     *
     * @return {@code ObjectMapper} instance
     */
    public static ObjectMapper createJsonObjectMapper() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final SimpleModule multilingualStringModule = new SimpleModule();
        multilingualStringModule.addSerializer(MultilingualString.class, new MultilingualStringSerializer());
        multilingualStringModule.addDeserializer(MultilingualString.class, new MultilingualStringDeserializer());
        objectMapper.registerModule(multilingualStringModule);
        // JSR 310 (Java 8 DateTime API)
        objectMapper.registerModule(new JavaTimeModule());
        // Serialize datetime as ISO strings
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return objectMapper;
    }

    @Bean(name = "jsonLdMapper")
    public ObjectMapper jsonLdObjectMapper() {
        return createJsonLdObjectMapper();
    }

    /**
     * Creates an {@link ObjectMapper} for processing JSON-LD using the JB4JSON-LD library.
     * <p>
     * This method is public static so that it can be used by the test environment as well.
     *
     * @return {@code ObjectMapper} instance
     */
    public static ObjectMapper createJsonLdObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final JsonLdModule jsonLdModule = new JsonLdModule();
        jsonLdModule.configure(cz.cvut.kbss.jsonld.ConfigParam.SCAN_PACKAGE, "cz.cvut.kbss.termit");
        jsonLdModule.configure(SerializationConstants.FORM, SerializationConstants.FORM_COMPACT_WITH_CONTEXT);
        mapper.registerModule(jsonLdModule);
        return mapper;
    }

    /**
     * Register the proxy for SPARQL endpoint.
     *
     * @return Returns the ServletWrappingController for the SPARQL endpoint.
     */
    @Bean(name = "sparqlEndpointProxyServlet")
    public ServletWrappingController sparqlEndpointController() throws Exception {
        ServletWrappingController controller = new ServletWrappingController();
        controller.setServletClass(AdjustedUriTemplateProxyServlet.class);
        controller.setBeanName("sparqlEndpointProxyServlet");
        final Properties p = new Properties();
        p.setProperty("targetUri", config.getUrl());
        p.setProperty("log", "false");
        p.setProperty(ConfigParam.REPO_USERNAME.toString(), config.getUsername() != null ? config.getUsername() : "");
        p.setProperty(ConfigParam.REPO_PASSWORD.toString(), config.getPassword() != null ? config.getPassword() : "");
        controller.setInitParameters(p);
        controller.afterPropertiesSet();
        return controller;
    }

    @Bean
    public SimpleUrlHandlerMapping sparqlQueryControllerMapping() throws Exception {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(0);
        final Map<String, Object> urlMap = Collections.singletonMap(Constants.REST_MAPPING_PATH + "/query",
                                                                    sparqlEndpointController());
        mapping.setUrlMap(urlMap);
        return mapping;
    }

    @Bean
    public HttpMessageConverter<?> termitStringHttpMessageConverter() {
        return new StringHttpMessageConverter(StandardCharsets.UTF_8);
    }

    @Bean
    public HttpMessageConverter<?> termitJsonLdHttpMessageConverter() {
        final MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(
                jsonLdObjectMapper());
        converter.setSupportedMediaTypes(Collections.singletonList(MediaType.valueOf(JsonLd.MEDIA_TYPE)));
        return converter;
    }

    @Bean
    public HttpMessageConverter<?> termitJsonHttpMessageConverter() {
        final MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper());
        return converter;
    }

    @Bean
    public HttpMessageConverter<?> termitResourceHttpMessageConverter() {
        return new ResourceHttpMessageConverter();
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer matcher) {
        matcher.addPathPrefix(Constants.REST_MAPPING_PATH, HandlerTypePredicate.forAnnotation(RestController.class));
    }

    @Bean
    public FilterRegistrationBean<DiagnosticsContextFilter> mdcFilter() {
        FilterRegistrationBean<DiagnosticsContextFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new DiagnosticsContextFilter());
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().components(new Components().addSecuritySchemes("bearer-key",
                                                                            new SecurityScheme().type(
                                                                                                        SecurityScheme.Type.HTTP)
                                                                                                .scheme("bearer")
                                                                                                .bearerFormat("JWT")))
                            .info(new Info().title("TermIt REST API").description("TermIt REST API definition.")
                                            .version(version));
    }
}
