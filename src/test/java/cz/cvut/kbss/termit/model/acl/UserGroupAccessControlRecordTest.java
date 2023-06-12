package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.UserGroup;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserGroupAccessControlRecordTest {

    @Test
    void getAccessLevelForReturnsAccessLevelWhenSpecifiedUserIsMemberOfUserGroupRepresentedByRecord() {
        final UserGroup group = new UserGroup();
        final UserAccount account = Generator.generateUserAccount();
        group.addMember(account.toUser());
        group.addMember(Generator.generateUserWithId());
        final UserGroupAccessControlRecord sut = new UserGroupAccessControlRecord(AccessLevel.SECURITY, group);

        final Optional<AccessLevel> result = sut.getAccessLevelFor(account);
        assertTrue(result.isPresent());
        assertEquals(sut.getAccessLevel(), result.get());
    }

    @Test
    void getAccessLevelForReturnsEmptyOptionalWhenSpecifiedMemberIsNotMemberOfUserGroupRepresentedByRecord() {
        final UserGroup group = new UserGroup();
        group.addMember(Generator.generateUserWithId());
        final UserGroupAccessControlRecord sut = new UserGroupAccessControlRecord(AccessLevel.SECURITY, group);

        final Optional<AccessLevel> result = sut.getAccessLevelFor(Generator.generateUserAccount());
        assertFalse(result.isPresent());
    }
}
