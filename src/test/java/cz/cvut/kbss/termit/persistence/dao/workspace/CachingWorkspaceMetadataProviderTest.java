package cz.cvut.kbss.termit.persistence.dao.workspace;

import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.workspace.WorkspaceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static cz.cvut.kbss.termit.environment.WorkspaceGenerator.generateWorkspace;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CachingWorkspaceMetadataProviderTest {

    @Mock
    private WorkspaceStore workspaceStore;

    @Mock
    private WorkspaceDao workspaceDao;

    @InjectMocks
    private CachingWorkspaceMetadataProvider sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }


    @Test
    void getWorkspaceRetrievesWorkspaceFromCache() {
        final Workspace ws = generateWorkspace();
        final WorkspaceMetadata metadata = new WorkspaceMetadata(ws);
        sut.initWorkspaceMetadata(metadata);

        final Workspace result = sut.getWorkspace(ws.getUri());
        assertEquals(ws, result);
        verify(workspaceDao, never()).find(any());
    }

    @Test
    void getWorkspaceRetrievesWorkspaceFromDaoWhenItIsNotCached() {
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
    void getWorkspaceMetadataRetrievesWorkspaceMetadataFromCache() {
        final Workspace ws = generateWorkspace();
        final WorkspaceMetadata metadata = new WorkspaceMetadata(ws);
        sut.initWorkspaceMetadata(metadata);

        final WorkspaceMetadata result = sut.getWorkspaceMetadata(ws.getUri());
        assertEquals(metadata, result);
        verify(workspaceDao, never()).find(any());
        verify(workspaceDao, never()).findWorkspaceVocabularyMetadata(any());
    }

    @Test
    void getWorkspaceMetadataLoadsDataFromDaoWhenTheAreNotCached() {
        final Workspace ws = generateWorkspace();
        when(workspaceDao.find(ws.getUri())).thenReturn(Optional.of(ws));
        final List<VocabularyInfo> metadata = initVocabularyInfo(ws);

        final WorkspaceMetadata result = sut.getWorkspaceMetadata(ws.getUri());
        assertThat(result.getVocabularies().values(), hasItems(metadata.toArray(new VocabularyInfo[]{})));
        verify(workspaceDao).findWorkspaceVocabularyMetadata(ws);
    }

    @Test
    void getCurrentWorkspaceRetrievesWorkspaceFromCacheBasedOnCurrentWorkspaceIdentifier() {
        final Workspace ws = generateWorkspace();
        final WorkspaceMetadata metadata = new WorkspaceMetadata(ws);
        when(workspaceStore.getCurrentWorkspace()).thenReturn(ws.getUri());
        sut.initWorkspaceMetadata(metadata);

        assertEquals(ws, sut.getCurrentWorkspace());
    }

    @Test
    void getCurrentWorkspaceMetadataRetrievesMetadataBasedOnCurrentWorkspaceIdentifier() {
        final Workspace ws = generateWorkspace();
        final WorkspaceMetadata metadata = new WorkspaceMetadata(ws);
        when(workspaceStore.getCurrentWorkspace()).thenReturn(ws.getUri());
        sut.initWorkspaceMetadata(metadata);

        assertEquals(metadata, sut.getCurrentWorkspaceMetadata());
    }

    @Test
    void loadWorkspaceLoadsWorkspaceMetadataAndStoresThemInCache() {
        final Workspace ws = generateWorkspace();
        when(workspaceStore.getCurrentWorkspace()).thenReturn(ws.getUri());
        final List<VocabularyInfo> metadata = initVocabularyInfo(ws);

        sut.loadWorkspace(ws);
        verify(workspaceDao).findWorkspaceVocabularyMetadata(ws);
        assertEquals(ws, sut.getWorkspace(ws.getUri()));
        verify(workspaceDao, never()).find(any());
        assertThat(sut.getWorkspaceMetadata(ws.getUri()).getVocabularies().values(),
                hasItems(metadata.toArray(new VocabularyInfo[]{})));
    }

    private List<VocabularyInfo> initVocabularyInfo(Workspace ws) {
        final VocabularyInfo viOne = new VocabularyInfo(Generator.generateUri(), Generator.generateUri(),
                Generator.generateUri());
        final VocabularyInfo viTwo = new VocabularyInfo(Generator.generateUri(), Generator.generateUri(),
                Generator.generateUri());
        final List<VocabularyInfo> info = Arrays.asList(viOne, viTwo);
        when(workspaceDao.findWorkspaceVocabularyMetadata(ws)).thenReturn(info);
        return info;
    }
}
