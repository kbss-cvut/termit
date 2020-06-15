package cz.cvut.kbss.termit.exception.workspace;

/**
 * Indicates that no workspace is currently selected.
 * <p>
 * Most likely to be thrown when attempt to get the current workspace is made and there is no workspace selected.
 */
public class WorkspaceNotSetException extends WorkspaceException {

    public WorkspaceNotSetException(String message) {
        super(message);
    }
}
