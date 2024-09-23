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
package cz.cvut.kbss.termit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.termit.exception.JwtException;
import cz.cvut.kbss.termit.rest.ConfigurationController;
import cz.cvut.kbss.termit.rest.LanguageController;
import cz.cvut.kbss.termit.rest.handler.ErrorInfo;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static cz.cvut.kbss.termit.security.SecurityConstants.PUBLIC_API_PATH;
import static cz.cvut.kbss.termit.util.Constants.REST_MAPPING_PATH;

/**
 * This filter retrieves JWT from the incoming request and validates it, ensuring that the user is authorized to access
 * the application.
 */
public class JwtAuthorizationFilter extends BasicAuthenticationFilter {

    private static final Set<String> PUBLIC_ENDPOINTS = new HashSet<>(Arrays.asList(
            REST_MAPPING_PATH + PUBLIC_API_PATH,
            REST_MAPPING_PATH + LanguageController.PATH + "/types",
            REST_MAPPING_PATH + LanguageController.PATH + "/states",
            REST_MAPPING_PATH + "/data/label"    // DataController.getLabel
    ));

    private final JwtUtils jwtUtils;

    private final ObjectMapper objectMapper;

    private final TermitJwtDecoder jwtDecoder;

    public JwtAuthorizationFilter(AuthenticationManager authenticationManager, JwtUtils jwtUtils, ObjectMapper objectMapper,
                                  TermitJwtDecoder jwtDecoder) {
        super(authenticationManager);
        this.jwtUtils = jwtUtils;
        this.objectMapper = objectMapper;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(SecurityConstants.JWT_TOKEN_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        final String authToken = authHeader.substring(SecurityConstants.JWT_TOKEN_PREFIX.length());
        try {
            Jwt jwt = jwtDecoder.decode(authToken);
            final Object principal = jwt.getClaim(JwtClaimNames.SUB);
            if (principal instanceof TermItUserDetails existingDetails) {
                SecurityUtils.setCurrentUser(existingDetails);
                refreshToken(authToken, response);
                chain.doFilter(request, response);
            } else {
                throw new JwtException("Invalid JWT token contents");
            }
        } catch (JwtException | org.springframework.security.oauth2.jwt.JwtException e) {
            if (shouldAllowThroughUnauthenticated(request)) {
                chain.doFilter(request, response);
            } else {
                unauthorizedRequest(request, response, e);
            }
        } catch (DisabledException | LockedException | UsernameNotFoundException e) {
            unauthorizedRequest(request, response, e);
        }
    }

    private void unauthorizedRequest(HttpServletRequest request, HttpServletResponse response, RuntimeException e)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        objectMapper.writeValue(response.getOutputStream(),
                                ErrorInfo.createWithMessage(e.getMessage(), request.getRequestURI()));
    }

    private void refreshToken(String authToken, HttpServletResponse response) {
        final String newToken = jwtUtils.refreshToken(authToken);
        response.setHeader(HttpHeaders.AUTHORIZATION, SecurityConstants.JWT_TOKEN_PREFIX + newToken);
    }

    /**
     * Whether to allow the specified request through even though it does not contain a valid authentication token.
     * <p>
     * This is useful for endpoints which support both authenticated and unauthenticated access.
     *
     * @param request Request to allow/not allow throw
     * @return Whether to allow the specified request through
     */
    private static boolean shouldAllowThroughUnauthenticated(HttpServletRequest request) {
        return request.getRequestURI().contains(REST_MAPPING_PATH + ConfigurationController.PATH);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Public API endpoints are not secured, so there is no need to check for token.
        // This resolves issues with public API requests containing expired/invalid JWT being rejected
        return PUBLIC_ENDPOINTS.stream().anyMatch(pattern -> request.getRequestURI().contains(pattern));
    }
}
