package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        termOccurrenceDao.removeAll(source);
        occurrences.stream().filter(o -> !o.getTerm().equals(source.getUri())).forEach(termOccurrenceDao::persist);
    }

    @Override
    public List<TermOccurrence> getExistingOccurrences(Asset<?> source) {
        return termOccurrenceDao.findAllTargeting(source);
    }
}
