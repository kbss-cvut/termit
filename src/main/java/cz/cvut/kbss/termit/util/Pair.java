package cz.cvut.kbss.termit.util;


import org.jetbrains.annotations.NotNull;

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
    public static class Comparable<T extends java.lang.Comparable<T>, V extends java.lang.Comparable<V>>
            extends Pair<T, V> implements java.lang.Comparable<Comparable<T, V>> {

        public Comparable(T first, V second) {
            super(first, second);
        }

        @Override
        public int compareTo(@NotNull Comparable<T, V> o) {
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
            Comparable<?, ?> that = (Comparable<?, ?>) o;
            return Objects.equals(getFirst(), that.getFirst()) && Objects.equals(getSecond(), that.getSecond());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getFirst(), getSecond());
        }
    }
}

