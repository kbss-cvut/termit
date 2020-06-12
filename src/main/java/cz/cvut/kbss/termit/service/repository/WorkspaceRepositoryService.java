package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.workspace.WorkspaceNotSetException;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.persistence.WorkspaceMetadataCache;
import cz.cvut.kbss.termit.persistence.dao.WorkspaceDao;
import cz.cvut.kbss.termit.service.business.WorkspaceService;
import cz.cvut.kbss.termit.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WorkspaceRepositoryService implements WorkspaceService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceRepositoryService.class);

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
        LOG.trace("Storing workspace ID in session.");
        session.setAttribute(Constants.WORKSPACE_SESSION_ATT, ws.getUri());
        workspaceCache.putWorkspace(loadWorkspaceMetadata(ws));
        return ws;
    }

    private WorkspaceMetadata loadWorkspaceMetadata(Workspace ws) {
        final WorkspaceMetadata metadata = new WorkspaceMetadata(ws);
        final List<VocabularyInfo> vocabularies = workspaceDao.findWorkspaceVocabularyMetadata(ws);
        metadata.setVocabularies(vocabularies.stream().collect(Collectors.toMap(VocabularyInfo::getUri, vi -> vi)));
        return metadata;
    }

    @Override
    public Workspace loadCurrentWorkspace() {
        // TODO
        return null;
    }

    @Override
    public Workspace getCurrentWorkspace() {
        final Object workspaceId = session.getAttribute(Constants.WORKSPACE_SESSION_ATT);
        if (workspaceId == null) {
            throw new WorkspaceNotSetException("No workspace is currently selected!");
        }
        assert workspaceId instanceof URI;

        return workspaceCache.getWorkspace((URI) workspaceId);
    }
}
