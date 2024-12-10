package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.DefinitionalOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionalOccurrence;
import cz.cvut.kbss.termit.model.assignment.TermFileOccurrence;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.dao.TermOccurrenceDao;
import cz.cvut.kbss.termit.service.repository.ResourceRepositoryService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermOccurrenceAuthorizationServiceTest {

    @Mock
    private TermOccurrenceDao toDao;

    @Mock
    private TermRepositoryService termService;

    @Mock
    private ResourceRepositoryService resourceService;

    @Mock
    private VocabularyAuthorizationService vocabularyAuthorizationService;

    @Mock
    private ResourceAuthorizationService resourceAuthorizationService;

    @InjectMocks
    private TermOccurrenceAuthorizationService sut;

    @Test
    void canModifyResolvesTermVocabularyAndChecksIfUserCanModifyItWhenTermOccurrenceIsDefinitional() {
        final URI vocabularyUri = Generator.generateUri();
        final TermOccurrence to = new TermDefinitionalOccurrence(Generator.generateUri(),
                                                                 new DefinitionalOccurrenceTarget(
                                                                         Generator.generateTermWithId(vocabularyUri)));
        to.setUri(Generator.generateUri());
        when(termService.findTermVocabulary(to.getTarget().getSource())).thenReturn(Optional.of(vocabularyUri));
        when(vocabularyAuthorizationService.canModify(new Vocabulary(vocabularyUri))).thenReturn(true);
        when(toDao.find(to.getUri())).thenReturn(Optional.of(to));

        assertTrue(sut.canModify(to.getUri()));
        verify(vocabularyAuthorizationService).canModify(new Vocabulary(vocabularyUri));
    }

    @Test
    void canModifyResolvesResourceVocabularyAndChecksIfUserCanModifyItWhenTermOccurrenceIsFileOccurrence() {
        final URI vocabularyUri = Generator.generateUri();
        final File file = Generator.generateFileWithId("test.html");
        file.setDocument(Generator.generateDocumentWithId());
        file.getDocument().setVocabulary(vocabularyUri);
        final TermOccurrence to = new TermFileOccurrence(Generator.generateUri(), new FileOccurrenceTarget(file));
        to.setUri(Generator.generateUri());
        when(resourceService.find(file.getUri())).thenReturn(Optional.of(file));
        when(resourceAuthorizationService.canModify(file)).thenReturn(true);
        when(toDao.find(to.getUri())).thenReturn(Optional.of(to));

        assertTrue(sut.canModify(to.getUri()));
        verify(resourceAuthorizationService).canModify(file);
    }

    @Test
    void canModifyReturnsTrueWhenTermOccurrenceDoesNotExist() {
        when(toDao.find(any())).thenReturn(Optional.empty());
        assertTrue(sut.canModify(Generator.generateUri()));
    }
}
