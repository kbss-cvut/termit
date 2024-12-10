package cz.cvut.kbss.termit.util.longrunning;

import cz.cvut.kbss.termit.event.ClearLongRunningTaskQueueEvent;
import cz.cvut.kbss.termit.event.LongRunningTaskChangedEvent;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LongRunningTasksRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(LongRunningTasksRegistry.class);

    private final ConcurrentHashMap<UUID, LongRunningTask> registry = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public LongRunningTasksRegistry(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void onTaskChanged(@Nonnull final LongRunningTask task) {
        final LongRunningTaskStatus status = new LongRunningTaskStatus(task);

        if (LOG.isTraceEnabled()) {
            LOG.atTrace().setMessage("Long running task changed state: {}{}").addArgument(status::getName)
               .addArgument(status).log();
        }

        handleTaskChanged(task);
        eventPublisher.publishEvent(new LongRunningTaskChangedEvent(this, status));
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    @EventListener(ClearLongRunningTaskQueueEvent.class)
    public void onClearLongRunningTaskQueueEvent() {
        AtomicInteger count = new AtomicInteger();
        LOG.info("Clearing long running task registry...");

        registry.entrySet().removeIf(entry -> {
            if (!entry.getValue().isRunning()) {
                count.incrementAndGet();
                return true;
            }
            return false;
        });
        performCleanup();

        if (count.get() > 0) {
            LOG.warn("Cleared {} non-running tasks from the registry", count.get());
        } else {
            LOG.info("Long running task registry cleared.");
        }
    }

    private void handleTaskChanged(@Nonnull final LongRunningTask task) {
        if(task.isDone()) {
            registry.remove(task.getUuid());
        } else {
            registry.put(task.getUuid(), task);
        }

        performCleanup();
    }

    private void performCleanup() {
        // perform cleanup
        registry.forEach((key, value) -> {
            if (value.isDone()) {
                registry.remove(key);
            }
        });

        if (LOG.isTraceEnabled() && registry.isEmpty()) {
            LOG.trace("All long running tasks completed");
        }
    }

    @Nonnull
    public List<LongRunningTaskStatus> getTasks() {
        return registry.values().stream().map(LongRunningTaskStatus::new).toList();
    }
}
