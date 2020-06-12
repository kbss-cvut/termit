package cz.cvut.kbss.termit.persistence;

import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.exception.workspace.WorkspaceNotLoadedException;
import cz.cvut.kbss.termit.model.Workspace;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches workspace metadata (e.g., contexts of vocabularies in workspace) so that they don't have to be resolved from
 * vocabulary for every request.
 */
@Component
public class WorkspaceMetadataCache {

    /**
     * Workspace identifier -> workspace metadata
     */
    private final Map<URI, WorkspaceMetadata> workspaces = new ConcurrentHashMap<>();

    public void putWorkspace(WorkspaceMetadata metadata) {
        Objects.requireNonNull(metadata);
        assert metadata.getWorkspace() != null;

        workspaces.put(metadata.getWorkspace().getUri(), metadata);
    }

    public Workspace getWorkspace(URI workspaceUri) {
        if (!workspaces.containsKey(workspaceUri)) {
            throw WorkspaceNotLoadedException.create(workspaceUri);
        }
        return workspaces.get(workspaceUri).getWorkspace();
    }

    public WorkspaceMetadata getWorkspaceMetadata(URI workspaceUri) {
        if (!workspaces.containsKey(workspaceUri)) {
            throw WorkspaceNotLoadedException.create(workspaceUri);
        }
        return workspaces.get(workspaceUri);
    }
}
