package cz.cvut.kbss.termit.util.throttle;


import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * First compares the first value, if they are equal, compares the second value.
 */
public class ComparablePair<T extends Comparable<T>, V extends Comparable<V>>
        implements Comparable<ComparablePair<T, V>> {

    private final T first;

    private final V second;

    public ComparablePair(T first, V second) {
        this.first = first;
        this.second = second;
    }

    public T getFirst() {
        return first;
    }

    public V getSecond() {
        return second;
    }

    @Override
    public int compareTo(@NotNull ComparablePair<T, V> o) {
        final int firstComparison = this.first.compareTo(o.first);
        if (firstComparison != 0) {
            return firstComparison;
        }
        return this.second.compareTo(o.second);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComparablePair<?, ?> that = (ComparablePair<?, ?>) o;
        return Objects.equals(first, that.first) && Objects.equals(second, that.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }
}

