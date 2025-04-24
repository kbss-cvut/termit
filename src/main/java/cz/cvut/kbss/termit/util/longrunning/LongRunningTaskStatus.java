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
package cz.cvut.kbss.termit.util.longrunning;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public class LongRunningTaskStatus implements Serializable {

    private final String name;

    private final UUID uuid;

    private final State state;

    private final Instant startedAt;

    public LongRunningTaskStatus(@Nonnull LongRunningTask task) {
        Objects.requireNonNull(task.getName());
        this.name = task.getName();
        this.startedAt = task.startedAt().map(time -> time.truncatedTo(ChronoUnit.SECONDS)).orElse(null);
        this.state = State.of(task);
        this.uuid = task.getUuid();
    }

    public LongRunningTaskStatus(@Nonnull String name, @Nonnull UUID uuid, @Nonnull State state,
                                 @Nullable Instant startedAt) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(state);
        this.name = name;
        this.uuid = uuid;
        this.state = state;
        this.startedAt = startedAt;
    }

    public @Nonnull String getName() {
        return name;
    }

    public @Nonnull State getState() {
        return state;
    }

    public @Nullable Instant getStartedAt() {
        return startedAt;
    }

    public @Nonnull UUID getUuid() {
        return uuid;
    }

    @Override
    public String toString() {
        return "{" + state.name() + (startedAt == null ? "" : ", startedAt=" + startedAt) + ", " + uuid + "}";
    }

    public enum State {
        PENDING, RUNNING, DONE;

        public static State of(@Nonnull LongRunningTask task) {
            if (task.isRunning()) {
                return RUNNING;
            } else if (task.isDone()) {
                return DONE;
            } else {
                return PENDING;
            }
        }
    }
}
