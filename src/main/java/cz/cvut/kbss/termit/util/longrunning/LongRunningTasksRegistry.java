package cz.cvut.kbss.termit.util.longrunning;

import cz.cvut.kbss.termit.event.LongRunningTaskChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LongRunningTasksRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(LongRunningTasksRegistry.class);

    private final ConcurrentHashMap<UUID, LongRunningTask> registry = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public LongRunningTasksRegistry(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void onTaskChanged(@NonNull final LongRunningTask task) {
        final LongRunningTaskStatus status = new LongRunningTaskStatus(task);

        if (LOG.isTraceEnabled()) {
            LOG.atTrace().setMessage("Long running task changed state: {}{}").addArgument(status::getName)
               .addArgument(status).log();
        }

        handleTaskChanged(task);
        eventPublisher.publishEvent(new LongRunningTaskChangedEvent(this, status));
    }

    private void handleTaskChanged(@NonNull final LongRunningTask task) {
        if(task.isDone()) {
            registry.remove(task.getUuid());
        } else {
            registry.put(task.getUuid(), task);
        }

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

    @NonNull
    public List<LongRunningTaskStatus> getTasks() {
        return registry.values().stream().map(LongRunningTaskStatus::new).toList();
    }
}
