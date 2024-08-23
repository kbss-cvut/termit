package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
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

    private void saveOccurrence(TermOccurrence occurrence, Asset<?> source) {
        if (occurrence.getTerm().equals(source.getUri())) {
            return;
        }
        LOG.debug("Saving a term occurrence for asset {}.", source);
        termOccurrenceDao.persist(occurrence);
    }

    @Transactional
    @Override
    public void saveFromQueue(final Asset<?> source, final AtomicBoolean finished,
                               final ConcurrentLinkedQueue<TermOccurrence> toSave) {
        removeAll(source);
        TermOccurrence occurrence;
        while (!finished.get() || !toSave.isEmpty()) {
            if (toSave.isEmpty()) {
                Thread.yield();
            }
            occurrence = toSave.poll();
            if (occurrence != null) {
                saveOccurrence(occurrence, source);
            }
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
