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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jsonld.ConfigParam;
import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.jsonld.jackson.JsonLdModule;
import cz.cvut.kbss.jsonld.jackson.serialization.SerializationConstants;
import cz.cvut.kbss.termit.rest.servlet.DiagnosticsContextFilter;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.security.model.LoginStatus;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.json.MultilingualStringDeserializer;
import cz.cvut.kbss.termit.util.json.MultilingualStringSerializer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

@Configuration
public class WebAppConfig implements WebMvcConfigurer {

    @Value("${application.version:development}")
    private String version;

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
        jsonLdModule.configure(ConfigParam.ASSUME_TARGET_TYPE, "true");
        jsonLdModule.configure(SerializationConstants.FORM, SerializationConstants.FORM_COMPACT_WITH_CONTEXT);
        mapper.registerModule(jsonLdModule);
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean(name = "objectMapper")
    @Primary
    public ObjectMapper objectMapper() {
        return createJsonObjectMapper();
    }

    @Bean(name = "jsonLdMapper")
    public ObjectMapper jsonLdObjectMapper() {
        return createJsonLdObjectMapper();
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

    @Bean
    public OpenApiCustomizer customerGlobalHeaderOpenApiCustomizer() {
        return openApi -> {
            // Add form login endpoint
            // We create the docs programmatically because our custom AuthenticationFilter apparently prevents Springdoc from
            // correctly recognizing the login endpoint and not offering the url-encoded content type in the login endpoint docs.
            openApi.getPaths().addPathItem(SecurityConstants.LOGIN_PATH, createLoginPathDocumentation());
        };
    }

    private static PathItem createLoginPathDocumentation() {
        return new PathItem()
                .post(new Operation()
                              .addTagsItem("Authentication")
                              .summary("Login with username and password")
                              .description("Authenticates user and returns JWT token")
                              .requestBody(new RequestBody()
                                                   .content(new Content()
                                                                    .addMediaType(
                                                                            MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                                                                            new io.swagger.v3.oas.models.media.MediaType().schema(
                                                                                    new Schema<Map<String, String>>()
                                                                                            .type("object")
                                                                                            .addProperty(
                                                                                                    SecurityConstants.USERNAME_PARAM,
                                                                                                    new StringSchema().description(
                                                                                                            "The username"))
                                                                                            .addProperty(
                                                                                                    SecurityConstants.PASSWORD_PARAM,
                                                                                                    new StringSchema().description(
                                                                                                                              "The password")
                                                                                                                      .format("password"))
                                                                                            .required(
                                                                                                    java.util.Arrays.asList(
                                                                                                            SecurityConstants.USERNAME_PARAM,
                                                                                                            SecurityConstants.PASSWORD_PARAM))))))
                              .responses(new ApiResponses()
                                                 .addApiResponse("200",
                                                                 new ApiResponse().description(
                                                                                          "Authentication request successfully processed")
                                                                                  .content(
                                                                                          new Content().addMediaType(
                                                                                                  MediaType.APPLICATION_JSON_VALUE,
                                                                                                  new io.swagger.v3.oas.models.media.MediaType().example(
                                                                                                          new LoginStatus(
                                                                                                                  true,
                                                                                                                  true,
                                                                                                                  "username",
                                                                                                                  "Error message if not logged in.")))))));
    }
}
