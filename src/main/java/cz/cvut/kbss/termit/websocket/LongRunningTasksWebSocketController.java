package cz.cvut.kbss.termit.websocket;

import cz.cvut.kbss.termit.event.LongRunningTaskChangedEvent;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTasksRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@MessageMapping("/long-running-tasks")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class LongRunningTasksWebSocketController extends BaseWebSocketController {

    private final LongRunningTasksRegistry registry;

    protected LongRunningTasksWebSocketController(IdentifierResolver idResolver, Configuration config,
                                                  SimpMessagingTemplate messagingTemplate,
                                                  LongRunningTasksRegistry registry) {
        super(idResolver, config, messagingTemplate);
        this.registry = registry;
    }

    @SubscribeMapping("/update")
    public void tasksRequest(@NonNull MessageHeaders messageHeaders) {
        sendToSession(WebSocketDestinations.LONG_RUNNING_TASKS_UPDATE, registry.getTasks(), Map.of(), messageHeaders);
    }

    @EventListener(LongRunningTaskChangedEvent.class)
    public void onTaskChanged() {
        messagingTemplate.convertAndSend(WebSocketDestinations.LONG_RUNNING_TASKS_UPDATE, registry.getTasks());
    }
}
