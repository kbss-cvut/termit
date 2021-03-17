/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.rest.util;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.security.SecurityConstants;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.Cookie;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.*;

class RestUtilsTest {

    @Test
    void createLocationHeaderFromCurrentUriWithPathAddsPathWithVariableReplacementsToRequestUri() {
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest(HttpMethod.GET.toString(),
                "/vocabularies");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
        final String id = "117";

        final URI result = RestUtils.createLocationFromCurrentUriWithPath("/{id}", id);
        assertThat(result.toString(), endsWith("/vocabularies/" + id));
    }

    @Test
    void createLocationHeaderFromCurrentUriWithQueryParamAddsQueryParameterWithValueToRequestUri() {
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest(HttpMethod.GET.toString(),
                "/vocabularies");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
        final URI id = Generator.generateUri();

        final URI result = RestUtils.createLocationFromCurrentUriWithQueryParam("id", id);
        assertThat(result.toString(), endsWith("/vocabularies?id=" + id));
    }

    @Test
    void getCookieExtractsCookieValueFromRequest() {
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest(HttpMethod.GET.toString(),
                "/vocabularies");
        mockRequest.setCookies(new Cookie(SecurityConstants.REMEMBER_ME_COOKIE_NAME, Boolean.TRUE.toString()));

        final Optional<String> result = RestUtils.getCookie(mockRequest, SecurityConstants.REMEMBER_ME_COOKIE_NAME);
        assertTrue(result.isPresent());
        assertTrue(Boolean.parseBoolean(result.get()));
    }

    @Test
    void getCookieReturnsEmptyOptionalWhenCookieIsNotFound() {
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest(HttpMethod.GET.toString(),
                "/vocabularies");
        mockRequest.setCookies(new Cookie(SecurityConstants.REMEMBER_ME_COOKIE_NAME, Boolean.TRUE.toString()));

        final Optional<String> result = RestUtils.getCookie(mockRequest, "unknown-cookie");
        assertFalse(result.isPresent());
    }

    @Test
    void urlEncodeEncodesSpecifiedStringWithUTF8URLEncoding() throws Exception {
        final String value = Generator.generateUri().toString();
        assertEquals(URLEncoder.encode(value, StandardCharsets.UTF_8.name()), RestUtils.urlEncode(value));
    }

    @Test
    void createLocationHeaderFromCurrentUriWithPathAndQueryCreatesLocationHeader() {
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest(HttpMethod.GET.toString(),
                "/vocabularies");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
        final String name = "metropolitan-plan";
        final String param = "namespace";
        final String paramValue = "http://onto.fel.cvut.cz/ontologies/termit/vocabularies/";
        final URI result = RestUtils.createLocationFromCurrentUriWithPathAndQuery("/{name}", param, paramValue, name);
        assertThat(result.toString(), endsWith("/" + name + "?" + param + "=" + paramValue));
    }

    @Test
    void createLocationHeaderFromCurrentContextWithPathAndQueryCreatesLocationHeader() {
        final MockHttpServletRequest mockRequest = new MockHttpServletRequest(HttpMethod.GET.toString(),
                "/vocabularies");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
        final String name = "metropolitan-plan";
        final String param = "namespace";
        final String paramValue = "http://onto.fel.cvut.cz/ontologies/termit/vocabularies/";
        final URI result = RestUtils.createLocationFromCurrentContextWithPathAndQuery("/{name}", param, paramValue, name);
        assertThat(result.toString(), endsWith("/" + name + "?" + param + "=" + paramValue));
    }
}
