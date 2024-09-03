package cz.cvut.kbss.termit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.termit.security.JwtUtils;
import cz.cvut.kbss.termit.security.WebSocketJwtAuthorizationInterceptor;
import cz.cvut.kbss.termit.service.security.TermItUserDetailsService;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.websocket.handler.StompExceptionHandler;
import cz.cvut.kbss.termit.websocket.handler.WebSocketMessageWithHeadersValueHandler;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.context.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

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

    private final ObjectMapper jsonLdMapper;

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final JwtUtils jwtUtils;

    private final TermItUserDetailsService userDetailsService;

    @Autowired
    public WebSocketConfig(cz.cvut.kbss.termit.util.Configuration configuration, ApplicationContext context,
                           @Qualifier("jsonLdMapper") ObjectMapper jsonLdMapper,
                           @Lazy SimpMessagingTemplate simpMessagingTemplate, JwtUtils jwtUtils,
                           TermItUserDetailsService userDetailsService) {
        this.configuration = configuration;
        this.context = context;
        this.jsonLdMapper = jsonLdMapper;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    /**
     * WebSocket security setup (replaces {@link EnableWebSocketSecurity @EnableWebSocketSecurity})
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        AuthenticationPrincipalArgumentResolver resolver = new AuthenticationPrincipalArgumentResolver();
        argumentResolvers.add(resolver);
    }

    /**
     * WebSocket security setup (replaces {@link EnableWebSocketSecurity @EnableWebSocketSecurity})
     * @see <a href="https://github.com/spring-projects/spring-security/blob/6.3.x/config/src/main/java/org/springframework/security/config/annotation/web/socket/WebSocketMessageBrokerSecurityConfiguration.java#L97">Spring security source</a>
     */
    @Override
    public void configureClientInboundChannel(@NotNull ChannelRegistration registration) {
        AuthorizationChannelInterceptor interceptor = new AuthorizationChannelInterceptor(messageAuthorizationManager());
        interceptor.setAuthorizationEventPublisher(new SpringAuthorizationEventPublisher(this.context));
        registration.interceptors(webSocketJwtAuthorizationInterceptor(), new SecurityContextChannelInterceptor(), interceptor);
    }

    @Override
    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
        returnValueHandlers.add(new WebSocketMessageWithHeadersValueHandler(simpMessagingTemplate));
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(configuration.getCors().getAllowedOrigins().split(","));
        registry.setErrorHandler(new StompExceptionHandler());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/")
                .setUserDestinationPrefix("/user");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setTimeToFirstMessage(Constants.WEBSOCKET_TIME_TO_FIRST_MESSAGE);
        registry.setSendBufferSizeLimit(Constants.WEBSOCKET_SEND_BUFFER_SIZE_LIMIT);
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        messageConverters.add(termitJsonLdMessageConverter());
        messageConverters.add(termitStringMessageConverter());
        return false; // do not add default converters
    }

    @Bean
    public MessageConverter termitStringMessageConverter() {
        return new StringMessageConverter(StandardCharsets.UTF_8);
    }

    @Bean
    public MessageConverter termitJsonLdMessageConverter() {
        return new MappingJackson2MessageConverter(jsonLdMapper);
    }

    /**
     * WebSocket security setup (replaces {@link EnableWebSocketSecurity @EnableWebSocketSecurity})
     */
    @Bean
    @Scope("prototype")
    public MessageMatcherDelegatingAuthorizationManager.Builder messageAuthorizationManagerBuilder() {
        return MessageMatcherDelegatingAuthorizationManager.builder().simpDestPathMatcher(
                () -> (context.getBeanNamesForType(SimpAnnotationMethodMessageHandler.class).length > 0)
                        ? context.getBean(SimpAnnotationMethodMessageHandler.class).getPathMatcher()
                        : new AntPathMatcher());
    }

    /**
     * WebSocket endpoint authorization
     */
    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager() {
        return messageAuthorizationManagerBuilder().simpTypeMatchers(SimpMessageType.DISCONNECT).permitAll()
                       .anyMessage().authenticated().build();
    }

    @Bean
    public WebSocketJwtAuthorizationInterceptor webSocketJwtAuthorizationInterceptor() {
        return new WebSocketJwtAuthorizationInterceptor(jwtUtils, userDetailsService);
    }
}
