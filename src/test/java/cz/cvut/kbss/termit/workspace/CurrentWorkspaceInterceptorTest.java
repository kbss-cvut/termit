package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.environment.Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import javax.servlet.http.HttpSession;
import java.net.URI;

import static cz.cvut.kbss.termit.util.Constants.WORKSPACE_SESSION_ATT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CurrentWorkspaceInterceptorTest {

    private MockHttpServletRequest mockRequest;

    private MockHttpServletResponse mockResponse;

    @Mock
    private WorkspaceStore workspaceStore;

    @InjectMocks
    private CurrentWorkspaceInterceptor sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
    }

    @Test
    void preHandleExtractsWorkspaceIdentifierFromRequestSession() {
        final HttpSession session = new MockHttpSession();
        final URI wsId = Generator.generateUri();
        session.setAttribute(WORKSPACE_SESSION_ATT, wsId);
        mockRequest.setSession(session);
        sut.preHandle(mockRequest, mockResponse, new Object());
        verify(workspaceStore).setCurrentWorkspace(wsId);
    }

    @Test
    void preHandleDoesNothingWhenRequestDoesNotHaveSession() {
        sut.preHandle(mockRequest, mockResponse, new Object());
        verify(workspaceStore, never()).setCurrentWorkspace(any());
    }

    @Test
    void preHandleDoesNothingWhenRequestSessionDoesNotContainWorkspaceIdentifier() {
        final HttpSession session = new MockHttpSession();
        mockRequest.setSession(session);
        sut.preHandle(mockRequest, mockResponse, new Object());
        verify(workspaceStore, never()).setCurrentWorkspace(any());
    }

    @Test
    void afterCompletionClearsWorkspaceStore() {
        sut.afterCompletion(mockRequest, mockResponse, new Object(), null);
        verify(workspaceStore).clear();
    }
}
