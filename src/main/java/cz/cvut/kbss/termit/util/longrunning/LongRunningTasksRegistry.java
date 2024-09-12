package cz.cvut.kbss.termit.util.longrunning;

import cz.cvut.kbss.termit.event.AllLongRunningTasksCompletedEvent;
import cz.cvut.kbss.termit.event.LongRunningTaskChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class LongRunningTasksRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(LongRunningTasksRegistry.class);

    private final ConcurrentHashMap<UUID, LongRunningTaskStatus> registry = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher eventPublisher;

    private final ReentrantLock lock = new ReentrantLock(true); // using fair ordering

    @Autowired
    public LongRunningTasksRegistry(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Order(0) // ensures that registry is updated before controllers
    @EventListener
    public void onTaskChanged(final LongRunningTaskChangedEvent event) {
        // using fair ReentrantLock guarantees receiving events in the order
        lock.lock();
        try {
            handleTaskChanged(event);
        } finally {
            lock.unlock();
        }
    }

    private void handleTaskChanged(final LongRunningTaskChangedEvent event) {
        final LongRunningTaskStatus status = event.getStatus();
        if (LOG.isTraceEnabled()) {
            LOG.atTrace().setMessage("Long running task changed state: {}{}").addArgument(status::getName)
               .addArgument(status).log();
        }

        if(status.getState() == LongRunningTaskStatus.State.DONE) {
            registry.remove(status.getUuid());
        } else {
            registry.put(status.getUuid(), status);
        }
        if (registry.isEmpty()) {
            eventPublisher.publishEvent(new AllLongRunningTasksCompletedEvent(this));
            LOG.trace("All long running tasks completed");
        }
    }

    @NonNull
    public Map<UUID, LongRunningTaskStatus> getTasks() {
        return Collections.unmodifiableMap(registry);
    }
}
