package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.workspace.WorkspaceNotSetException;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.persistence.WorkspaceMetadataCache;
import cz.cvut.kbss.termit.persistence.dao.WorkspaceDao;
import cz.cvut.kbss.termit.service.business.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.net.URI;

@Service
public class WorkspaceRepositoryService implements WorkspaceService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceRepositoryService.class);

    /**
     * Session attribute denoting the current user's workspace
     */
    public static final String WORKSPACE_SESSION_ATT = "workspace";

    private final HttpSession session;

    private final WorkspaceDao workspaceDao;

    private final WorkspaceMetadataCache workspaceCache;

    @Autowired
    public WorkspaceRepositoryService(HttpSession session, WorkspaceDao workspaceDao,
                                      WorkspaceMetadataCache workspaceCache) {
        this.session = session;
        this.workspaceDao = workspaceDao;
        this.workspaceCache = workspaceCache;
    }

    @Override
    public Workspace loadWorkspace(URI id) {
        LOG.trace("Loading workspace {}.", id);
        final Workspace ws = workspaceDao.find(id).orElseThrow(
                () -> NotFoundException.create(Workspace.class.getSimpleName(), id));
        LOG.trace("Storing workspace in session.");
        session.setAttribute(WORKSPACE_SESSION_ATT, ws.getUri());
        workspaceCache.putWorkspace(loadWorkspaceMetadata(ws));
        return ws;
    }

    private WorkspaceMetadata loadWorkspaceMetadata(Workspace ws) {
        // TODO
        return new WorkspaceMetadata(ws);
    }

    @Override
    public Workspace loadCurrentWorkspace() {
        // TODO
        return null;
    }

    @Override
    public Workspace getCurrentWorkspace() {
        final Object workspaceId = session.getAttribute(WORKSPACE_SESSION_ATT);
        if (workspaceId == null) {
            throw new WorkspaceNotSetException("No workspace is currently selected!");
        }
        assert workspaceId instanceof URI;

        return workspaceCache.getWorkspace((URI) workspaceId);
    }
}
