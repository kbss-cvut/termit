package cz.cvut.kbss.termit.util;

import cz.cvut.kbss.termit.model.acl.AccessLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {

    @Test
    void aclRecordSupportsDefaultValues() {
        final Configuration.ACL sut = new Configuration.ACL();
        assertEquals(AccessLevel.READ, sut.defaultEditorAccessLevel());
        assertEquals(AccessLevel.READ, sut.defaultReaderAccessLevel());
    }

    @Test
    void aclRecordSupportsExplicitArguments() {
        final Configuration.ACL sut = new Configuration.ACL(AccessLevel.WRITE, AccessLevel.NONE);
        assertEquals(AccessLevel.WRITE, sut.defaultEditorAccessLevel());
        assertEquals(AccessLevel.NONE, sut.defaultReaderAccessLevel());
    }
}
