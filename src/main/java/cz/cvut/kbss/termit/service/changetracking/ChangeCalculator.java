package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;

import java.util.Collection;

/**
 * Calculates changes made to an asset when compared to its original from the repository.
 */
interface ChangeCalculator {

    /**
     * Calculates the set of changes made to the specified asset.
     * <p>
     * Note that this method assumes the asset already exists, so creation change records are not generated by it. Also,
     * the implementations need not generate provenance data (timestamp, authorship) for the change records.
     *
     * @param changed  The updated asset
     * @param original The original asset against which changes are calculated
     * @return A collection of changes made to the asset
     */
    Collection<UpdateChangeRecord> calculateChanges(Asset changed, Asset original);
}
