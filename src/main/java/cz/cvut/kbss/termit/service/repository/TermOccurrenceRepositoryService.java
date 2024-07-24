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

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import cz.cvut.kbss.termit.service.business.TermOccurrenceService;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import static cz.cvut.kbss.termit.util.Constants.SCHEDULING_PATTERN;

@Service
public class TermOccurrenceRepositoryService implements TermOccurrenceService {

    private static final Logger LOG = LoggerFactory.getLogger(TermOccurrenceRepositoryService.class);

    private final TermOccurrenceDao termOccurrenceDao;

    private final TermRepositoryService termService;

    private final ResourceRepositoryService resourceService;

    @Autowired
    public TermOccurrenceRepositoryService(TermOccurrenceDao termOccurrenceDao, TermRepositoryService termService,
                                           ResourceRepositoryService resourceService) {
        this.termOccurrenceDao = termOccurrenceDao;
        this.termService = termService;
        this.resourceService = resourceService;
    }

    @Transactional
    @Override
    public void persist(TermOccurrence occurrence) {
        Objects.requireNonNull(occurrence);
        checkTermExists(occurrence);
        if (!termService.exists(occurrence.getTarget().getSource()) && !resourceService.exists(
                occurrence.getTarget().getSource())) {
            throw new ValidationException(
                    "Occurrence references an unknown asset " + Utils.uriToString(occurrence.getTarget().getSource()));
        }
        termOccurrenceDao.persist(occurrence);
    }

    private void checkTermExists(TermOccurrence occurrence) {
        if (!termService.exists(occurrence.getTerm())) {
            throw new ValidationException(
                    "Occurrence references an unknown term " + Utils.uriToString(occurrence.getTerm()));
        }
    }

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
}
