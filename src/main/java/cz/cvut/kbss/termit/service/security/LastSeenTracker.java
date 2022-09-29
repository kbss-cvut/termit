package cz.cvut.kbss.termit.service.security;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the most recent access to the system by users.
 * <p>
 * Note that the tracking is done in memory, so the data do not persist between system restarts.
 */
@Service
public class LastSeenTracker {

    private final Map<URI, Instant> lastSeen = new ConcurrentHashMap<>();

    /**
     * Sets last seen timestamp for the specified user.
     *
     * @param userId    User identifier
     * @param timestamp Timestamp of last access
     */
    public void updateLastSeen(URI userId, Instant timestamp) {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(timestamp);
        lastSeen.put(userId, timestamp);
    }

    /**
     * Gets timestamp of the last access to the system by the specified user.
     *
     * @param userId User identifier
     * @return Timestamp of last access, empty if it is not recorded
     */
    public Optional<Instant> getlastSeen(URI userId) {
        return Optional.ofNullable(lastSeen.get(userId));
    }
}
