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
import java.util.function.Function;

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
        removeOrphanedExactMatches(update, original);
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
        removeOrphaned(tiUpdate, orphaned, Term::getRelated);
    }

    private void removeOrphaned(TermInfo toRemove, Set<TermInfo> orphaned, Function<Term, Set<TermInfo>> getter) {
        orphaned.forEach(o -> {
            final Optional<Term> tOpt = termDao.find(o.getUri());
            assert tOpt.isPresent();
            final Term t = tOpt.get();
            if (getter.apply(t) != null) {
                getter.apply(t).remove(toRemove);
            }
        });
    }

    private void removeOrphanedRelatedMatch(Term update, Term original) {
        LOG.trace("Removing orphaned inverse relatedMatch relationships of term {}.", update);
        final TermInfo tiUpdate = new TermInfo(update);
        final Set<TermInfo> orphaned =
                determineOrphaned(update.getInverseRelatedMatch(), original.getInverseRelatedMatch());
        LOG.trace("Found {} orphaned relatedMatch to remove.", orphaned);
        removeOrphaned(tiUpdate, orphaned, Term::getRelatedMatch);
    }

    private void removeOrphanedExactMatches(Term update, Term original) {
        LOG.trace("Removing orphaned inverse exactMatch relationships of term {}.", update);
        final TermInfo tiUpdate = new TermInfo(update);
        final Set<TermInfo> orphaned =
                determineOrphaned(update.getInverseExactMatchTerms(), original.getInverseExactMatchTerms());
        LOG.trace("Found {} orphaned exactMatch to remove.", orphaned);
        removeOrphaned(tiUpdate, orphaned, Term::getExactMatchTerms);
    }
}
