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
