package cz.cvut.kbss.termit.exception.workspace;

import java.net.URI;

/**
 * Indicates that no workspace with the specified identifier has been loaded by TermIt.
 */
public class WorkspaceNotLoadedException extends WorkspaceException {

    private WorkspaceNotLoadedException(String message) {
        super(message);
    }

    public static WorkspaceNotLoadedException create(URI wsUri) {
        return new WorkspaceNotLoadedException("Workspace <" + wsUri + "> has not been loaded!");
    }
}
