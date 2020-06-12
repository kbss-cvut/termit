package cz.cvut.kbss.termit.persistence;

import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.workspace.WorkspaceNotLoadedException;
import cz.cvut.kbss.termit.model.Workspace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceMetadataCacheTest {

    private final WorkspaceMetadataCache sut = new WorkspaceMetadataCache();

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
}
