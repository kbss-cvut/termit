package cz.cvut.kbss.termit.environment.config;

import cz.cvut.kbss.termit.aspect.Aspects;
import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.persistence.dao.workspace.CachingWorkspaceMetadataProvider;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceDao;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.workspace.WorkspaceStore;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@Configuration
public class WorkspaceTestConfig {

    public static URI DEFAULT_WORKSPACE = Generator.generateUri();
    public static URI DEFAULT_VOCABULARY_CTX = Generator.generateUri();

    @Bean(name = "workspaceStore")
    public WorkspaceStore workspaceStore() {
        final WorkspaceStore wsStore = spy(new WorkspaceStore(new MockHttpSession()));
        doReturn(DEFAULT_WORKSPACE).when(wsStore).getCurrentWorkspace();
        return wsStore;
    }

    @Bean
    @Primary
    public WorkspaceMetadataProvider workspaceMetadataCache(WorkspaceStore workspaceStore, WorkspaceDao workspaceDao) {
        final WorkspaceMetadataProvider cache = spy(new CachingWorkspaceMetadataProvider(workspaceStore, workspaceDao));
        final Workspace ws = WorkspaceGenerator.generateWorkspace();
        ws.setUri(DEFAULT_WORKSPACE);
        final WorkspaceMetadata wsMetadata = spy(new WorkspaceMetadata(ws));
        doReturn(wsMetadata).when(cache).getCurrentWorkspaceMetadata();
        doReturn(ws).when(cache).getCurrentWorkspace();
        final VocabularyInfo info = new VocabularyInfo(DEFAULT_VOCABULARY_CTX, DEFAULT_VOCABULARY_CTX,
                URI.create(DEFAULT_VOCABULARY_CTX.toString() +
                        Constants.DEFAULT_CHANGE_TRACKING_CONTEXT_EXTENSION));
        doReturn(info).when(wsMetadata).getVocabularyInfo(any());
        return cache;
    }
}
