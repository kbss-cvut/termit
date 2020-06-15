package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.workspace.WorkspaceNotSetException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import javax.servlet.http.HttpSession;
import java.net.URI;

import static cz.cvut.kbss.termit.util.Constants.WORKSPACE_SESSION_ATT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WorkspaceStoreTest {

    @Test
    void getCurrentWorkspaceThrowsWorkspaceNotSetExceptionWhenNoCurrentWorkspaceIsSet() {
        final WorkspaceStore sut = new WorkspaceStore(mock(HttpSession.class));
        assertThrows(WorkspaceNotSetException.class, sut::getCurrentWorkspace);
    }

    @Test
    void setCurrentWorkspaceStoresWorkspaceInSession() {
        final HttpSession session = new MockHttpSession();
        final WorkspaceStore sut = new WorkspaceStore(session);
        final URI wsUri = Generator.generateUri();

        sut.setCurrentWorkspace(wsUri);
        assertEquals(wsUri, session.getAttribute(WORKSPACE_SESSION_ATT));
    }
}
