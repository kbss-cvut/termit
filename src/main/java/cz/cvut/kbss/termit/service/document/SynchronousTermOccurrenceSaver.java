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
public class SynchronousTermOccurrenceSaver implements TermOccurrenceSaver {

    private static final Logger LOG = LoggerFactory.getLogger(SynchronousTermOccurrenceSaver.class);

    private final TermOccurrenceDao termOccurrenceDao;

    public SynchronousTermOccurrenceSaver(TermOccurrenceDao termOccurrenceDao) {
        this.termOccurrenceDao = termOccurrenceDao;
    }

    @Transactional
    @Override
    public void saveOccurrences(List<TermOccurrence> occurrences, Asset<?> source) {
        LOG.debug("Saving term occurrences for asset {}.", source);
        removeAll(source);
        LOG.trace("Persisting new occurrences in {}.", source);
        occurrences.stream().filter(o -> !o.getTerm().equals(source.getUri())).forEach(termOccurrenceDao::persist);
    }

    @Override
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

    @Transactional
    @Override
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
            LOG.debug("Saved {} term occurrences for assert {}.", count, source);
        } catch (InterruptedException e) {
            LOG.error("Thread interrupted while waiting for occurrences to save.");
            Thread.currentThread().interrupt();
            throw new TermItException(e);
        }
    }

    @Override
    public List<TermOccurrence> getExistingOccurrences(Asset<?> source) {
        return termOccurrenceDao.findAllTargeting(source);
    }

    private void removeAll(Asset<?> source) {
        LOG.trace("Removing all existing occurrences in asset {}.", source);
        termOccurrenceDao.removeAll(source);
    }
}
