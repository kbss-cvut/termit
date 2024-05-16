package cz.cvut.kbss.termit.persistence.dao.util;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Drops registered repository contexts at scheduled moments.
 * <p>
 * This allows to move time-consuming removal of repository contexts containing a lot of data to times of low system
 * activity.
 */
@Component
public class ScheduledContextRemover {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledContextRemover.class);

    private final EntityManager em;

    private final Set<URI> contextsToRemove = new HashSet<>();

    public ScheduledContextRemover(EntityManager em) {
        this.em = em;
    }

    /**
     * Schedules the specified context identifier for removal at the next execution of the context cleanup.
     *
     * @param contextUri Identifier of the context to remove
     * @see #runContextRemoval()
     */
    public synchronized void scheduleForRemoval(@NonNull URI contextUri) {
        LOG.debug("Scheduling context {} for removal.", Utils.uriToString(contextUri));
        contextsToRemove.add(Objects.requireNonNull(contextUri));
    }

    /**
     * Runs the removal of the registered repository contexts.
     * <p>
     * This method is scheduled and should not be invoked manually.
     *
     * @see #scheduleForRemoval(URI)
     */
    @Transactional
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void runContextRemoval() {
        LOG.trace("Running scheduled repository context removal.");
        contextsToRemove.forEach(g -> {
            LOG.trace("Dropping repository context {}.", Utils.uriToString(g));
            em.createNativeQuery("DROP GRAPH ?g").setParameter("g", g).executeUpdate();
        });
        contextsToRemove.clear();
    }
}
