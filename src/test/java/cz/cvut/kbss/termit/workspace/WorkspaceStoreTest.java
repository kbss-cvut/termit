package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.exception.workspace.WorkspaceNotSetException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceStoreTest {

    @Test
    void getCurrentWorkspaceThrowsWorkspaceNotSetExceptionWhenNoCurrentWorkspaceIsSet() {
        final WorkspaceStore sut = new WorkspaceStore();
        assertThrows(WorkspaceNotSetException.class, sut::getCurrentWorkspace);
    }
}
