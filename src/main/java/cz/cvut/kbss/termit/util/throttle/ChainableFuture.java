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

import java.util.concurrent.Future;
import java.util.function.Consumer;

public interface ChainableFuture<T, F extends ChainableFuture<T, F>> extends Future<T> {

    /**
     * Executes this action once the future is completed.
     * Action is executed no matter if the future is completed successfully, exceptionally or cancelled.
     * <p>
     * If the future is already completed, it is executed synchronously.
     * <p>
     * Note that you <b>must</b> use the future passed as the parameter and not the original future object.
     * @param action action receiving this future after completion
     * @return this future
     */
    ChainableFuture<T, F> then(Consumer<F> action);
}
