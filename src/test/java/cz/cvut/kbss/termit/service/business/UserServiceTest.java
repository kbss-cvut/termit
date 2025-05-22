/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.PasswordChangeDto;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.LoginAttemptsThresholdExceeded;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.InvalidPasswordChangeRequestException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.rest.dto.UserUpdateDto;
import cz.cvut.kbss.termit.service.notification.PasswordChangeNotifier;
import cz.cvut.kbss.termit.service.repository.PasswordChangeRequestRepositoryService;
import cz.cvut.kbss.termit.service.repository.UserRepositoryService;
import cz.cvut.kbss.termit.service.repository.UserRoleRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static cz.cvut.kbss.termit.environment.util.ContainsSameEntities.containsSameEntities;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepositoryService repositoryServiceMock;

    @Mock
    private UserRoleRepositoryService roleServiceMock;

    @Mock
    private SecurityUtils securityUtilsMock;

    @Mock
    private AccessControlListService aclService;

    // required as dependency for UserService
    @Spy
    private DtoMapper dtoMapper = Environment.getDtoMapper();

    @Spy
    Configuration configuration = new Configuration();

    @Mock
    PasswordChangeRequestRepositoryService passwordChangeRequestRepositoryService;

    @Mock
    PasswordChangeNotifier passwordChangeNotifier;

    @InjectMocks
    private UserService sut;

    @Test
    void findAllLoadsUsersFromRepositoryService() {
        sut.findAll();
        verify(repositoryServiceMock).findAll();
    }

    @Test
    void persistPassesAccountToPersistToRepositoryService() {
        final UserAccount toPersist = Generator.generateUserAccount();
        toPersist.setPassword("aaa");
        sut.persist(toPersist);
        verify(repositoryServiceMock).persist(toPersist);
    }

    @Test
    void persistThrowsValidationExceptionOnMissingPassword() {
        final UserAccount toPersist = Generator.generateUserAccount();
        toPersist.setPassword(null);
        assertThrows(ValidationException.class, () -> sut.persist(toPersist));
    }

    @Test
    void persistThrowsValidationExceptionWhenPasswordIsEmpty() {
        final UserAccount user = Generator.generateUserAccount();
        user.setPassword("");
        assertThrows(ValidationException.class, () -> sut.persist(user));
    }

    @Test
    void updateVerifiesOriginalPasswordBeforeUpdatingAccountWithNewPassword() {
        final UserUpdateDto update = new UserUpdateDto();
        update.setUri(Generator.generateUri());
        update.setFirstName("firstName");
        update.setLastName("lastName");
        update.setUsername("username");
        update.setPassword("password");
        update.setOriginalPassword("originalPassword");
        when(securityUtilsMock.getCurrentUser()).thenReturn(update.asUserAccount());
        sut.updateCurrent(update);
        final InOrder inOrder = Mockito.inOrder(securityUtilsMock, repositoryServiceMock);
        inOrder.verify(securityUtilsMock).verifyCurrentUserPassword(update.getOriginalPassword());
        inOrder.verify(repositoryServiceMock).update(update.asUserAccount());
    }

    @Test
    void updateDoesNotVerifyOriginalPasswordWhenAccountDoesNotUpdatePassword() {
        final UserUpdateDto update = new UserUpdateDto();
        update.setUri(Generator.generateUri());
        update.setFirstName("firstName");
        update.setLastName("lastName");
        update.setUsername("username");
        when(securityUtilsMock.getCurrentUser()).thenReturn(update.asUserAccount());
        sut.updateCurrent(update);
        verify(repositoryServiceMock).update(update.asUserAccount());
        verify(securityUtilsMock, never()).verifyCurrentUserPassword(any());
    }

    @Test
    void updateThrowsAuthorizationExceptionWhenAttemptingToUpdateDifferentUserThatCurrent() {
        final UserUpdateDto update = new UserUpdateDto();
        update.setUri(Generator.generateUri());
        update.setFirstName("firstName");
        update.setLastName("lastName");
        update.setUsername("username");
        final UserAccount ua = Generator.generateUserAccount();
        when(securityUtilsMock.getCurrentUser()).thenReturn(ua);
        assertThrows(AuthorizationException.class, () -> sut.updateCurrent(update));
    }

    @Test
    void updateThrowsValidationExceptionWhenAttemptingToUpdateUsernameOfAccount() {
        final UserAccount ua = Generator.generateUserAccount();
        final UserUpdateDto update = new UserUpdateDto();

        update.setUri(ua.getUri());
        update.setUsername("username");

        when(securityUtilsMock.getCurrentUser()).thenReturn(ua);
        assertThrows(ValidationException.class, () -> sut.updateCurrent(update));
    }

    @Test
    void existsChecksForUsernameExistenceInRepositoryService() {
        final String username = "user@termit";
        sut.exists(username);
        verify(repositoryServiceMock).exists(username);
    }

    @Test
    void unlockUnlocksUserAccountAndUpdatesItViaRepositoryService() {
        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(Generator.generateUserAccount());
        final UserAccount account = Generator.generateUserAccount();
        account.lock();
        sut.unlock(account, "newPassword");
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repositoryServiceMock).update(captor.capture());
        assertFalse(captor.getValue().isLocked());
    }

    @Test
    void unlockSetsNewPasswordOnAccountSpecifiedAsArgument() {
        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(Generator.generateUserAccount());
        final UserAccount account = Generator.generateUserAccount();
        account.lock();
        final String newPassword = "newPassword";
        sut.unlock(account, newPassword);
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repositoryServiceMock).update(captor.capture());
        assertEquals(newPassword, captor.getValue().getPassword());
    }

    @Test
    void unlockThrowsUnsupportedOperationExceptionWhenAttemptingToUnlockOwnAccount() {
        final UserAccount account = Generator.generateUserAccount();
        account.lock();
        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(account);
        assertThrows(UnsupportedOperationException.class, () -> sut.unlock(account, "newPassword"));
        verify(repositoryServiceMock, never()).update(any());
    }

    @Test
    void disableDisablesUserAccountAndUpdatesItViaRepositoryService() {
        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(Generator.generateUserAccount());
        final UserAccount account = Generator.generateUserAccount();
        sut.disable(account);
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repositoryServiceMock).update(captor.capture());
        assertFalse(captor.getValue().isEnabled());
    }

    @Test
    void disableThrowsUnsupportedOperationExceptionWhenAttemptingToDisableOwnAccount() {
        final UserAccount account = Generator.generateUserAccount();
        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(account);
        assertThrows(UnsupportedOperationException.class, () -> sut.disable(account));
        verify(repositoryServiceMock, never()).update(any());
    }

    @Test
    void enableEnablesUserAccountAndUpdatesItViaRepositoryService() {
        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(Generator.generateUserAccount());
        final UserAccount account = Generator.generateUserAccount();
        account.disable();
        sut.enable(account);
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repositoryServiceMock).update(captor.capture());
        assertTrue(captor.getValue().isEnabled());
    }

    @Test
    void enableThrowsUnsupportedOperationExceptionWhenAttemptingToEnableOwnAccount() {
        final UserAccount account = Generator.generateUserAccount();
        account.disable();
        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(account);
        assertThrows(UnsupportedOperationException.class, () -> sut.enable(account));
        verify(repositoryServiceMock, never()).update(any());
    }

    @Test
    void onLoginAttemptsThresholdExceededLocksUserAccountAndUpdatesItViaRepositoryService() {
        final UserAccount account = Generator.generateUserAccount();
        sut.onLoginAttemptsThresholdExceeded(new LoginAttemptsThresholdExceeded(account));
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repositoryServiceMock).update(captor.capture());
        assertTrue(captor.getValue().isLocked());
    }

    @Test
    void getCurrentRetrievesCurrentlyLoggedInUserAccount() {
        final UserAccount account = Generator.generateUserAccount();
        when(securityUtilsMock.getCurrentUser()).thenReturn(account);
        final UserAccount result = sut.getCurrent();
        assertEquals(account, result);
    }

    @Test
    void getCurrentReturnsCurrentUserAccountWithoutPassword() {
        final UserAccount account = Generator.generateUserAccount();
        account.setPassword("12345");
        when(securityUtilsMock.getCurrentUser()).thenReturn(account);
        final UserAccount result = sut.getCurrent();
        assertNull(result.getPassword());
    }

    @Test
    void getCurrentUpdatesLastSeenTimestampOfCurrentUser() {
        final UserAccount account = Generator.generateUserAccount();
        when(securityUtilsMock.getCurrentUser()).thenReturn(account);
        assertNull(account.getLastSeen());
        sut.getCurrent();
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repositoryServiceMock).update(captor.capture());
        assertEquals(account.getUri(), captor.getValue().getUri());
        assertNotNull(captor.getValue().getLastSeen());
    }

    @Test
    void persistAddsFullUserType() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        sut.persist(user);

        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repositoryServiceMock).persist(captor.capture());
        assertThat(captor.getValue().getTypes(), hasItem(Vocabulary.s_c_plny_uzivatel_termitu));
        assertThat(captor.getValue().getTypes(), not(hasItem(Vocabulary.s_c_omezeny_uzivatel_termitu)));
    }

    @Test
    void persistEnsuresAdminTypeIsNotPresentInUserAccount() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        user.addType(Vocabulary.s_c_administrator_termitu);
        sut.persist(user);

        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repositoryServiceMock).persist(captor.capture());
        assertThat(captor.getValue().getTypes(), not(hasItem(Vocabulary.s_c_administrator_termitu)));
    }

    @Test
    void persistDoesNotRestrictUserTypeIfItIsBeingPersistedByAdmin() {
        final UserAccount currentUser = Generator.generateUserAccountWithPassword();
        currentUser.addType(Vocabulary.s_c_administrator_termitu);
        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(currentUser);
        final UserAccount user = Generator.generateUserAccountWithPassword();
        user.addType(Vocabulary.s_c_administrator_termitu);
        sut.persist(user);

        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repositoryServiceMock).persist(captor.capture());
        assertThat(captor.getValue().getTypes(), hasItem(Vocabulary.s_c_administrator_termitu));
        assertThat(captor.getValue().getTypes(), not(hasItem(Vocabulary.s_c_omezeny_uzivatel_termitu)));
    }

    @Test
    void updateThrowsValidationExceptionWhenAttemptingToChangeRoles() {
        final UserAccount ua = Generator.generateUserAccount();
        final UserUpdateDto update = new UserUpdateDto();

        update.setUri(ua.getUri());
        update.setUsername(ua.getUsername());
        update.addType(Vocabulary.s_c_administrator_termitu);

        when(securityUtilsMock.getCurrentUser()).thenReturn(ua);
        ValidationException ex = assertThrows(ValidationException.class, () -> sut.updateCurrent(update));
        assertThat(ex.getMessage(), containsString("role"));
    }

    @Test
    void updateInvokesRepositoryServiceWhenDataAreValid() {
        final UserAccount ua = Generator.generateUserAccount();
        ua.addType(Vocabulary.s_c_plny_uzivatel_termitu);
        final UserUpdateDto update = new UserUpdateDto();

        update.setUri(ua.getUri());
        update.setUsername(ua.getUsername());
        update.setTypes(new HashSet<>(ua.getTypes()));

        when(securityUtilsMock.getCurrentUser()).thenReturn(ua);
        sut.updateCurrent(update);
        verify(repositoryServiceMock).update(update.asUserAccount());
    }

    @Test
    void changeRoleThrowsUnsupportedOperationExceptionWhenAttemptingToChangeOwnRole() {
        final UserAccount ua = Generator.generateUserAccount();
        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(ua);

        assertThrows(UnsupportedOperationException.class,
                     () -> sut.changeRole(ua, Vocabulary.s_c_administrator_termitu));
    }

    @Test
    void changeRoleReplacesPreviouslyAssignedRoleTypeWithSpecifiedOne() {
        final UserAccount ua = Generator.generateUserAccount();
        ua.addType(Vocabulary.s_c_omezeny_uzivatel_termitu);
        final UserAccount current = Generator.generateUserAccount();
        final UserRole rOne = new UserRole(URI.create(Vocabulary.s_c_administrator_termitu));
        final UserRole rTwo = new UserRole(URI.create(Vocabulary.s_c_plny_uzivatel_termitu));
        final UserRole rThree = new UserRole(URI.create(Vocabulary.s_c_omezeny_uzivatel_termitu));
        final List<UserRole> roles = Arrays.asList(rOne, rTwo, rThree);

        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(current);
        when(roleServiceMock.findAll()).thenReturn(roles);
        sut.changeRole(ua, Vocabulary.s_c_administrator_termitu);
        final ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repositoryServiceMock).update(captor.capture());
        assertThat(captor.getValue().getTypes(), hasItem(Vocabulary.s_c_administrator_termitu));
        assertThat(captor.getValue().getTypes(), not(hasItem(Vocabulary.s_c_omezeny_uzivatel_termitu)));
    }

    @Test
    void getManagedAssetsRetrievesManagedAssetsForSpecifiedUserAndReturnsThemAsRdfsResources() {
        final UserAccount ua = Generator.generateUserAccount();
        final List<cz.cvut.kbss.termit.model.Vocabulary> assets = List.of(Generator.generateVocabularyWithId(),
                                                                          Generator.generateVocabularyWithId());
        when(aclService.findAssetsByAgentWithSecurityAccess(any(User.class))).thenReturn((List) assets);

        final List<RdfsResource> result = sut.getManagedAssets(ua);
        verify(aclService).findAssetsByAgentWithSecurityAccess(ua.toUser());
        assertThat(result, containsSameEntities(assets));
    }


    @Test
    void requestPasswordResetRequestCreatedAndEmailSent() {
        final UserAccount userAccount = Generator.generateUserAccount();
        final PasswordChangeRequest request = new PasswordChangeRequest();

        when(passwordChangeRequestRepositoryService.findAllByUserAccount(userAccount))
                .thenReturn(List.of());
        when(repositoryServiceMock.findByUsername(userAccount.getUsername()))
                .thenReturn(Optional.of(userAccount));
        when(passwordChangeRequestRepositoryService.create(userAccount)).thenReturn(request);


        sut.requestPasswordReset(userAccount.getUsername());

        verify(passwordChangeRequestRepositoryService).create(userAccount);
        verify(passwordChangeNotifier).sendPasswordResetEmail(request);
    }

    @Test
    void requestPasswordResetSinglePreviousRequestRemoved() {
        final UserAccount userAccount = Generator.generateUserAccount();
        final PasswordChangeRequest oldRequest = new PasswordChangeRequest();
        final PasswordChangeRequest request = new PasswordChangeRequest();

        when(passwordChangeRequestRepositoryService.findAllByUserAccount(userAccount))
                .thenReturn(List.of(oldRequest));
        when(repositoryServiceMock.findByUsername(userAccount.getUsername()))
                .thenReturn(Optional.of(userAccount));
        when(passwordChangeRequestRepositoryService.create(userAccount)).thenReturn(request);

        sut.requestPasswordReset(userAccount.getUsername());

        verify(passwordChangeRequestRepositoryService).remove(oldRequest);
    }

    @Test
    void requestPasswordResetAllPreviousRequestsRemoved() {
        final UserAccount userAccount = Generator.generateUserAccount();
        final PasswordChangeRequest oldRequest_A = new PasswordChangeRequest();
        final PasswordChangeRequest oldRequest_B = new PasswordChangeRequest();
        final PasswordChangeRequest oldRequest_C = new PasswordChangeRequest();
        final PasswordChangeRequest request = new PasswordChangeRequest();

        when(passwordChangeRequestRepositoryService.findAllByUserAccount(userAccount))
                .thenReturn(List.of(oldRequest_A, oldRequest_B, oldRequest_C));
        when(repositoryServiceMock.findByUsername(userAccount.getUsername()))
                .thenReturn(Optional.of(userAccount));
        when(passwordChangeRequestRepositoryService.create(userAccount)).thenReturn(request);

        sut.requestPasswordReset(userAccount.getUsername());

        verify(passwordChangeRequestRepositoryService).remove(oldRequest_A);
        verify(passwordChangeRequestRepositoryService).remove(oldRequest_B);
        verify(passwordChangeRequestRepositoryService).remove(oldRequest_C);
    }

    @Test
    void requestPasswordResetInvalidUsernameExceptionThrown() {
        final String username = Generator.generateUserAccount().getUsername();
        when(repositoryServiceMock.findByUsername(username))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                sut.requestPasswordReset(username)
        );

        verify(repositoryServiceMock).findByUsername(username);
    }

    @Test
    void changePasswordValidRequestPasswordChanged() {
        final UserAccount account = Generator.generateUserAccountWithPassword();
        final String originalPassword = account.getPassword();
        final PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCreatedAt(Utils.timestamp());
        request.setToken(UUID.randomUUID().toString());
        request.setUserAccount(account);
        request.setUri(Generator.generateUri());

        final PasswordChangeDto dto = new PasswordChangeDto();
        dto.setToken(request.getToken());
        dto.setNewPassword(UUID.randomUUID().toString());
        dto.setUri(Generator.generateUri());

        when(passwordChangeRequestRepositoryService.find(dto.getUri()))
                .thenReturn(Optional.of(request));
        when(repositoryServiceMock.find(account.getUri()))
                .thenReturn(Optional.of(account));

        sut.changePassword(dto);

        verify(passwordChangeRequestRepositoryService).remove(request);
        verify(repositoryServiceMock).update(account);
        assertEquals(dto.getNewPassword(), account.getPassword());
        assertNotEquals(originalPassword, account.getPassword());
    }

    @Test
    void changePasswordRequestNotFoundExceptionThrown() {
        final PasswordChangeDto dto = new PasswordChangeDto();
        dto.setUri(Generator.generateUri());

        when(passwordChangeRequestRepositoryService.find(dto.getUri()))
                .thenReturn(Optional.empty());

        assertThrows(InvalidPasswordChangeRequestException.class, () ->
                sut.changePassword(dto)
        );
        verify(passwordChangeRequestRepositoryService).find(dto.getUri());
    }

    @Test
    void changePasswordExpiredRequestExceptionThrown() {
        final PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCreatedAt(Utils.timestamp().minus(configuration.getSecurity()
                                                              .getPasswordChangeRequestValidity())
                                    .minusNanos(1));
        request.setUri(Generator.generateUri());
        request.setToken(UUID.randomUUID().toString());

        final PasswordChangeDto dto = new PasswordChangeDto();
        dto.setToken(request.getToken());
        dto.setUri(request.getUri());

        when(passwordChangeRequestRepositoryService.find(dto.getUri()))
                .thenReturn(Optional.of(request));

        assertThrows(InvalidPasswordChangeRequestException.class, () ->
                sut.changePassword(dto)
        );
        verify(passwordChangeRequestRepositoryService).find(dto.getUri());
    }

    @Test
    void changePasswordValidURINotMatchingTokenExceptionThrown() {
        final PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCreatedAt(Utils.timestamp());
        request.setUri(Generator.generateUri());
        request.setToken(UUID.randomUUID().toString());

        final PasswordChangeDto dto = new PasswordChangeDto();
        dto.setToken("non-existing-token");
        dto.setUri(request.getUri());

        when(passwordChangeRequestRepositoryService.find(dto.getUri()))
                .thenReturn(Optional.of(request));

        assertThrows(InvalidPasswordChangeRequestException.class, () ->
                sut.changePassword(dto)
        );
        verify(passwordChangeRequestRepositoryService).find(dto.getUri());
    }

    @Test
    void changePasswordUnlocksLockedAccount() {
        final UserAccount user = Generator.generateUserAccount();
        user.lock();

        final PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCreatedAt(Utils.timestamp().minusMillis(1));
        request.setUri(Generator.generateUri());
        request.setToken(UUID.randomUUID().toString());
        request.setUserAccount(user);

        final PasswordChangeDto dto = new PasswordChangeDto();
        dto.setToken(request.getToken());
        dto.setNewPassword(UUID.randomUUID().toString());
        dto.setUri(request.getUri());

        when(passwordChangeRequestRepositoryService.find(dto.getUri()))
                .thenReturn(Optional.of(request));
        when(repositoryServiceMock.find(user.getUri())).thenReturn(Optional.of(user));

        assertTrue(user.isLocked());

        sut.changePassword(dto);

        assertFalse(user.isLocked());
        verify(passwordChangeRequestRepositoryService).find(dto.getUri());
        verify(repositoryServiceMock).update(user);
    }


    @Test
    void adminCreateUserCreatesUserWithPassword() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        final UserAccount admin = Generator.generateUserAccountWithPassword();
        admin.addType(Vocabulary.s_c_administrator_termitu);

        Environment.setCurrentUser(admin);
        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(admin);

        sut.adminCreateUser(user);

        verifyNoInteractions(passwordChangeNotifier);
        verifyNoInteractions(passwordChangeRequestRepositoryService);
        verify(repositoryServiceMock).persist(user);
    }

    @Test
    void adminCreateUserWithoutPasswordSendsEmail() {
        final UserAccount user = Generator.generateUserAccount();
        final UserAccount admin = Generator.generateUserAccountWithPassword();
        admin.addType(Vocabulary.s_c_administrator_termitu);
        final PasswordChangeRequest request = new PasswordChangeRequest();

        Environment.setCurrentUser(admin);
        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(admin);
        when(passwordChangeRequestRepositoryService.create(user)).thenReturn(request);

        sut.adminCreateUser(user);

        verify(passwordChangeNotifier).sendCreatePasswordEmail(request);
    }

    @Test
    void adminCreateUserWithoutPasswordCreatesLockedAccount() {
        final UserAccount user = Generator.generateUserAccount();
        final UserAccount admin = Generator.generateUserAccountWithPassword();
        admin.addType(Vocabulary.s_c_administrator_termitu);

        Environment.setCurrentUser(admin);
        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(admin);

        sut.adminCreateUser(user);

        assertTrue(user.isLocked());
    }

    @Test
    void adminCreateUserWithPasswordCreatesUnlockedAccount() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        final UserAccount admin = Generator.generateUserAccountWithPassword();
        admin.addType(Vocabulary.s_c_administrator_termitu);

        Environment.setCurrentUser(admin);
        when(securityUtilsMock.isAuthenticated()).thenReturn(true);
        when(securityUtilsMock.getCurrentUser()).thenReturn(admin);

        sut.adminCreateUser(user);

        assertFalse(user.isLocked());
    }

}
