package cz.cvut.kbss.termit.util.longrunning;

import cz.cvut.kbss.termit.event.AllLongRunningTasksCompletedEvent;
import cz.cvut.kbss.termit.event.LongRunningTaskChangedEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LongRunningTasksRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(LongRunningTasksRegistry.class);

    private final ConcurrentHashMap<String, LongRunningTaskStatus> registry = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public LongRunningTasksRegistry(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void onTaskChanged(LongRunningTaskChangedEvent event) {
        final LongRunningTaskStatus status = event.getStatus();
        if (LOG.isTraceEnabled()) {
            synchronized (LongRunningTasksRegistry.class) {
                LOG.atTrace().setMessage("Long running task changed state: {}{}").addArgument(status::getName)
                   .addArgument(status).log();
            }
        }

        if(status.getState() == LongRunningTaskStatus.State.DONE) {
            registry.remove(status.getName());
        } else {
            registry.put(status.getName(), status);
        }
        if (registry.isEmpty()) {
            eventPublisher.publishEvent(new AllLongRunningTasksCompletedEvent(this));
            LOG.trace("All long running tasks completed");
        }
    }

    @NotNull
    @UnmodifiableView
    public Map<String, LongRunningTaskStatus> getTasks() {
        return Collections.unmodifiableMap(registry);
    }
}
