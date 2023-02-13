package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.AbstractUser;
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

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGroupRepositoryServiceTest {

    @Mock
    private UserGroupDao dao;

    @Mock
    private UserRepositoryService userService;

    @InjectMocks
    private UserGroupRepositoryService sut;

    @Test
    void addUsersFindsUsersAndAddsThemToTargetGroup() {
        final UserGroup group = generateGroup();
        final List<UserAccount> accounts = List.of(Generator.generateUserAccount(), Generator.generateUserAccount());
        accounts.forEach(ua -> when(userService.findRequired(ua.getUri())).thenReturn(ua));
        sut.addUsers(group, accounts.stream().map(AbstractUser::getUri).toArray(URI[]::new));
        accounts.forEach(ua -> verify(userService).findRequired(ua.getUri()));
        verify(dao).update(group);
        assertThat(group.getMembers(), hasItems(accounts.stream().map(UserAccount::toUser).toArray(User[]::new)));
    }

    private static UserGroup generateGroup() {
        final UserGroup group = new UserGroup();
        group.setUri(Generator.generateUri());
        group.setLabel(UserGroup.class.getSimpleName() + Generator.randomInt());
        return group;
    }

    @Test
    void removeUsersRemovesUsersFromTargetGroup() {
        final UserGroup group = generateGroup();
        final List<User> users = IntStream.range(0, 5).mapToObj(i -> Generator.generateUserWithId())
                                          .collect(Collectors.toList());
        group.setMembers(new HashSet<>(users));
        final List<User> usersToRemove = users.subList(0, users.size() / 2);
        final List<URI> idsToRemove = usersToRemove.stream().map(AbstractUser::getUri).collect(Collectors.toList());
        sut.removeUsers(group, idsToRemove.toArray(new URI[]{}));
        verify(dao).update(group);
        assertThat(group.getMembers(), IsNot.not(hasItems(usersToRemove.toArray(new User[]{}))));
        assertFalse(group.getMembers().isEmpty());
    }
}
