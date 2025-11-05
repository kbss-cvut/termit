package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.meta.TermRelationshipAnnotation;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.persistence.dao.meta.TermRelationshipAnnotationDao;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.business.readonly.TermRelationshipAnnotationService;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Service
public class TermRelationshipAnnotationRepositoryService implements TermRelationshipAnnotationService {

    private final TermService termService;

    private final TermRelationshipAnnotationDao dao;

    public TermRelationshipAnnotationRepositoryService(TermService termService, TermRelationshipAnnotationDao dao) {
        this.termService = termService;
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    @Override
    public @NotNull List<TermRelationshipAnnotation> findAllForSubject(@NotNull URI termId) {
        Objects.requireNonNull(termId);
        final Term t = termService.findRequired(termId);
        return dao.findAllForSubject(t);
    }

    @Transactional
    @Override
    public void updateAnnotation(@NotNull URI termId, @NotNull TermRelationshipAnnotation annotation) {
        // TODO
    }
}
