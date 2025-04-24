/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.assignment.TermOccurrences;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.persistence.dao.ResourceDao;
import cz.cvut.kbss.termit.persistence.dao.TermDao;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import cz.cvut.kbss.termit.service.business.TermOccurrenceService;
import cz.cvut.kbss.termit.service.document.TermOccurrenceSelectorCreator;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static cz.cvut.kbss.termit.util.Constants.SCHEDULING_PATTERN;

@Service
public class TermOccurrenceRepositoryService implements TermOccurrenceService {

    private static final Logger LOG = LoggerFactory.getLogger(TermOccurrenceRepositoryService.class);

    private final TermOccurrenceDao termOccurrenceDao;

    private final TermDao termDao;

    private final ResourceDao resourceDao;

    private final TermOccurrenceSelectorCreator selectorCreator;

    @Autowired
    public TermOccurrenceRepositoryService(TermOccurrenceDao termOccurrenceDao, TermDao termDao,
                                           ResourceDao resourceDao, TermOccurrenceSelectorCreator selectorCreator) {
        this.termOccurrenceDao = termOccurrenceDao;
        this.termDao = termDao;
        this.resourceDao = resourceDao;
        this.selectorCreator = selectorCreator;
    }

    @PreAuthorize("@termOccurrenceAuthorizationService.canModify(#occurrence)")
    @Transactional
    @Override
    public void persist(TermOccurrence occurrence) {
        Objects.requireNonNull(occurrence);
        checkTermExists(occurrence);
        if (!termDao.exists(occurrence.getTarget().getSource()) && !resourceDao.exists(
                occurrence.getTarget().getSource())) {
            throw new ValidationException(
                    "Occurrence references an unknown asset " + Utils.uriToString(occurrence.getTarget().getSource()));
        }
        if (occurrence.getElementAbout() != null) {
            LOG.trace("Generating selectors for new term occurrence with ID '{}'.", occurrence.getUri());
            occurrence.getTarget()
                      .setSelectors(
                              selectorCreator.createSelectors(occurrence.getTarget(), occurrence.getElementAbout()));
        }
        termOccurrenceDao.persist(occurrence);
    }

    private void checkTermExists(TermOccurrence occurrence) {
        if (!termDao.exists(occurrence.getTerm())) {
            throw new ValidationException(
                    "Occurrence references an unknown term " + Utils.uriToString(occurrence.getTerm()));
        }
    }

    @PreAuthorize("@termOccurrenceAuthorizationService.canModify(#occurrence)")
    @Transactional
    @Override
    public void persistOrUpdate(TermOccurrence occurrence) {
        Objects.requireNonNull(occurrence);
        if (termOccurrenceDao.exists(occurrence.getUri())) {
            final Optional<TermOccurrence> existingWrapped = termOccurrenceDao.find(occurrence.getUri());
            assert existingWrapped.isPresent();
            final TermOccurrence existing = existingWrapped.get();
            termOccurrenceDao.detach(existing);
            checkTermExists(occurrence);
            existing.setTerm(occurrence.getTerm());
            termOccurrenceDao.update(existing);
        } else {
            persist(occurrence);
        }
    }

    @PreAuthorize("@termOccurrenceAuthorizationService.canModify(#occurrenceId)")
    @Async
    // Retry in case the occurrence has not been persisted, yet (see AsynchronousTermOccurrenceSaver)
    @Retryable(retryFor = NotFoundException.class, maxAttempts = 3, backoff = @Backoff(delay = 30000L))
    @Transactional
    @Override
    public void approve(URI occurrenceId) {
        Objects.requireNonNull(occurrenceId);
        final TermOccurrence toApprove = termOccurrenceDao.find(occurrenceId).orElseThrow(
                () -> NotFoundException.create(TermOccurrence.class, occurrenceId));
        LOG.trace("Approving term occurrence {}", toApprove);
        toApprove.markApproved();
    }

    @PreAuthorize("@termOccurrenceAuthorizationService.canModify(#occurrenceId)")
    @Transactional
    @Override
    public void remove(URI occurrenceId) {
        Objects.requireNonNull(occurrenceId);
        LOG.trace("Removing term occurrence {}.", occurrenceId);
        termOccurrenceDao.find(occurrenceId).ifPresent(termOccurrenceDao::remove);
    }

    /**
     * Cleans up possibly orphaned term occurrences.
     * <p>
     * Such occurrences reference targets whose sources no longer exist in the repository.
     */
    @Scheduled(cron = SCHEDULING_PATTERN)
    @Transactional
    public void cleanupOrphans() {
        LOG.debug("Executing orphaned term occurrences cleanup.");
        termOccurrenceDao.removeAllOrphans();
    }

    @PreAuthorize("@termAuthorizationService.canRead(#term)")
    @Transactional(readOnly = true)
    @Override
    public List<TermOccurrences> getOccurrenceInfo(AbstractTerm term) {
        return termOccurrenceDao.getOccurrenceInfo(term);
    }

    @PreAuthorize("@termAuthorizationService.canRead(#term)")
    @Transactional(readOnly = true)
    @Override
    public List<TermOccurrence> findAllDefinitionalOf(AbstractTerm term) {
        return termOccurrenceDao.findAllDefinitionalOf(term);
    }

    @Transactional(readOnly = true)
    @Override
    public List<TermOccurrence> findAllTargeting(Asset<?> target) {
        return termOccurrenceDao.findAllTargeting(target);
    }

    @Async
    @Transactional
    @Override
    public void removeAllOf(AbstractTerm term) {
        LOG.debug("Removing all occurrences of term {}.", term);
        termOccurrenceDao.removeAllOf(term);
    }
}
