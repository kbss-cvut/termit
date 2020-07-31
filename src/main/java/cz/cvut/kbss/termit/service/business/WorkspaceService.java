package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.model.Workspace;

import java.net.URI;

public interface WorkspaceService {

    /**
     * Loads workspace with the specified identifier and stores it is the user's session.
     *
     * @param id Workspace identifier
     */
    Workspace loadWorkspace(URI id);

    /**
     * Loads workspace associated with the current user as their current workspace.
     */
    Workspace loadCurrentWorkspace();

    /**
     * Gets the current user's loaded workspace.
     *
     * @return Current user's workspace
     */
    Workspace getCurrentWorkspace();
}
