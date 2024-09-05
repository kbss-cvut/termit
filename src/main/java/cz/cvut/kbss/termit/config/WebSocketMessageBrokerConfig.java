package cz.cvut.kbss.termit.config;

import cz.cvut.kbss.termit.security.WebSocketJwtAuthorizationInterceptor;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.websocket.handler.StompExceptionHandler;
import cz.cvut.kbss.termit.websocket.handler.WebSocketExceptionHandler;
import org.jetbrains.annotations.NotNull;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.context.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99) // ensures priority above Spring Security
public class WebSocketMessageBrokerConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthorizationManager<Message<?>> messageAuthorizationManager;

    private final ApplicationContext context;

    private final WebSocketJwtAuthorizationInterceptor webSocketJwtAuthorizationInterceptor;

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final String allowedOrigins;

    private final StringMessageConverter termitStringMessageConverter;

    private final MappingJackson2MessageConverter termitJsonLdMessageConverter;

    private final WebSocketExceptionHandler webSocketExceptionHandler;

    public WebSocketMessageBrokerConfig(AuthorizationManager<Message<?>> messageAuthorizationManager,
                                        ApplicationContext context,
                                        WebSocketJwtAuthorizationInterceptor webSocketJwtAuthorizationInterceptor,
                                        @Lazy SimpMessagingTemplate simpMessagingTemplate,
                                        StringMessageConverter termitStringMessageConverter,
                                        MappingJackson2MessageConverter termitJsonLdMessageConverter,
                                        cz.cvut.kbss.termit.util.Configuration configuration,
                                        WebSocketExceptionHandler webSocketExceptionHandler) {
        this.messageAuthorizationManager = messageAuthorizationManager;
        this.context = context;
        this.webSocketJwtAuthorizationInterceptor = webSocketJwtAuthorizationInterceptor;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.termitStringMessageConverter = termitStringMessageConverter;
        this.termitJsonLdMessageConverter = termitJsonLdMessageConverter;

        this.allowedOrigins = configuration.getCors().getAllowedOrigins();
        this.webSocketExceptionHandler = webSocketExceptionHandler;
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
     *
     * @see <a href="https://github.com/spring-projects/spring-security/blob/6.3.x/config/src/main/java/org/springframework/security/config/annotation/web/socket/WebSocketMessageBrokerSecurityConfiguration.java#L97">Spring security source</a>
     */
    @Override
    public void configureClientInboundChannel(@NotNull ChannelRegistration registration) {
        AuthorizationChannelInterceptor interceptor = new AuthorizationChannelInterceptor(messageAuthorizationManager);
        interceptor.setAuthorizationEventPublisher(new SpringAuthorizationEventPublisher(context));
        registration.interceptors(webSocketJwtAuthorizationInterceptor, new SecurityContextChannelInterceptor(), interceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins.split(","));
        registry.setErrorHandler(new StompExceptionHandler(webSocketExceptionHandler));
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
        messageConverters.add(termitJsonLdMessageConverter);
        messageConverters.add(termitStringMessageConverter);
        return false; // do not add default converters
    }
}
