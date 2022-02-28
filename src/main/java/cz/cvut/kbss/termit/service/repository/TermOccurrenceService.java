/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import static cz.cvut.kbss.termit.util.Constants.SCHEDULING_PATTERN;

@Service
public class TermOccurrenceService {

    private static final Logger LOG = LoggerFactory.getLogger(TermOccurrenceService.class);

    private final TermOccurrenceDao termOccurrenceDao;

    @Autowired
    public TermOccurrenceService(TermOccurrenceDao termOccurrenceDao) {
        this.termOccurrenceDao = termOccurrenceDao;
    }

    @Transactional
    public void persistOccurrence(TermOccurrence occurrence) {
        Objects.requireNonNull(occurrence);
        termOccurrenceDao.persist(occurrence);
    }

    @Transactional
    public void approveOccurrence(URI identifier) {
        Objects.requireNonNull(identifier);
        LOG.trace("approve term occurrence with identifier {}", identifier);
        Optional<TermOccurrence> occurrence = termOccurrenceDao.find(identifier);
        occurrence.ifPresent(o -> {
            o.removeType(cz.cvut.kbss.termit.util.Vocabulary.s_c_navrzeny_vyskyt_termu);
            termOccurrenceDao.update(o);
        });
    }

    @Transactional
    public void removeOccurrence(TermOccurrence occurrence) {
        Objects.requireNonNull(occurrence);
        LOG.trace("remove term occurrence with identifier {}", occurrence);
        termOccurrenceDao.remove(occurrence);
    }

    public TermOccurrence getRequiredReference(URI id) {
        return termOccurrenceDao.getReference(id).orElseThrow(() ->
                NotFoundException.create(TermOccurrence.class.getSimpleName(), id)
        );
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
