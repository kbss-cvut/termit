package cz.cvut.kbss.termit.util.longrunning;

import cz.cvut.kbss.termit.event.LongRunningTaskChangedEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LongRunningTasksRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(LongRunningTasksRegistry.class);

    private final ConcurrentHashMap<String, LongRunningTaskStatus> registry = new ConcurrentHashMap<>();

    @EventListener
    public void onTaskChanged(LongRunningTaskChangedEvent event) {
        if (LOG.isTraceEnabled()) {
            synchronized (LongRunningTasksRegistry.class) {
                LOG.atTrace().setMessage("Long running task changed state: {}{}").addArgument(event::getName)
                   .addArgument(event::getStatus).log();
            }
        }

        if(event.getStatus().getState() == LongRunningTaskStatus.State.DONE) {
            registry.remove(event.getName());
        } else {
            registry.put(event.getName(), event.getStatus());
        }
    }

    @NotNull
    @UnmodifiableView
    public Map<String, LongRunningTaskStatus> getTasks() {
        return Collections.unmodifiableMap(registry);
    }
}
