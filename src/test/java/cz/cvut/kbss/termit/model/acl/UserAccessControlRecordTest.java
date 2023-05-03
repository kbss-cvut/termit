package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserAccessControlRecordTest {

    private final UserAccount account = Generator.generateUserAccount();

    @Test
    void getAccessLevelForReturnsAccessLevelWhenSpecifiedUserMatchesRecordUser() {
        final UserAccessControlRecord sut = new UserAccessControlRecord(AccessLevel.WRITE, account.toUser());
        final Optional<AccessLevel> result = sut.getAccessLevelFor(account);
        assertTrue(result.isPresent());
        assertEquals(sut.getAccessLevel(), result.get());
    }

    @Test
    void getAccessLevelForReturnsEmptyOptionalWhenSpecifiedUserDoesNotMatchRecordUser() {
        final UserAccessControlRecord sut = new UserAccessControlRecord(AccessLevel.WRITE,
                                                                        Generator.generateUserWithId());
        final Optional<AccessLevel> result = sut.getAccessLevelFor(account);
        assertFalse(result.isPresent());
    }
}
