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
package cz.cvut.kbss.termit.util;


import jakarta.annotation.Nonnull;

import java.util.Objects;

public class Pair<T, V> {

    private final T first;

    private final V second;

    public Pair(T first, V second) {
        this.first = first;
        this.second = second;
    }

    public T getFirst() {
        return first;
    }

    public V getSecond() {
        return second;
    }


    /**
     * First compares the first value, if they are equal, compares the second value.
     */
    public static class ComparablePair<T extends java.lang.Comparable<T>, V extends java.lang.Comparable<V>>
            extends Pair<T, V> implements java.lang.Comparable<ComparablePair<T, V>> {

        public ComparablePair(T first, V second) {
            super(first, second);
        }

        @Override
        public int compareTo(@Nonnull Pair.ComparablePair<T, V> o) {
            final int firstComparison = this.getFirst().compareTo(o.getFirst());
            if (firstComparison != 0) {
                return firstComparison;
            }
            return this.getSecond().compareTo(o.getSecond());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ComparablePair<?, ?> that = (ComparablePair<?, ?>) o;
            return Objects.equals(getFirst(), that.getFirst()) && Objects.equals(getSecond(), that.getSecond());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getFirst(), getSecond());
        }
    }
}

