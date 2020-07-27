package cz.cvut.kbss.termit.asset.provenance;

/**
 * Indicates that last modification date of assets is tracked by this class.
 */
public interface SupportsLastModification {

    /**
     * Gets timestamp of the last modification of assets managed by this class.
     *
     * @return Timestamp of last modification in millis since epoch
     */
    long getLastModified();

    /**
     * Refreshes the last modified value.
     * <p>
     * This method is required only for implementations which actually store the last modified value. Those which act
     * only as delegates need not implement it.
     */
    default void refreshLastModified() {
    }
}
