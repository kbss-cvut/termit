package cz.cvut.kbss.termit.service.term;

import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.persistence.dao.TermDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Component
public class OrphanedInverseTermRelationshipRemover {

    private static final Logger LOG = LoggerFactory.getLogger(OrphanedInverseTermRelationshipRemover.class);

    private final TermDao termDao;

    public OrphanedInverseTermRelationshipRemover(TermDao termDao) {
        this.termDao = termDao;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeOrphanedInverseTermRelationships(Term update, Term original) {
        removeOrphanedRelated(update, original);
        removeOrphanedRelatedMatch(update, original);
    }

    private Set<TermInfo> determineOrphaned(Set<TermInfo> newValue, Set<TermInfo> originalValue) {
        if (originalValue == null || originalValue.isEmpty()) {
            return Collections.emptySet();
        }
        final Set<TermInfo> orphaned = new HashSet<>(originalValue);
        if (newValue != null) {
            orphaned.removeAll(newValue);
        }
        return orphaned;
    }

    private void removeOrphanedRelated(Term update, Term original) {
        LOG.trace("Removing orphaned inverse related relationships of term {}.", update);
        final TermInfo tiUpdate = new TermInfo(update);
        final Set<TermInfo> orphaned = determineOrphaned(update.getInverseRelated(), original.getInverseRelated());
        LOG.trace("Found {} orphaned related to remove.", orphaned);
        orphaned.forEach(o -> {
            final Optional<Term> tOpt = termDao.find(o.getUri());
            assert tOpt.isPresent();
            final Term t = tOpt.get();
            if (t.getRelated() != null) {
                t.getRelated().remove(tiUpdate);
            }
        });
    }

    private void removeOrphanedRelatedMatch(Term update, Term original) {
        LOG.trace("Removing orphaned inverse relatedMatch relationships of term {}.", update);
        final TermInfo tiUpdate = new TermInfo(update);
        final Set<TermInfo> orphaned =
                determineOrphaned(update.getInverseRelatedMatch(), original.getInverseRelatedMatch());
        LOG.trace("Found {} orphaned relatedMatch to remove.", orphaned);
        orphaned.forEach(o -> {
            final Optional<Term> tOpt = termDao.find(o.getUri());
            assert tOpt.isPresent();
            final Term t = tOpt.get();
            if (t.getRelatedMatch() != null) {
                t.getRelatedMatch().remove(tiUpdate);
            }
        });
    }
}
