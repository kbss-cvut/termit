package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Saves occurrences synchronously.
 * <p>
 * Existing occurrences are reused if they match.
 */
@Service
public class TermOccurrenceSaver {

    private static final Logger LOG = LoggerFactory.getLogger(TermOccurrenceSaver.class);

    private final TermOccurrenceDao termOccurrenceDao;

    public TermOccurrenceSaver(TermOccurrenceDao termOccurrenceDao) {
        this.termOccurrenceDao = termOccurrenceDao;
    }

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
    @Transactional
    public void saveOccurrences(List<TermOccurrence> occurrences, Asset<?> source) {
        LOG.debug("Saving term occurrences for asset {}.", source);
        removeAll(source);
        LOG.trace("Persisting new occurrences in {}.", source);
        occurrences.stream().filter(o -> !o.getTerm().equals(source.getUri())).forEach(termOccurrenceDao::persist);
    }

    public void saveOccurrence(TermOccurrence occurrence, Asset<?> source) {
        if (occurrence.getTerm().equals(source.getUri())) {
            return;
        }
        if(!termOccurrenceDao.exists(occurrence.getUri())) {
            termOccurrenceDao.persist(occurrence);
        } else {
            LOG.debug("Occurrence already exists, skipping: {}", occurrence);
        }
    }

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
    @Transactional
    public void saveFromQueue(final Asset<?> source, final AtomicBoolean finished,
                              final BlockingQueue<TermOccurrence> toSave) {
        LOG.debug("Saving term occurrences for asset {}.", source);
        removeAll(source);
        TermOccurrence occurrence;
        long count = 0;
        try {
            while (!finished.get() || !toSave.isEmpty()) {
                if (toSave.isEmpty()) {
                    Thread.yield();
                }
                occurrence = toSave.poll(1, TimeUnit.SECONDS);
                if (occurrence != null) {
                    saveOccurrence(occurrence, source);
                    count++;
                }
            }
            LOG.debug("Saved {} term occurrences for asset {}.", count, source);
        } catch (InterruptedException e) {
            LOG.error("Thread interrupted while waiting for occurrences to save.");
            Thread.currentThread().interrupt();
            throw new TermItException(e);
        }
    }

    /**
     * Gets a list of existing term occurrences in the specified asset.
     *
     * @param source Asset in which the terms occur
     * @return List of existing term occurrences
     */
    public List<TermOccurrence> getExistingOccurrences(Asset<?> source) {
        return termOccurrenceDao.findAllTargeting(source);
    }

    private void removeAll(Asset<?> source) {
        LOG.trace("Removing all existing occurrences in asset {}.", source);
        termOccurrenceDao.removeAll(source);
    }
}
