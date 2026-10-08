package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.dto.IriMigrationParams;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.service.repository.migration.IriMigrationRepositoryService;
import cz.cvut.kbss.termit.service.repository.migration.IriMigrationType;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.service.security.authorization.TermAuthorizationService;
import cz.cvut.kbss.termit.service.security.authorization.VocabularyAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IriMigrationServiceTest {

    @Mock
    private IriMigrationRepositoryService repositoryService;

    @Mock
    private VocabularyRepositoryService vocabularyRepositoryService;

    @Mock
    private TermRepositoryService termRepositoryService;

    @Mock
    private VocabularyAuthorizationService vocabularyAuthorizationService;

    @Mock
    private TermAuthorizationService termAuthorizationService;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private IriMigrationService sut;

    private final IriMigrationParams params = new IriMigrationParams(null);

    @Test
    void migrateIdentifierMigratesVocabularyIdentifierWhenUserIsAuthorizedToMigrateIt() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final IriMigrationPair iris = new IriMigrationPair(vocabulary.getUri(), Generator.generateUri());
        when(vocabularyRepositoryService.findRequired(vocabulary.getUri())).thenReturn(vocabulary);
        when(vocabularyAuthorizationService.canMigrateIdentifier(vocabulary)).thenReturn(true);
        final UserAccount user = Generator.generateUserAccount();
        when(securityUtils.getCurrentUser()).thenReturn(user);

        sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params);
        verify(vocabularyAuthorizationService).canMigrateIdentifier(vocabulary);
        verify(repositoryService)
                .migrateIdentifier(eq(iris), eq(IriMigrationType.VOCABULARY), eq(params), eq(user.toUser()), any());
    }

    @Test
    void migrateIdentifierThrowsAuthorizationExceptionWhenUserIsNotAuthorizedToMigrateVocabularyIdentifier() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final IriMigrationPair iris = new IriMigrationPair(vocabulary.getUri(), Generator.generateUri());
        when(vocabularyRepositoryService.findRequired(vocabulary.getUri())).thenReturn(vocabulary);
        when(vocabularyAuthorizationService.canMigrateIdentifier(vocabulary)).thenReturn(false);

        assertThrows(
                AuthorizationException.class, () -> sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params));
        verify(vocabularyAuthorizationService).canMigrateIdentifier(vocabulary);
        verify(repositoryService, never()).migrateIdentifier(any(), any(), any(), any(), any());
    }

    @Test
    void migrateIdentifierThrowsNotFoundExceptionWhenMigratedVocabularyDoesNotExist() {
        final IriMigrationPair iris = new IriMigrationPair(Generator.generateUri(), Generator.generateUri());
        when(vocabularyRepositoryService.findRequired(iris.originalIri()))
                .thenThrow(NotFoundException.create(Vocabulary.class, iris.originalIri()));

        assertThrows(NotFoundException.class, () -> sut.migrateIdentifier(iris, IriMigrationType.VOCABULARY, params));
        verify(vocabularyAuthorizationService, never()).canMigrateIdentifier(any());
        verify(repositoryService, never()).migrateIdentifier(any(), any(), any(), any(), any());
    }

    @Test
    void migrateIdentifierMigratesTermIdentifierWhenUserIsAuthorizedToMigrateIt() {
        final Term term = Generator.generateTermWithId(Generator.generateUri());
        final IriMigrationPair iris = new IriMigrationPair(term.getUri(), Generator.generateUri());
        when(termRepositoryService.findRequired(term.getUri())).thenReturn(term);
        when(termAuthorizationService.canMigrateIdentifier(term)).thenReturn(true);
        final UserAccount user = Generator.generateUserAccount();
        when(securityUtils.getCurrentUser()).thenReturn(user);

        sut.migrateIdentifier(iris, IriMigrationType.TERM, params);
        verify(termAuthorizationService).canMigrateIdentifier(term);
        verify(repositoryService)
                .migrateIdentifier(eq(iris), eq(IriMigrationType.TERM), eq(params), eq(user.toUser()), any());
    }

    @Test
    void migrateIdentifierThrowsAuthorizationExceptionWhenUserIsNotAuthorizedToMigrateTermIdentifier() {
        final Term term = Generator.generateTermWithId(Generator.generateUri());
        final IriMigrationPair iris = new IriMigrationPair(term.getUri(), Generator.generateUri());
        when(termRepositoryService.findRequired(term.getUri())).thenReturn(term);
        when(termAuthorizationService.canMigrateIdentifier(term)).thenReturn(false);

        assertThrows(AuthorizationException.class, () -> sut.migrateIdentifier(iris, IriMigrationType.TERM, params));
        verify(termAuthorizationService).canMigrateIdentifier(term);
        verify(repositoryService, never()).migrateIdentifier(any(), any(), any(), any(), any());
    }

    @Test
    void migrateIdentifierThrowsNotFoundExceptionWhenMigratedTermDoesNotExist() {
        final IriMigrationPair iris = new IriMigrationPair(Generator.generateUri(), Generator.generateUri());
        when(termRepositoryService.findRequired(iris.originalIri()))
                .thenThrow(NotFoundException.create(Term.class, iris.originalIri()));

        assertThrows(NotFoundException.class, () -> sut.migrateIdentifier(iris, IriMigrationType.TERM, params));
        verify(termAuthorizationService, never()).canMigrateIdentifier(any());
        verify(repositoryService, never()).migrateIdentifier(any(), any(), any(), any(), any());
    }

    @Test
    void migrateIdentifierMigratesCustomAttributeIdentifierWhenCurrentUserIsAdmin() {
        final UserAccount user = Generator.generateUserAccount();
        user.addType(UserRole.ADMIN.getType());
        final IriMigrationPair iris = new IriMigrationPair(Generator.generateUri(), Generator.generateUri());
        when(securityUtils.getCurrentUser()).thenReturn(user);

        sut.migrateIdentifier(iris, IriMigrationType.CUSTOM_ATTRIBUTE, params);
        verify(repositoryService)
                .migrateIdentifier(
                        eq(iris), eq(IriMigrationType.CUSTOM_ATTRIBUTE), eq(params), eq(user.toUser()), any());
        verifyNoInteractions(vocabularyAuthorizationService, termAuthorizationService);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void migrateIdentifierThrowsAuthorizationExceptionWhenMigratingCustomAttributeIdentifierAndCurrentUserIsNotAdmin(
            UserRole role) {
        final UserAccount user = Generator.generateUserAccount();
        user.addType(role.getType());
        final IriMigrationPair iris = new IriMigrationPair(Generator.generateUri(), Generator.generateUri());
        when(securityUtils.getCurrentUser()).thenReturn(user);

        assertThrows(
                AuthorizationException.class,
                () -> sut.migrateIdentifier(iris, IriMigrationType.CUSTOM_ATTRIBUTE, params));
        verify(repositoryService, never()).migrateIdentifier(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = IriMigrationType.class, names = "VOCABULARY", mode = EnumSource.Mode.EXCLUDE)
    void migrateIdentifierThrowsWhenNewNamespaceIsProvidedForNonVocabularyMigration(IriMigrationType migrationType) {
        final IriMigrationPair iris = new IriMigrationPair(Generator.generateUri(), Generator.generateUri());
        final IriMigrationParams params = new IriMigrationParams(Generator.generateUri());

        assertThrows(InvalidParameterException.class, () -> sut.migrateIdentifier(iris, migrationType, params));
    }
}
