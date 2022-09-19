package cz.cvut.kbss.termit.model.util;

/**
 * Indicates that snapshots of individuals of this type can be created.
 */
public interface SupportsSnapshots extends HasIdentifier {

    /**
     * Checks whether this particular instance is a snapshot or not.
     *
     * @return {@code true} if this instance is a snapshot, {@code false} otherwise
     */
    boolean isSnapshot();
}
