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
package cz.cvut.kbss.termit.util.throttle;

import jakarta.annotation.Nonnull;

import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduledFutureTask<T> extends FutureTask<T> implements ScheduledFuture<T> {

    public ScheduledFutureTask(@Nonnull Callable<T> callable) {
        super(callable);
    }

    public ScheduledFutureTask(@Nonnull Runnable runnable, T result) {
        super(runnable, result);
    }

    @Override
    public long getDelay(@Nonnull TimeUnit unit) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public int compareTo(@Nonnull Delayed o) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
