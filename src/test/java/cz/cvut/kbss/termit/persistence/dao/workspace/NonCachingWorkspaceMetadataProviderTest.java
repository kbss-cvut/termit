package cz.cvut.kbss.termit.persistence.dao.workspace;

import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Optional;

import static cz.cvut.kbss.termit.environment.WorkspaceGenerator.generateWorkspace;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NonCachingWorkspaceMetadataProviderTest {

    @Mock
    private WorkspaceDao workspaceDao;

    @InjectMocks
    private NonCachingWorkspaceMetadataProvider sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void getWorkspaceRetrievesWorkspaceFromDao() {
        final Workspace ws = generateWorkspace();
        when(workspaceDao.find(ws.getUri())).thenReturn(Optional.of(ws));

        final Workspace result = sut.getWorkspace(ws.getUri());
        assertEquals(ws, result);
        verify(workspaceDao).find(ws.getUri());
    }

    @Test
    void getWorkspaceThrowsNotFoundExceptionWhenWorkspaceDoesNotExist() {
        when(workspaceDao.find(any())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> sut.getWorkspace(Generator.generateUri()));
    }

    @Test
    void getWorkspaceMetadataRetrievesMetadataFromDao() {
        final Workspace ws = generateWorkspace();
        when(workspaceDao.find(ws.getUri())).thenReturn(Optional.of(ws));
        final VocabularyInfo vInfo = new VocabularyInfo(Generator.generateUri(), Generator.generateUri(),
                Generator.generateUri());
        when(workspaceDao.findWorkspaceVocabularyMetadata(ws)).thenReturn(Collections.singletonList(vInfo));

        final WorkspaceMetadata result = sut.getWorkspaceMetadata(ws.getUri());
        assertNotNull(result);
        assertEquals(ws, result.getWorkspace());
        assertThat(result.getVocabularies().values(), hasItem(vInfo));
        verify(workspaceDao).findWorkspaceVocabularyMetadata(ws);
    }
}
