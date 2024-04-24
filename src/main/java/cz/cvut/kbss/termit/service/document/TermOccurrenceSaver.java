package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;

import java.util.List;

/**
 * Saves occurrences of terms.
 */
public interface TermOccurrenceSaver {

    /**
     * Saves the specified occurrences of terms in the specified asset.
     * <p>
     * Implementations may reuse existing occurrences if they match the provided ones.
     *
     * @param occurrences Occurrences to save
     * @param source      Asset in which the terms occur
     */
    void saveOccurrences(List<TermOccurrence> occurrences, Asset<?> source);

    /**
     * Gets a list of existing term occurrences in the specified asset.
     *
     * @param source Asset in which the terms occur
     * @return List of existing term occurrences
     */
    List<TermOccurrence> getExistingOccurrences(Asset<?> source);
}
