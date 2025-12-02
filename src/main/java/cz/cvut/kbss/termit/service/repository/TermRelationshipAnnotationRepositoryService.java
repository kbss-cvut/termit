package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.meta.AnnotatedTermRelationship;
import cz.cvut.kbss.termit.dto.meta.TermRelationshipAnnotation;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.persistence.dao.meta.TermRelationshipAnnotationDao;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.business.readonly.TermRelationshipAnnotationService;
import jakarta.validation.Validator;
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

    private final Validator validator;

    public TermRelationshipAnnotationRepositoryService(TermService termService, TermRelationshipAnnotationDao dao,
                                                       Validator validator) {
        this.termService = termService;
        this.dao = dao;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    @Override
    public @NotNull List<TermRelationshipAnnotation> findAllForSubject(@NotNull URI termId) {
        Objects.requireNonNull(termId);
        final Term t = termService.findRequired(termId);
        return dao.findAllForSubject(t);
    }

    @Transactional(readOnly = true)
    @Override
    public @NotNull List<AnnotatedTermRelationship> findAnnotatedRelationships(@NotNull URI termId) {
        Objects.requireNonNull(termId);
        final Term t = termService.findRequired(termId);
        return dao.getRelationshipsAnnotatedByTerm(t);
    }

    @Transactional
    @Override
    public void updateAnnotation(@NotNull URI termId, @NotNull TermRelationshipAnnotation annotation) {
        if (!termService.exists(termId)) {
            throw NotFoundException.create(Term.class, termId);
        }
        BaseRepositoryService.validate(annotation, validator);
        dao.updateTermRelationshipAnnotation(annotation);
    }
}
