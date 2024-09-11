package cz.cvut.kbss.termit.websocket;

import cz.cvut.kbss.termit.event.AllLongRunningTasksCompletedEvent;
import cz.cvut.kbss.termit.event.LongRunningTaskChangedEvent;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTasksRegistry;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.event.EventListener;
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
    public void tasksRequest(@NotNull MessageHeaders messageHeaders) {
        sendToSession(WebSocketDestinations.LONG_RUNNING_TASKS_UPDATE, "", Map.of("reset", true), messageHeaders);
        registry.getTasks().values()
                .forEach(status -> sendToSession(WebSocketDestinations.LONG_RUNNING_TASKS_UPDATE, status, Map.of(), messageHeaders));
    }

    @EventListener(AllLongRunningTasksCompletedEvent.class)
    public void onAllTasksCompleted() {
        messagingTemplate.convertAndSend(WebSocketDestinations.LONG_RUNNING_TASKS_UPDATE, "", Map.of("reset", true));
    }

    @EventListener
    public void onTaskChanged(LongRunningTaskChangedEvent event) {
        messagingTemplate.convertAndSend(WebSocketDestinations.LONG_RUNNING_TASKS_UPDATE, event.getStatus());
    }
}
