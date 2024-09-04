package cz.cvut.kbss.termit.environment.config;

import cz.cvut.kbss.termit.config.WebAppConfig;
import cz.cvut.kbss.termit.config.WebSocketConfig;
import cz.cvut.kbss.termit.config.WebSocketMessageBrokerConfig;
import cz.cvut.kbss.termit.security.WebSocketJwtAuthorizationInterceptor;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.websocket.handler.WebSocketExceptionHandler;
import cz.cvut.kbss.termit.websocket.util.ReturnValueCollectingSimpMessagingTemplate;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@TestConfiguration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(Configuration.class)
@Import({TestSecurityConfig.class, TestRestSecurityConfig.class, WebAppConfig.class, WebSocketConfig.class, WebSocketMessageBrokerConfig.class})
@ComponentScan(basePackages = {"cz.cvut.kbss.termit.websocket", "cz.cvut.kbss.termit.websocket.handler"})
public class TestWebSocketConfig
        implements ApplicationListener<ContextRefreshedEvent>, WebSocketMessageBrokerConfigurer {

    private final List<SubscribableChannel> channels;

    private final List<MessageHandler> handlers;

    @Autowired
    @Lazy
    public TestWebSocketConfig(List<SubscribableChannel> channels, List<MessageHandler> handlers) {
        this.channels = channels;
        this.handlers = handlers;
    }

    /**
     * Unregisters MessageHandler's from the message channels to reduce processing during the test.
     * Also stops further processing so for example user responses remain in the broker channel.
     * @param event the event to respond to
     */
    @Override
    public void onApplicationEvent(@NotNull ContextRefreshedEvent event) {
        for (MessageHandler handler : handlers) {
            if (handler instanceof SimpAnnotationMethodMessageHandler) {
                continue;
            }
            for (SubscribableChannel channel : channels) {
                channel.unsubscribe(handler);
            }
        }
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.executor(new SyncTaskExecutor());
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.executor(new SyncTaskExecutor());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.configureBrokerChannel().executor(new SyncTaskExecutor());
    }

    @Bean
    public Map<UUID, Object> returnedValuesMap() {
        return new HashMap<>();
    }

    @Bean
    @Primary
    public SimpMessagingTemplate brokerMessagingTemplate(
            AbstractSubscribableChannel brokerChannel, CompositeMessageConverter brokerMessageConverter) {

        SimpMessagingTemplate template = new ReturnValueCollectingSimpMessagingTemplate(brokerChannel, returnedValuesMap());
        template.setMessageConverter(brokerMessageConverter);
        return template;
    }

    @Bean
    public WebSocketJwtAuthorizationInterceptor webSocketJwtAuthorizationInterceptor(JwtAuthenticationProvider jwtAuthenticationProvider) {
        return new WebSocketJwtAuthorizationInterceptor(jwtAuthenticationProvider);
    }
}
