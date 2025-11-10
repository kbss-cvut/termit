package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RdfStatement;
import cz.cvut.kbss.termit.dto.meta.TermRelationshipAnnotation;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.persistence.dao.meta.TermRelationshipAnnotationDao;
import cz.cvut.kbss.termit.service.business.TermService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermRelationshipAnnotationRepositoryServiceTest {

    @Mock
    private TermRelationshipAnnotationDao dao;

    @Mock
    private TermService termService;

    @Mock
    private Validator validator;

    @InjectMocks
    private TermRelationshipAnnotationRepositoryService sut;

    @Test
    void findAllForSubjectFindsTermAndThenRetrievesItsRelationshipAnnotations() {
        final Term term = Generator.generateTermWithId();
        final TermRelationshipAnnotation annotation = new TermRelationshipAnnotation(new RdfStatement(term.getUri(),
                                                                                                      URI.create(
                                                                                                              SKOS.RELATED),
                                                                                                      Generator.generateUri()),
                                                                                     URI.create(
                                                                                             DC.Terms.CREATED),
                                                                                     LocalDate.now());
        when(termService.findRequired(term.getUri())).thenReturn(term);
        when(dao.findAllForSubject(term)).thenReturn(List.of(annotation));

        final List<TermRelationshipAnnotation> result = sut.findAllForSubject(term.getUri());
        assertEquals(List.of(annotation), result);
    }

    @Test
    void updateAnnotationChecksForTermExistenceAndThenUpdatesRelationshipAnnotation() {
        final Term term = Generator.generateTermWithId();
        when(termService.exists(term.getUri())).thenReturn(true);
        final TermRelationshipAnnotation annotation = new TermRelationshipAnnotation(new RdfStatement(term.getUri(),
                                                                                                      URI.create(
                                                                                                              SKOS.RELATED),
                                                                                                      Generator.generateUri()),
                                                                                     URI.create(
                                                                                             DC.Terms.CREATED),
                                                                                     LocalDate.now());

        sut.updateAnnotation(term.getUri(), annotation);
        verify(termService).exists(term.getUri());
        verify(dao).updateTermRelationshipAnnotation(annotation);
    }

    @Test
    void updateAnnotationValidatesSpecifiedAnnotation() {
        final Term term = Generator.generateTermWithId();
        when(termService.exists(term.getUri())).thenReturn(true);
        final TermRelationshipAnnotation annotation = new TermRelationshipAnnotation(new RdfStatement(term.getUri(),
                                                                                                      URI.create(
                                                                                                              SKOS.RELATED),
                                                                                                      Generator.generateUri()),
                                                                                     URI.create(
                                                                                             DC.Terms.CREATED),
                                                                                     LocalDate.now());

        sut.updateAnnotation(term.getUri(), annotation);
        verify(validator).validate(annotation);
    }
}
