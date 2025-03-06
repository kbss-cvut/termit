package cz.cvut.kbss.termit.model.util;

/**
 * This interface is used to mark classes that can create a copy of themselves.
 *
 * @param <T> Type of the object
 */
public interface Copyable<T extends HasIdentifier> {

    /**
     * Creates a copy of this instance, copying the attribute values.
     * <p>
     * Note that the identifier should not be copied into the new instance, so that it can be persisted.
     *
     * @return New instance with values copied from this one
     */
    T copy();
}
