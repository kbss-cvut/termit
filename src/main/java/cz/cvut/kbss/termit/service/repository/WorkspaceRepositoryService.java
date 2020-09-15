package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceDao;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import cz.cvut.kbss.termit.service.business.WorkspaceService;
import cz.cvut.kbss.termit.workspace.WorkspaceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;

@Service
public class WorkspaceRepositoryService implements WorkspaceService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceRepositoryService.class);

    private final WorkspaceDao workspaceDao;

    private final WorkspaceStore workspaceStore;

    private final WorkspaceMetadataProvider workspaceMetadataProvider;

    @Autowired
    public WorkspaceRepositoryService(WorkspaceDao workspaceDao, WorkspaceStore workspaceStore,
                                      WorkspaceMetadataProvider workspaceMetadataProvider) {
        this.workspaceDao = workspaceDao;
        this.workspaceStore = workspaceStore;
        this.workspaceMetadataProvider = workspaceMetadataProvider;
    }

    @Override
    public Workspace loadWorkspace(URI id) {
        LOG.trace("Loading workspace {}.", id);
        final Workspace ws = workspaceDao.find(id).orElseThrow(
                () -> NotFoundException.create(Workspace.class.getSimpleName(), id));
        LOG.trace("Storing workspace ID in session.");
        workspaceStore.setCurrentWorkspace(id);
        workspaceMetadataProvider.loadWorkspace(ws);
        return ws;
    }

    @Override
    public Workspace loadCurrentWorkspace() {
        // TODO
        return null;
    }

    @Override
    public Workspace getCurrentWorkspace() {
        return workspaceMetadataProvider.getCurrentWorkspace();
    }
}
