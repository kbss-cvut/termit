/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.rest.handler.RestExceptionHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.accept.ContentNegotiationManager;

import static cz.cvut.kbss.termit.environment.Environment.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Common configuration for REST controller tests.
 */
public class BaseControllerTestRunner {

    protected ObjectMapper objectMapper;

    protected ObjectMapper jsonLdObjectMapper;

    protected MockMvc mockMvc;

    public BaseControllerTestRunner() {
        setupObjectMappers();
    }

    public void setUp(Object controller) {
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new RestExceptionHandler())
                                      .setMessageConverters(createJsonLdMessageConverter(),
                                              createDefaultMessageConverter(), createStringEncodingMessageConverter(),
                                              createResourceMessageConverter())
                                      .setUseSuffixPatternMatch(false)
                                      .setContentNegotiationManager(new ContentNegotiationManager())
                                      .build();
    }

    protected void setupObjectMappers() {
        this.objectMapper = Environment.getObjectMapper();
        this.jsonLdObjectMapper = Environment.getJsonLdObjectMapper();
    }

    protected String toJson(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    protected String toJsonLd(Object object) throws Exception {
        return jsonLdObjectMapper.writeValueAsString(object);
    }

    protected <T> T readValue(MvcResult result, Class<T> targetType) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), targetType);
    }

    protected <T> T readValue(MvcResult result, TypeReference<T> targetType) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), targetType);
    }

    void verifyLocationEquals(String expectedPath, MvcResult result) {
        final String locationHeader = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertNotNull(locationHeader);
        final String path = locationHeader.substring(0,
                locationHeader.indexOf('?') != -1 ? locationHeader.indexOf('?') : locationHeader.length());
        assertEquals("http://localhost" + expectedPath, path);
    }
}
