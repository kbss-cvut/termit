package cz.cvut.kbss.termit.service.term;

import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.dao.TermDao;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

@Component
public class OrphanedInverseTermRelationshipRemover {

    private final TermDao termDao;

    public OrphanedInverseTermRelationshipRemover(TermDao termDao) {
        this.termDao = termDao;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeOrphanedRelatedRelationships(Collection<? extends HasIdentifier> orphaned, Term toRemove) {
        final TermInfo tiToRemove = new TermInfo(toRemove);
        orphaned.forEach(o -> {
            final Optional<Term> term = termDao.find(o.getUri());
            assert term.isPresent();
            term.get().getRelated().remove(tiToRemove);
        });
    }
}
