/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionalOccurrence;
import cz.cvut.kbss.termit.model.assignment.TermFileOccurrence;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import cz.cvut.kbss.termit.service.repository.ResourceRepositoryService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

@Service
public class TermOccurrenceAuthorizationService {

    private final TermOccurrenceDao dao;

    private final TermRepositoryService termService;

    private final ResourceRepositoryService resourceService;

    private final VocabularyAuthorizationService vocabularyAuthorizationService;

    private final ResourceAuthorizationService resourceAuthorizationService;

    public TermOccurrenceAuthorizationService(TermOccurrenceDao dao, TermRepositoryService termService,
                                              ResourceRepositoryService resourceService,
                                              VocabularyAuthorizationService vocabularyAuthorizationService,
                                              ResourceAuthorizationService resourceAuthorizationService) {
        this.dao = dao;
        this.termService = termService;
        this.resourceService = resourceService;
        this.vocabularyAuthorizationService = vocabularyAuthorizationService;
        this.resourceAuthorizationService = resourceAuthorizationService;
    }

    @Transactional(readOnly = true)
    public boolean canModify(TermOccurrence occurrence) {
        Objects.requireNonNull(occurrence);
        if (occurrence instanceof TermDefinitionalOccurrence definitionalOccurrence) {
            final Optional<URI> vocabularyUri = termService.findTermVocabulary(
                    definitionalOccurrence.getTarget().getSource());
            return vocabularyUri.map(vUri -> vocabularyAuthorizationService.canModify(new Vocabulary(vUri)))
                                .orElse(false);
        } else {
            final TermFileOccurrence fo = (TermFileOccurrence) occurrence;
            final Optional<Resource> file = resourceService.find(fo.getTarget().getSource());
            return file.map(resourceAuthorizationService::canModify).orElse(false);
        }
    }

    @Transactional(readOnly = true)
    public boolean canModify(URI occurrenceId) {
        return dao.find(occurrenceId).map(this::canModify).orElse(true);
    }
}
