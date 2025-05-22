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
package cz.cvut.kbss.termit.websocket;

import cz.cvut.kbss.termit.event.LongRunningTaskChangedEvent;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTasksRegistry;
import jakarta.annotation.Nonnull;
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
    public void tasksRequest(@Nonnull MessageHeaders messageHeaders) {
        sendToSession(WebSocketDestinations.LONG_RUNNING_TASKS_UPDATE, registry.getTasks(), Map.of(), messageHeaders);
    }

    @EventListener(LongRunningTaskChangedEvent.class)
    public void onTaskChanged() {
        messagingTemplate.convertAndSend(WebSocketDestinations.LONG_RUNNING_TASKS_UPDATE, registry.getTasks());
    }
}
