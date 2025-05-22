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
package cz.cvut.kbss.termit.security;

import jakarta.annotation.Nonnull;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Authenticates STOMP CONNECT messages
 * <p>
 * Retrieves token from the {@code Authorization} header
 * and uses {@link JwtAuthenticationProvider} to authenticate the token.
 * @see <a href="https://stackoverflow.com/a/45405333/12690791">Consult this Stackoverflow answer</a>
 */
@Component
public class WebSocketJwtAuthorizationInterceptor implements ChannelInterceptor {

    private final JwtAuthenticationProvider jwtAuthenticationProvider;

    public WebSocketJwtAuthorizationInterceptor(JwtAuthenticationProvider jwtAuthenticationProvider) {
        this.jwtAuthenticationProvider = jwtAuthenticationProvider;
    }

    @Override
    public Message<?> preSend(@Nonnull Message<?> message, @Nonnull MessageChannel channel) {
        StompHeaderAccessor headerAccessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headerAccessor != null && StompCommand.CONNECT.equals(headerAccessor.getCommand()) && headerAccessor.isMutable()) {
            final String authHeader = headerAccessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null) {
                headerAccessor.removeNativeHeader(HttpHeaders.AUTHORIZATION);
                process(headerAccessor, authHeader);
                return message;
            }
            throw new AuthenticationCredentialsNotFoundException("Invalid authorization header");
        }
        return message;
    }

    /**
     * Authenticates user using JWT token in authentication header
     * <p>
     * According to <a href="https://openid.net/specs/openid-connect-core-1_0.html#TokenResponse">Open ID spec</a>,
     * the token MUST be {@code Bearer}.
     * And for example {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider}
     * also supports only {@code Bearer} tokens.
     */
    protected void process(StompHeaderAccessor stompHeaderAccessor, final @Nonnull String authHeader) {
        if (!StringUtils.startsWithIgnoreCase(authHeader, SecurityConstants.JWT_TOKEN_PREFIX)) {
            throw new InvalidBearerTokenException("Invalid Bearer token in authorization header");
        }

        final String token = authHeader.substring(SecurityConstants.JWT_TOKEN_PREFIX.length());

        BearerTokenAuthenticationToken authenticationRequest = new BearerTokenAuthenticationToken(token);

        try {
            Authentication authenticationResult = jwtAuthenticationProvider.authenticate(authenticationRequest);
            if (authenticationResult != null && authenticationResult.isAuthenticated()) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authenticationResult);
                SecurityContextHolder.setContext(context);
                stompHeaderAccessor.setUser(authenticationResult);
                return; // all ok
            }
            throw new OAuth2AuthenticationException("Authentication failed");
        } catch (Exception e) {
            // ensure that context is cleared when any exception happens
            stompHeaderAccessor.setUser(null);
            SecurityContextHolder.clearContext();
            throw e;
        }
    }
}
