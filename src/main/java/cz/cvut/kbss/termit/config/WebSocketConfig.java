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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.util.AntPathMatcher;

import java.nio.charset.StandardCharsets;

/*
We are not using @EnableWebSocketSecurity
it automatically requires CSRF which cannot be configured (disabled) at the moment
(will probably change in the future)
*/
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 98) // ensures priority above Spring Security
public class WebSocketConfig {

    private final ApplicationContext context;

    private final ObjectMapper jsonLdMapper;

    @Autowired
    public WebSocketConfig(ApplicationContext context, @Qualifier("jsonLdMapper") ObjectMapper jsonLdMapper) {
        this.context = context;
        this.jsonLdMapper = jsonLdMapper;
    }

    @Bean
    public StringMessageConverter termitStringMessageConverter() {
        return new StringMessageConverter(StandardCharsets.UTF_8);
    }

    @Bean
    public MappingJackson2MessageConverter termitJsonLdMessageConverter() {
        return new MappingJackson2MessageConverter(jsonLdMapper);
    }

    /**
     * WebSocket security setup (replaces {@link EnableWebSocketSecurity @EnableWebSocketSecurity})
     */
    @Bean
    @Scope("prototype")
    public MessageMatcherDelegatingAuthorizationManager.Builder messageAuthorizationManagerBuilder() {
        return MessageMatcherDelegatingAuthorizationManager.builder()
                                                           .simpDestPathMatcher(() -> (context.getBeanNamesForType(SimpAnnotationMethodMessageHandler.class).length > 0) ? context.getBean(SimpAnnotationMethodMessageHandler.class)
                                                                                                                                                                                  .getPathMatcher() : new AntPathMatcher());
    }

    /**
     * WebSocket endpoint authorization
     */
    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager() {
        return messageAuthorizationManagerBuilder().simpTypeMatchers(SimpMessageType.DISCONNECT).permitAll()
                                                   .anyMessage().authenticated().build();
    }
}
