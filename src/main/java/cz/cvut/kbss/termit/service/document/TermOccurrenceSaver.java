package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Saves occurrences of terms.
 */
public interface TermOccurrenceSaver {

    /**
     * Saves the specified occurrences of terms in the specified asset.
     * <p>
     * Removes all existing occurrences.
     * <p>
     * Implementations may reuse existing occurrences if they match the provided ones.
     *
     * @param occurrences Occurrences to save
     * @param source      Asset in which the terms occur
     */
    void saveOccurrences(List<TermOccurrence> occurrences, Asset<?> source);

    /**
     * Continously saves occurrences from the queue while blocking current thread until
     * {@code #finished} is set to {@code true}.
     * <p>
     * Removes all existing occurrences before processing.
     *
     * @param source   Asset in which the terms occur
     * @param finished Whether all occurrences were added to the queue
     * @param toSave   the queue with occurrences to save
     */
    void saveFromQueue(final Asset<?> source, final AtomicBoolean finished,
                       final BlockingQueue<TermOccurrence> toSave);

    void saveOccurrence(TermOccurrence occurrence, Asset<?> source);

    /**
     * Gets a list of existing term occurrences in the specified asset.
     *
     * @param source Asset in which the terms occur
     * @return List of existing term occurrences
     */
    List<TermOccurrence> getExistingOccurrences(Asset<?> source);
}
