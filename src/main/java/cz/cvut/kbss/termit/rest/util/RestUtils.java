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
package cz.cvut.kbss.termit.rest.util;

import cz.cvut.kbss.termit.exception.TermItException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Null;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility functions for request processing.
 */
public class RestUtils {

    private RestUtils() {
        throw new AssertionError();
    }

    /**
     * Creates location URI corresponding to the current request's URI.
     *
     * @return location {@code URI}
     */
    public static URI createLocationFromCurrentUri() {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri();
    }

    /**
     * Creates location URI with the specified path appended to the current request URI.
     * <p>
     * The {@code uriVariableValues} are used to fill in possible variables specified in {@code path}.
     *
     * @param path              Path to add to the current request URI in order to construct a resource location
     * @param uriVariableValues Values used to replace possible variables in the path
     * @return location {@code URI}
     * @see #createLocationFromCurrentUriWithQueryParam(String, Object...)
     */
    public static URI createLocationFromCurrentUriWithPath(String path, Object... uriVariableValues) {
        Objects.requireNonNull(path);
        return ServletUriComponentsBuilder.fromCurrentRequestUri().path(path).buildAndExpand(
                uriVariableValues).toUri();
    }

    /**
     * Creates location URI with the specified query parameter appended to the current request URI.
     * <p>
     * The {@code values} are used as values of {@code param} in the resulting URI.
     *
     * @param param  Query parameter to add to current request URI
     * @param values Values of the query parameter
     * @return location {@code URI}
     * @see #createLocationFromCurrentUriWithPath(String, Object...)
     */
    public static URI createLocationFromCurrentUriWithQueryParam(String param, Object... values) {
        Objects.requireNonNull(param);
        return ServletUriComponentsBuilder.fromCurrentRequestUri().queryParam(param, values).build()
                                          .toUri();
    }

    /**
     * Creates location URI with the specified path and query parameter appended to the current request URI.
     * <p>
     * The {@code paramValue} is specified for the query parameter and {@code pathValues} are used to replace path
     * variables.
     *
     * @param path       Path string, may contain path variables
     * @param param      Query parameter to add to current request URI
     * @param paramValue Value of the query parameter
     * @param pathValues Path variable values
     * @return location {@code URI}
     * @see #createLocationFromCurrentUriWithPath(String, Object...)
     * @see #createLocationFromCurrentUriWithQueryParam(String, Object...)
     */
    public static URI createLocationFromCurrentUriWithPathAndQuery(String path, String param,
                                                                   Object paramValue,
                                                                   Object... pathValues) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(param);
        return ServletUriComponentsBuilder.fromCurrentRequestUri().queryParam(param, paramValue)
                                          .path(path).buildAndExpand(pathValues).toUri();
    }

    /**
     * Creates location URI with the specified path and query parameter appended to the current context URI.
     * <p>
     * The {@code paramValue} is specified for the query parameter and {@code pathValues} are used to replace path
     * variables.
     *
     * @param queryParam Query parameter to add to current request URI
     * @param path       Path string, may contain path variables
     * @param queryValue Value of the query parameter
     * @param pathValues Path variable values
     * @return location {@code URI}
     * @see #createLocationFromCurrentUriWithPath(String, Object...)
     * @see #createLocationFromCurrentUriWithQueryParam(String, Object...)
     */
    public static URI createLocationFromCurrentContextWithPathAndQuery(String path, String queryParam,
                                                                       String queryValue, Object... pathValues) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(queryParam);
        return ServletUriComponentsBuilder.fromCurrentContextPath().queryParam(queryParam, queryValue).path(path)
                                          .buildAndExpand(pathValues).toUri();
    }

    /**
     * Encodes the specifies value with an URL encoder, using {@link StandardCharsets#UTF_8}.
     *
     * @param value The value to encode
     * @return Encoded string
     */
    public static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // Unlikely
            throw new TermItException("Encoding not found.", e);
        }
    }

    /**
     * Retrieves value of the specified cookie from the specified request.
     *
     * @param request    Request to get cookie from
     * @param cookieName Name of the cookie to retrieve
     * @return Value of the cookie
     */
    public static Optional<String> getCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals(cookieName)) {
                    return Optional.ofNullable(cookie.getValue());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Parses the specified string as {@link Instant}.
     * <p>
     * It expects the string to be in the ISO format at UTC, compatible with {@link Instant} string representation.
     *
     * @param strTimestamp String representing the instant
     * @return Parsed instant
     * @throws ResponseStatusException with status 400 in case the string is not parseable
     */
    public static Instant parseTimestamp(String strTimestamp) {
        try {
            return Instant.parse(strTimestamp);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                              "Value '" + strTimestamp + "' is not a valid timestamp in ISO format.");
        }
    }
}
