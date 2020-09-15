package cz.cvut.kbss.termit.persistence;

import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.workspace.WorkspaceNotLoadedException;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import cz.cvut.kbss.termit.workspace.WorkspaceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class WorkspaceMetadataProviderTest {

    @Mock
    private WorkspaceStore workspaceStore;

    @InjectMocks
    private WorkspaceMetadataProvider sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void getWorkspaceRetrievesWorkspaceMetadataFromCacheAndReturnsWorkspace() {
        final Workspace ws = generateWorkspace();
        final WorkspaceMetadata metadata = new WorkspaceMetadata(ws);
        sut.putWorkspace(metadata);

        final Workspace result = sut.getWorkspace(ws.getUri());
        assertEquals(ws, result);
    }

    private static Workspace generateWorkspace() {
        final Workspace ws = new Workspace();
        ws.setUri(Generator.generateUri());
        ws.setLabel("Test workspace");
        ws.setDescription("Test workspace description.");
        return ws;
    }

    @Test
    void getWorkspaceMetadataRetrievesWorkspaceMetadataFromCache() {
        final Workspace ws = generateWorkspace();
        final WorkspaceMetadata metadata = new WorkspaceMetadata(ws);
        sut.putWorkspace(metadata);

        final WorkspaceMetadata result = sut.getWorkspaceMetadata(ws.getUri());
        assertEquals(metadata, result);
    }

    @Test
    void getWorkspaceMetadataThrowsWorkspaceNotLoadedExceptionForUnknownWorkspaceIdentifier() {
        assertThrows(WorkspaceNotLoadedException.class, () -> sut.getWorkspaceMetadata(Generator.generateUri()));
    }

    @Test
    void getWorkspaceThrowsWorkspaceNotLoadedExceptionForUnknownWorkspaceIdentifier() {
        assertThrows(WorkspaceNotLoadedException.class, () -> sut.getWorkspace(Generator.generateUri()));
    }

    @Test
    void getCurrentWorkspaceRetrievesWorkspaceFromCacheBasedOnCurrentWorkspaceIdentifier() {
        final Workspace ws = generateWorkspace();
        final WorkspaceMetadata metadata = new WorkspaceMetadata(ws);
        when(workspaceStore.getCurrentWorkspace()).thenReturn(ws.getUri());
        sut.putWorkspace(metadata);

        assertEquals(ws, sut.getCurrentWorkspace());
    }

    @Test
    void getCurrentWorkspaceMetadataRetrievesMetadataBasedOnCurrentWorkspaceIdentifier() {
        final Workspace ws = generateWorkspace();
        final WorkspaceMetadata metadata = new WorkspaceMetadata(ws);
        when(workspaceStore.getCurrentWorkspace()).thenReturn(ws.getUri());
        sut.putWorkspace(metadata);

        assertEquals(metadata, sut.getCurrentWorkspaceMetadata());
    }
}
