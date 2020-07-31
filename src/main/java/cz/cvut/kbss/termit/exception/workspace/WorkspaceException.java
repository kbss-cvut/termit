package cz.cvut.kbss.termit.exception.workspace;

import cz.cvut.kbss.termit.exception.TermItException;

public class WorkspaceException extends TermItException {

    public WorkspaceException(String message) {
        super(message);
    }

    public WorkspaceException(String message, Throwable cause) {
        super(message, cause);
    }

    public WorkspaceException(Throwable cause) {
        super(cause);
    }
}
