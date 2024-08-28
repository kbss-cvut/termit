package cz.cvut.kbss.termit.security;

import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.JwtException;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.service.security.TermItUserDetailsService;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class WebSocketJwtAuthorizationInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;

    private final TermItUserDetailsService userDetailsService;

    public WebSocketJwtAuthorizationInterceptor(JwtUtils jwtUtils, TermItUserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel channel) {
        StompHeaderAccessor headerAccessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headerAccessor != null && StompCommand.CONNECT.equals(headerAccessor.getCommand()) && headerAccessor.isMutable()) {
            final String authHeader = headerAccessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith(SecurityConstants.JWT_TOKEN_PREFIX)) {
                headerAccessor.removeNativeHeader(HttpHeaders.AUTHORIZATION);
                return process(message, authHeader, headerAccessor);
            }
        }
        return message;
    }

    private Message<?> process(final @NotNull Message<?> message, final @NotNull String authHeader,
                               final @NotNull StompHeaderAccessor headerAccessor) {
        final String authToken = authHeader.substring(SecurityConstants.JWT_TOKEN_PREFIX.length());
        try {
            final TermItUserDetails userDetails = jwtUtils.extractUserInfo(authToken);
            final TermItUserDetails existingDetails = userDetailsService.loadUserByUsername(userDetails.getUsername());
            SecurityUtils.verifyAccountStatus(existingDetails.getUser());
            Authentication authentication = SecurityUtils.setCurrentUser(existingDetails);
            headerAccessor.setUser(authentication);
            return message;
        } catch (JwtException | DisabledException | LockedException | UsernameNotFoundException e) {
            throw new AuthorizationException(e.getMessage());
        }
    }
}
