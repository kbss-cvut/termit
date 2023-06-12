package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.util.HasIdentifier;

import java.util.List;

/**
 * Service which can provide change records for assets.
 *
 * @param <T> Type of asset to get changes for
 */
public interface ChangeRecordProvider<T extends HasIdentifier> {

    /**
     * Gets change records of the specified asset.
     *
     * @param asset Asset to find change records for
     * @return List of change records, ordered by record timestamp in descending order
     */
    List<AbstractChangeRecord> getChanges(T asset);
}
