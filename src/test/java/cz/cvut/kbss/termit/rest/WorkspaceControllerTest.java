package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.WorkspaceService;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkspaceControllerTest extends BaseControllerTestRunner {

    private static final String PATH = "/workspaces/";
    private static final String NAMESPACE = Vocabulary.ONTOLOGY_IRI_model;
    private static final String FRAGMENT = "test-workspace";
    private static final URI WORKSPACE_URI = URI.create(NAMESPACE + FRAGMENT);

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private IdentifierResolver idResolver;

    @InjectMocks
    private WorkspaceController sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        setUp(sut);
    }

    @Test
    void loadWorkspaceUsesServiceToGetWorkspaceByIdentifier() throws Exception {
        when(idResolver.resolveIdentifier(anyString(), anyString())).thenReturn(WORKSPACE_URI);
        final Workspace workspace = new Workspace();
        workspace.setUri(WORKSPACE_URI);
        workspace.setLabel("test workspace");
        when(workspaceService.loadWorkspace(any())).thenReturn(workspace);
        mockMvc.perform(put(PATH + FRAGMENT).param(Constants.QueryParams.NAMESPACE, NAMESPACE))
               .andExpect(status().isOk());
        verify(idResolver).resolveIdentifier(NAMESPACE, FRAGMENT);
        verify(workspaceService).loadWorkspace(WORKSPACE_URI);
    }

    @Test
    void loadWorkspaceReturnsLoadedWorkspace() throws Exception {
        when(idResolver.resolveIdentifier(anyString(), anyString())).thenReturn(WORKSPACE_URI);
        final Workspace workspace = new Workspace();
        workspace.setUri(WORKSPACE_URI);
        workspace.setLabel("test workspace");
        when(workspaceService.loadWorkspace(any())).thenReturn(workspace);
        final MvcResult mvcResult = mockMvc
                .perform(put(PATH + FRAGMENT).param(Constants.QueryParams.NAMESPACE, NAMESPACE)).andReturn();
        final Workspace result = readValue(mvcResult, Workspace.class);
        assertEquals(workspace, result);
    }

    @Test
    void loadWorkspaceReturnsNotFoundWhenServiceThrowsNotFoundException() throws Exception {
        when(idResolver.resolveIdentifier(anyString(), anyString())).thenReturn(WORKSPACE_URI);
        when(workspaceService.loadWorkspace(any())).thenThrow(NotFoundException.class);
        mockMvc.perform(put(PATH + FRAGMENT).param(Constants.QueryParams.NAMESPACE, NAMESPACE))
               .andExpect(status().isNotFound());
    }
}
