package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.UpdateChangeRecordRollbackException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRollbackDao;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.service.security.authorization.TermAuthorizationService;
import cz.cvut.kbss.termit.service.security.authorization.VocabularyAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeRollbackServiceTest {

    @Mock
    private RollbackValidator rollbackValidator;

    @Mock
    private ChangeRollbackDao rollbackDao;

    @Mock
    private TermRepositoryService termService;

    @Mock
    private VocabularyRepositoryService vocabularyService;

    @Mock
    private TermAuthorizationService termAuthorizationService;

    @Mock
    private VocabularyAuthorizationService vocabularyAuthorizationService;

    @InjectMocks
    private ChangeRollbackService sut;

    @Test
    void canRollbackValidatesExistingVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final UpdateChangeRecord record = recordFor(vocabulary);
        when(vocabularyService.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        when(vocabularyAuthorizationService.canModify(vocabulary)).thenReturn(true);
        when(rollbackValidator.canRollback(record, Vocabulary.class)).thenReturn(true);

        assertTrue(sut.canRollback(record));
    }

    @Test
    void canRollbackValidatesExistingTerm() {
        final Term term = Generator.generateTermWithId();
        final UpdateChangeRecord record = recordFor(term);
        when(termService.find(term.getUri())).thenReturn(Optional.of(term));
        when(termAuthorizationService.canModify(term)).thenReturn(true);
        when(rollbackValidator.canRollback(record, Term.class)).thenReturn(true);

        assertTrue(sut.canRollback(record));
    }

    @Test
    void canRollbackReturnsFalseWhenChangedEntityDoesNotExist() {
        final UpdateChangeRecord record = new UpdateChangeRecord();
        record.setChangedEntity(Generator.generateUri());
        when(vocabularyService.find(record.getChangedEntity())).thenReturn(Optional.empty());
        when(termService.find(record.getChangedEntity())).thenReturn(Optional.empty());

        assertFalse(sut.canRollback(record));
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void canRollbackChecksForUserAuthorizationWhenRollbackingTerm(boolean isAuthorized) {
        final Term term = Generator.generateTermWithId();
        final UpdateChangeRecord record = recordFor(term);
        when(termService.find(term.getUri())).thenReturn(Optional.of(term));
        when(termAuthorizationService.canModify(term)).thenReturn(isAuthorized);
        when(rollbackValidator.canRollback(record, Term.class)).thenReturn(true);

        assertEquals(isAuthorized, sut.canRollback(record));
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void canRollbackChecksForUserAuthorizationWhenRollbackingVocabulary(boolean isAuthorized) {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final UpdateChangeRecord record = recordFor(vocabulary);
        when(vocabularyService.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        when(vocabularyAuthorizationService.canModify(vocabulary)).thenReturn(isAuthorized);
        when(rollbackValidator.canRollback(record, Vocabulary.class)).thenReturn(true);

        assertEquals(isAuthorized, sut.canRollback(record));
    }

    @Test
    void rollbackThrowsWhenRecordCannotBeRolledBack() {
        final Term term = Generator.generateTermWithId();
        final UpdateChangeRecord record = recordFor(term);
        when(vocabularyService.find(term.getUri())).thenReturn(Optional.empty());
        when(termService.find(term.getUri())).thenReturn(Optional.of(term));
        when(rollbackValidator.canRollback(record, Term.class)).thenReturn(false);

        assertThrows(UpdateChangeRecordRollbackException.class, () -> sut.rollback(record));
    }

    @Test
    void rollbackThrowsWhenUserIsNotAuthorizedToModifyTerm() {
        final Term term = Generator.generateTermWithId();
        final UpdateChangeRecord record = recordFor(term);
        when(vocabularyService.find(term.getUri())).thenReturn(Optional.empty());
        when(termService.find(term.getUri())).thenReturn(Optional.of(term));
        when(rollbackValidator.canRollback(record, Term.class)).thenReturn(true);
        when(termAuthorizationService.canModify(term)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> sut.rollback(record));
    }

    @Test
    void rollbackThrowsWhenUserIsNotAuthorizedToModifyVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final UpdateChangeRecord record = recordFor(vocabulary);
        when(vocabularyService.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        when(rollbackValidator.canRollback(record, Vocabulary.class)).thenReturn(true);
        when(vocabularyAuthorizationService.canModify(vocabulary)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> sut.rollback(record));
    }

    private static final String WITH_REVERSIBLE_CSV_SOURCE = """
            # can be rolled back and user is authorized -> is reversible
            true, true, true
            # can not be rolled back, user is authorized -> is not reversible
            false, true, false
            # can be rolled back, user is not authorized -> is not reversible
            true, false, false
            # can not be rolled back, user is not authorized -> is not reversible
            false, false, false
            """;

    @ParameterizedTest
    @CsvSource(textBlock = WITH_REVERSIBLE_CSV_SOURCE)
    void withReversibleTypeAddsReversibleTypeToReversibleVocabularyChange(boolean canRollback, boolean canModify, boolean expectedIsReversible) {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final UpdateChangeRecord record = recordFor(vocabulary);

        when (vocabularyService.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        lenient().when(rollbackValidator.canRollback(record, Vocabulary.class)).thenReturn(canRollback);
        when(vocabularyAuthorizationService.canModify(vocabulary)).thenReturn(canModify);

        assertFalse(record.getTypes().contains(UpdateChangeRecord.REVERSIBLE_CHANGE_CLASS));
        sut.withReversibleType(List.of(record));
        final boolean isReversibleResult = record.getTypes().contains(UpdateChangeRecord.REVERSIBLE_CHANGE_CLASS);
        assertEquals(expectedIsReversible, isReversibleResult);

        if (canModify) {
            verify(rollbackValidator).canRollback(record, Vocabulary.class);
        }
        verify(vocabularyAuthorizationService).canModify(vocabulary);
        verifyNoMoreInteractions(rollbackValidator);
        verifyNoMoreInteractions(vocabularyAuthorizationService);
    }

    @ParameterizedTest
    @CsvSource(textBlock = WITH_REVERSIBLE_CSV_SOURCE)
    void withReversibleTypeAddsReversibleTypeToReversibleTermChange(boolean canRollback, boolean canModify, boolean expectedIsReversible) {
        final Term term = Generator.generateTermWithId();
        final UpdateChangeRecord record = recordFor(term);

        when (termService.find(term.getUri())).thenReturn(Optional.of(term));
        lenient().when(rollbackValidator.canRollback(record, Term.class)).thenReturn(canRollback);
        when(termAuthorizationService.canModify(term)).thenReturn(canModify);

        assertFalse(record.getTypes().contains(UpdateChangeRecord.REVERSIBLE_CHANGE_CLASS));
        sut.withReversibleType(List.of(record));
        final boolean isReversibleResult = record.getTypes().contains(UpdateChangeRecord.REVERSIBLE_CHANGE_CLASS);
        assertEquals(expectedIsReversible, isReversibleResult);

        if (canModify) {
            verify(rollbackValidator).canRollback(record, Term.class);
        }
        verify(termAuthorizationService).canModify(term);
        verifyNoMoreInteractions(rollbackValidator);
        verifyNoMoreInteractions(termAuthorizationService);
    }

    private static UpdateChangeRecord recordFor(Asset<?> asset) {
        final UpdateChangeRecord record = new UpdateChangeRecord(asset);
        record.setChangedAttribute(Generator.generateUri());
        record.setOriginalValue(Set.of("original"));
        record.setNewValue(Set.of("updated"));
        record.setTypes(new HashSet<>());
        return record;
    }
}
