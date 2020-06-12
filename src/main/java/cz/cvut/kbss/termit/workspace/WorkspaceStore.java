package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.exception.workspace.WorkspaceNotSetException;

import java.net.URI;

public class WorkspaceStore {

    private URI currentWorkspace;

    public void setCurrentWorkspace(URI currentWorkspace) {
        this.currentWorkspace = currentWorkspace;
    }

    public URI getCurrentWorkspace() {
        if (currentWorkspace == null) {
            throw new WorkspaceNotSetException("No workspace is currently selected.");
        }
        return currentWorkspace;
    }

    public void clear() {
        this.currentWorkspace = null;
    }
}
