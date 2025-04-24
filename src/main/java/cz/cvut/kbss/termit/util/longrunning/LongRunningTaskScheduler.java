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

/**
 * An object that will schedule a long-running tasks
 * @see LongRunningTask
 */
public abstract class LongRunningTaskScheduler {
    private final LongRunningTasksRegistry registry;

    protected LongRunningTaskScheduler(LongRunningTasksRegistry registry) {
        this.registry = registry;
    }

    protected final void notifyTaskChanged(final @Nonnull LongRunningTask task) {
        final String name = task.getName();
        if (name != null && !name.isBlank()) {
            registry.onTaskChanged(task);
        }
    }
}
