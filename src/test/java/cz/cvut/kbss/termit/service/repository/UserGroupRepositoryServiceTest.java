package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.persistence.dao.UserGroupDao;
import org.hamcrest.core.IsNot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserGroupRepositoryServiceTest {

    @Mock
    private UserGroupDao dao;

    @InjectMocks
    private UserGroupRepositoryService sut;

    @Test
    void addMembersFindsUsersAndAddsThemToTargetGroup() {
        final UserGroup group = Generator.generateUserGroup();
        final List<UserAccount> accounts = List.of(Generator.generateUserAccount(), Generator.generateUserAccount());
        sut.addMembers(group, accounts.stream().map(UserAccount::toUser).collect(Collectors.toList()));
        verify(dao).update(group);
        assertThat(group.getMembers(), hasItems(accounts.stream().map(UserAccount::toUser).toArray(User[]::new)));
    }

    @Test
    void removeMembersRemovesUsersFromTargetGroup() {
        final UserGroup group = Generator.generateUserGroup();
        final List<User> users = IntStream.range(0, 5).mapToObj(i -> Generator.generateUserWithId())
                                          .collect(Collectors.toList());
        group.setMembers(new HashSet<>(users));
        final List<User> usersToRemove = users.subList(0, users.size() / 2);
        sut.removeMembers(group, usersToRemove);
        verify(dao).update(group);
        assertThat(group.getMembers(), IsNot.not(hasItems(usersToRemove.toArray(new User[]{}))));
        assertFalse(group.getMembers().isEmpty());
    }
}
