package cz.cvut.kbss.termit.websocket;

import cz.cvut.kbss.termit.dto.LongRunningTaskDto;
import cz.cvut.kbss.termit.event.LongRunningTaskChangedEvent;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
@MessageMapping("/long-running-tasks")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class LongRunningTasksWebSocketController extends BaseWebSocketController {
    protected LongRunningTasksWebSocketController(IdentifierResolver idResolver, Configuration config,
                                                  SimpMessagingTemplate messagingTemplate) {
        super(idResolver, config, messagingTemplate);
    }

    @EventListener
    public void onTaskChanged(LongRunningTaskChangedEvent event) {
        messagingTemplate.convertAndSend(WebSocketDestinations.LONG_RUNNING_TASKS_UPDATE, new LongRunningTaskDto(event));
    }
}
