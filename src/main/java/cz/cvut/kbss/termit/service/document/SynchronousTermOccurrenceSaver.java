package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.assignment.OccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.selector.Selector;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

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
        LOG.trace("Saving term occurrences for asset {}.", source);
        final List<TermOccurrence> existing = termOccurrenceDao.findAllTargeting(source);
        occurrences.stream().filter(o -> !o.getTerm().equals(source.getUri()))
                   .filter(o -> isNew(o, existing))
                   .forEach(termOccurrenceDao::persist);
    }

    /**
     * Checks whether the specified term occurrence is new or if there already exists an equivalent one.
     * <p>
     * Two occurrences are considered equivalent iff they represent the same term, they have a target with the same
     * source file, and the target contains at least one equal selector.
     *
     * @param occurrence The supposedly new occurrence to check
     * @param existing   Existing occurrences relevant to the specified file
     * @return Whether the occurrence is truly new
     */
    private static boolean isNew(TermOccurrence occurrence, List<TermOccurrence> existing) {
        final OccurrenceTarget target = occurrence.getTarget();
        assert target != null;
        final Set<Selector> selectors = target.getSelectors();
        for (TermOccurrence to : existing) {
            if (!to.getTerm().equals(occurrence.getTerm())) {
                continue;
            }
            final OccurrenceTarget fileTarget = to.getTarget();
            assert fileTarget != null;
            assert fileTarget.getSource().equals(target.getSource());
            // Same term, contains at least one identical selector
            if (fileTarget.getSelectors().stream().anyMatch(selectors::contains)) {
                LOG.trace("Skipping occurrence {} because another one with matching term and selectors exists.",
                          occurrence);
                return false;
            }
        }
        return true;
    }
}
