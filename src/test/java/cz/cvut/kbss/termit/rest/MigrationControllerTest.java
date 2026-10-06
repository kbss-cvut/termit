package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.dto.IriMigrationPair;
import cz.cvut.kbss.termit.dto.IriMigrationParams;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.business.IriMigrationService;
import cz.cvut.kbss.termit.service.repository.migration.IriMigrationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MigrationControllerTest extends BaseControllerTestRunner {

    private static final String PATH = MigrationController.PATH + "/identifier";

    @Mock
    private IriMigrationService iriMigrationService;

    @InjectMocks
    private MigrationController sut;

    private URI originalIri;

    private URI newIri;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
        this.originalIri = Generator.generateUri();
        this.newIri = Generator.generateUri();
    }

    @ParameterizedTest
    @EnumSource(IriMigrationType.class)
    void migrateIdentifierPassesIrisAndMigrationTypeToService(IriMigrationType migrationType) throws Exception {
        mockMvc.perform(post(PATH)
                        .param("originalIri", originalIri.toString())
                        .param("newIri", newIri.toString())
                        .param("type", migrationType.name()))
                .andExpect(status().isAccepted());
        verify(iriMigrationService)
                .migrateIdentifier(
                        new IriMigrationPair(originalIri, newIri), migrationType, new IriMigrationParams(null));
    }

    @Test
    void migrateIdentifierPassesPreferredNamespaceToServiceWhenItIsSpecified() throws Exception {
        final URI preferredNamespace = URI.create(newIri + "/pojem/");
        mockMvc.perform(post(PATH)
                        .param("originalIri", originalIri.toString())
                        .param("newIri", newIri.toString())
                        .param("type", IriMigrationType.VOCABULARY.name())
                        .param("preferredNamespace", preferredNamespace.toString()))
                .andExpect(status().isAccepted());
        verify(iriMigrationService)
                .migrateIdentifier(
                        new IriMigrationPair(originalIri, newIri),
                        IriMigrationType.VOCABULARY,
                        new IriMigrationParams(preferredNamespace));
    }

    @ParameterizedTest
    @EnumSource(value = IriMigrationType.class, names = "VOCABULARY", mode = EnumSource.Mode.EXCLUDE)
    void migrateIdentifierThrowsUnprocessableContentWhenNamespaceIsSpecifiedForNonVocabularyMigrationType(
            IriMigrationType type) throws Exception {
        final URI preferredNamespace = URI.create(newIri + "/pojem/");
        mockMvc.perform(post(PATH)
                        .param("originalIri", originalIri.toString())
                        .param("newIri", newIri.toString())
                        .param("type", type.name())
                        .param("preferredNamespace", preferredNamespace.toString()))
                .andExpect(status().isUnprocessableContent());
        verify(iriMigrationService, never()).migrateIdentifier(any(), any(), any());
    }

    @Test
    void migrateIdentifierThrowsBadRequestWhenOriginalIriIsMissing() throws Exception {
        mockMvc.perform(post(PATH).param("newIri", newIri.toString()).param("type", IriMigrationType.TERM.name()))
                .andExpect(status().isBadRequest());
        verify(iriMigrationService, never()).migrateIdentifier(any(), any(), any());
    }

    @Test
    void migrateIdentifierThrowsBadRequestWhenNewIriIsMissing() throws Exception {
        mockMvc.perform(post(PATH)
                        .param("originalIri", originalIri.toString())
                        .param("type", IriMigrationType.TERM.name()))
                .andExpect(status().isBadRequest());
        verify(iriMigrationService, never()).migrateIdentifier(any(), any(), any());
    }

    @Test
    void migrateIdentifierThrowsBadRequestWhenMigrationTypeIsMissing() throws Exception {
        mockMvc.perform(post(PATH).param("originalIri", originalIri.toString()).param("newIri", newIri.toString()))
                .andExpect(status().isBadRequest());
        verify(iriMigrationService, never()).migrateIdentifier(any(), any(), any());
    }

    @Test
    void migrateIdentifierThrowsBadRequestWhenMigrationTypeIsInvalid() throws Exception {
        mockMvc.perform(post(PATH)
                        .param("originalIri", originalIri.toString())
                        .param("newIri", newIri.toString())
                        .param("type", "INVALID"))
                .andExpect(status().isBadRequest());
        verify(iriMigrationService, never()).migrateIdentifier(any(), any(), any());
    }

    @Test
    void migrateIdentifierThrowsUnprocessableEntityWhenOriginalAndNewIriAreTheSame() throws Exception {
        mockMvc.perform(post(PATH)
                        .param("originalIri", originalIri.toString())
                        .param("newIri", originalIri.toString())
                        .param("type", IriMigrationType.TERM.name()))
                .andExpect(status().is(HttpStatus.UNPROCESSABLE_CONTENT.value()));
        verify(iriMigrationService, never()).migrateIdentifier(any(), any(), any());
    }

    @Test
    void migrateIdentifierThrowsNotFoundExceptionWhenAssetWithOriginalIriDoesNotExist() throws Exception {
        final IriMigrationPair iris = new IriMigrationPair(originalIri, newIri);
        final IriMigrationParams params = new IriMigrationParams(null);
        doThrow(NotFoundException.create(Vocabulary.class, originalIri))
                .when(iriMigrationService)
                .migrateIdentifier(iris, IriMigrationType.VOCABULARY, params);

        mockMvc.perform(post(PATH)
                        .param("originalIri", originalIri.toString())
                        .param("newIri", newIri.toString())
                        .param("type", IriMigrationType.VOCABULARY.name()))
                .andExpect(status().isNotFound());
        verify(iriMigrationService).migrateIdentifier(iris, IriMigrationType.VOCABULARY, params);
    }

    @Test
    void migrateIdentifierThrowsForbiddenWhenUserIsNotAuthorizedToModifyMigratedAsset() throws Exception {
        final IriMigrationPair iris = new IriMigrationPair(originalIri, newIri);
        final IriMigrationParams params = new IriMigrationParams(null);
        doThrow(new AuthorizationException("User is not authorized to migrate identifier " + originalIri + "."))
                .when(iriMigrationService)
                .migrateIdentifier(iris, IriMigrationType.VOCABULARY, params);

        mockMvc.perform(post(PATH)
                        .param("originalIri", originalIri.toString())
                        .param("newIri", newIri.toString())
                        .param("type", IriMigrationType.VOCABULARY.name()))
                .andExpect(status().isForbidden());
        verify(iriMigrationService).migrateIdentifier(iris, IriMigrationType.VOCABULARY, params);
    }
}
