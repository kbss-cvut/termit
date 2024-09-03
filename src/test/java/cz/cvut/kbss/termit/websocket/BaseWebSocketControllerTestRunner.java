package cz.cvut.kbss.termit.websocket;

import cz.cvut.kbss.termit.environment.config.TestWebSocketConfig;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.websocket.util.CachingChannelInterceptor;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static cz.cvut.kbss.termit.websocket.util.ReturnValueCollectingSimpMessagingTemplate.MESSAGE_IDENTIFIER_HEADER;

@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ExtendWith(MockitoExtension.class)
@EnableConfigurationProperties({Configuration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = {TestWebSocketConfig.class},
                      initializers = {ConfigDataApplicationContextInitializer.class})
public abstract class BaseWebSocketControllerTestRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BaseWebSocketControllerTestRunner.class);

    /**
     * Simulated messages from client to server
     */
    @Autowired
    @Qualifier("clientInboundChannel")
    protected AbstractSubscribableChannel serverInboundChannel;

    /**
     * Messages sent from the server to the client
     */
    @Autowired
    @Qualifier("clientOutboundChannel")
    protected AbstractSubscribableChannel serverOutboundChannel;

    @Autowired
    protected AbstractSubscribableChannel brokerChannel;

    /**
     * Holds message ids mapped to the values returned from the controllers
     */
    @Autowired
    protected Map<UUID, Object> returnedValuesMap;

    /**
     * Caches any messages sent from the server to the client
     */
    protected CachingChannelInterceptor serverOutboundChannelInterceptor;

    protected CachingChannelInterceptor brokerChannelInterceptor;

    @PostConstruct
    protected void runnerPostConstruct() {
        this.brokerChannelInterceptor = new CachingChannelInterceptor();
        this.serverOutboundChannelInterceptor = new CachingChannelInterceptor();

        this.brokerChannel.addInterceptor(this.brokerChannelInterceptor);
        this.serverOutboundChannel.addInterceptor(this.serverOutboundChannelInterceptor);
    }

    @BeforeEach
    protected void runnerBeforeEach() {
        this.serverOutboundChannelInterceptor.reset();
        this.brokerChannelInterceptor.reset();
        this.returnedValuesMap.clear();
    }

    /**
     * Returns result of controller method associated with the specified message.
     *
     * @param message The message sent from some controller
     */
    @SuppressWarnings("unchecked")
    protected <T> Optional<T> readPayload(Message<?> message) throws ClassCastException {
        final UUID id = message.getHeaders().get(MESSAGE_IDENTIFIER_HEADER, UUID.class);
        if (id == null) {
            LOG.error("Unable to read message payload. Message id is null.");
            return Optional.empty();
        }
        if (returnedValuesMap.containsKey(id)) {
            return Optional.of((T) returnedValuesMap.get(id));
        }
        return Optional.empty();
    }
}
