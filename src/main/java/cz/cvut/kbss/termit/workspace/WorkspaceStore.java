package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.exception.workspace.WorkspaceNotSetException;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpSession;
import java.net.URI;
import java.util.Objects;

import static cz.cvut.kbss.termit.util.Constants.WORKSPACE_SESSION_ATT;

public class WorkspaceStore {

    private final HttpSession session;

    private URI currentWorkspace;

    public WorkspaceStore(HttpSession session) {
        this.session = session;
    }

    @PostConstruct
    private void loadWorkspace() {
        if (session.getAttribute(WORKSPACE_SESSION_ATT) != null) {
            this.currentWorkspace = (URI) session.getAttribute(WORKSPACE_SESSION_ATT);
        }
    }

    public void setCurrentWorkspace(URI currentWorkspace) {
        this.currentWorkspace = Objects.requireNonNull(currentWorkspace);
        session.setAttribute(WORKSPACE_SESSION_ATT, currentWorkspace);
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
