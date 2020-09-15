package cz.cvut.kbss.termit.persistence.dao.workspace;

import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.workspace.WorkspaceStore;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides workspace metadata (vocabularies, their contexts etc.) to system components.
 */
public abstract class WorkspaceMetadataProvider {

    protected final WorkspaceStore workspaceStore;
    protected final WorkspaceDao workspaceDao;

    protected WorkspaceMetadataProvider(WorkspaceStore workspaceStore, WorkspaceDao workspaceDao) {
        this.workspaceStore = workspaceStore;
        this.workspaceDao = workspaceDao;
    }

    /**
     * Gets workspace with the specified identifier.
     *
     * @param workspaceUri Workspace identifier
     * @return Workspace instance
     */
    public abstract Workspace getWorkspace(URI workspaceUri);

    /**
     * Gets metadata of a workspace with the specified identifier.
     *
     * @param workspaceUri Workspace identifier
     * @return Workspace metadata
     */
    public abstract WorkspaceMetadata getWorkspaceMetadata(URI workspaceUri);

    /**
     * Gets the currently loaded workspace.
     *
     * @return Current workspace
     * @throws cz.cvut.kbss.termit.exception.workspace.WorkspaceNotSetException When no workspace is currently loaded
     */
    public Workspace getCurrentWorkspace() {
        return getWorkspace(workspaceStore.getCurrentWorkspace());
    }

    /**
     * Gets metadata of the currently loaded workspace.
     *
     * @return Workspace metadata
     * @throws cz.cvut.kbss.termit.exception.workspace.WorkspaceNotSetException When no workspace is currently loaded
     */
    public WorkspaceMetadata getCurrentWorkspaceMetadata() {
        return getWorkspaceMetadata(workspaceStore.getCurrentWorkspace());
    }

    /**
     * Loads metadata for the current workspace so that it can be provided by this instance's methods.
     * <p>
     * The implementation is free to either pre-loaded and cache the metadata or ignore this call and reload metadata on
     * demand.
     *
     * @param workspace The workspace to load
     */
    public void loadWorkspace(Workspace workspace) {
        // Do nothing
    }

    protected WorkspaceMetadata loadWorkspaceMetadata(Workspace ws) {
        final WorkspaceMetadata metadata = new WorkspaceMetadata(ws);
        final List<VocabularyInfo> vocabularies = workspaceDao.findWorkspaceVocabularyMetadata(ws);
        metadata.setVocabularies(vocabularies.stream().collect(Collectors.toMap(VocabularyInfo::getUri, vi -> vi)));
        return metadata;
    }
}
