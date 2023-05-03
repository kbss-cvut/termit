package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RoleAccessControlRecordTest {

    private final UserAccount account = Generator.generateUserAccount();

    @Test
    void getAccessLevelForReturnsAccessLevelWhenSpecifiedUserAccountHasRoleRepresentedByRecord() {
        final UserRole role = new UserRole(URI.create(Vocabulary.s_c_plny_uzivatel_termitu));
        account.addType(Vocabulary.s_c_plny_uzivatel_termitu);
        final RoleAccessControlRecord sut = new RoleAccessControlRecord(AccessLevel.READ, role);

        final Optional<AccessLevel> result = sut.getAccessLevelFor(account);
        assertTrue(result.isPresent());
        assertEquals(sut.getAccessLevel(), result.get());
    }

    @Test
    void getAccessLevelForReturnsEmptyOptionalWhenSpecifiedUserDoesNotHaveRoleRepresentedByRecord() {
        final UserRole role = new UserRole(URI.create(Vocabulary.s_c_administrator_termitu));
        final RoleAccessControlRecord sut = new RoleAccessControlRecord(AccessLevel.WRITE, role);

        final Optional<AccessLevel> result = sut.getAccessLevelFor(account);
        assertFalse(result.isPresent());
    }
}
