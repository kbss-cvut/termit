package cz.cvut.kbss.termit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.termit.security.WebSocketJwtAuthorizationInterceptor;
import cz.cvut.kbss.termit.util.WebSocketMessageWithHeadersValueHandler;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.context.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/*
We are not using @EnableWebSocketSecurity
it automatically requires CSRF which cannot be configured (disabled) at the moment
(will probably change in the future)
*/
@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99) // ensures priority above Spring Security
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final cz.cvut.kbss.termit.util.Configuration configuration;

    private final ApplicationContext context;

    private final AuthorizationManager<Message<?>> messageAuthorizationManager;

    private final WebSocketJwtAuthorizationInterceptor jwtAuthorizationInterceptor;

    private final ObjectMapper jsonLdMapper;

    private final SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    public WebSocketConfig(cz.cvut.kbss.termit.util.Configuration configuration, ApplicationContext context,
                           AuthorizationManager<Message<?>> messageAuthorizationManager,
                           WebSocketJwtAuthorizationInterceptor jwtAuthorizationInterceptor,
                           @Qualifier("jsonLdMapper") ObjectMapper jsonLdMapper,
                           @Lazy SimpMessagingTemplate simpMessagingTemplate) {
        this.configuration = configuration;
        this.context = context;
        this.messageAuthorizationManager = messageAuthorizationManager;
        this.jwtAuthorizationInterceptor = jwtAuthorizationInterceptor;
        this.jsonLdMapper = jsonLdMapper;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    /* WebSocket security setup (replaces @EnableWebSocketSecurity) */

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        AuthenticationPrincipalArgumentResolver resolver = new AuthenticationPrincipalArgumentResolver();
        argumentResolvers.add(resolver);
    }

    /**
     * @see <a href="https://github.com/spring-projects/spring-security/blob/6.3.x/config/src/main/java/org/springframework/security/config/annotation/web/socket/WebSocketMessageBrokerSecurityConfiguration.java#L97">Spring security source</a>
     */
    @Override
    public void configureClientInboundChannel(@NotNull ChannelRegistration registration) {
        AuthorizationChannelInterceptor interceptor = new AuthorizationChannelInterceptor(this.messageAuthorizationManager);
        interceptor.setAuthorizationEventPublisher(new SpringAuthorizationEventPublisher(this.context));
        registration.interceptors(jwtAuthorizationInterceptor, new SecurityContextChannelInterceptor(), interceptor);
    }

    @Override
    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
        returnValueHandlers.add(new WebSocketMessageWithHeadersValueHandler(simpMessagingTemplate));
    }

    /* WebSocket endpoint configuration */

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(configuration.getCors().getAllowedOrigins().split(","));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/");
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        messageConverters.add(termitJsonLdMessageConverter());
        messageConverters.add(termitStringMessageConverter());
        return true;
    }

    public MessageConverter termitStringMessageConverter() {
        return new StringMessageConverter(StandardCharsets.UTF_8);
    }

    public MessageConverter termitJsonLdMessageConverter() {
        return new MappingJackson2MessageConverter(jsonLdMapper);
    }

}
