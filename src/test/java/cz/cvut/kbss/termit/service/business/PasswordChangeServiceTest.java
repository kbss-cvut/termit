package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.PasswordChangeDto;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.InvalidPasswordChangeTokenException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.notification.PasswordChangeNotifier;
import cz.cvut.kbss.termit.service.repository.PasswordChangeRequestRepositoryService;
import cz.cvut.kbss.termit.service.repository.UserRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PasswordChangeServiceTest {

    @Spy
    Configuration configuration = new Configuration();

    @Mock
    PasswordChangeRequestRepositoryService passwordChangeRequestRepositoryService;

    @Mock
    UserRepositoryService userRepositoryService;

    @Mock
    PasswordChangeNotifier passwordChangeNotifier;

    @InjectMocks
    PasswordChangeService sut;

    @Test
    void requestPasswordResetRequestCreatedAndEmailSent() {
        final UserAccount userAccount = Generator.generateUserAccount();
        final PasswordChangeRequest request = new PasswordChangeRequest();

        when(passwordChangeRequestRepositoryService.findByUsername(userAccount.getUsername()))
                .thenReturn(Optional.empty());
        when(userRepositoryService.findByUsername(userAccount.getUsername()))
                .thenReturn(Optional.of(userAccount));
        when(passwordChangeRequestRepositoryService.create(userAccount)).thenReturn(request);


        sut.requestPasswordReset(userAccount.getUsername());

        verify(passwordChangeRequestRepositoryService).create(userAccount);
        verify(passwordChangeNotifier).sendEmail(request);
    }

    @Test
    void requestPasswordResetPreviousRequestRemoved() {
        final UserAccount userAccount = Generator.generateUserAccount();
        final PasswordChangeRequest oldRequest = new PasswordChangeRequest();
        final PasswordChangeRequest request = new PasswordChangeRequest();

        when(passwordChangeRequestRepositoryService.findByUsername(userAccount.getUsername()))
                .thenReturn(Optional.of(oldRequest));
        when(userRepositoryService.findByUsername(userAccount.getUsername()))
                .thenReturn(Optional.of(userAccount));
        when(passwordChangeRequestRepositoryService.create(userAccount)).thenReturn(request);

        sut.requestPasswordReset(userAccount.getUsername());

        verify(passwordChangeRequestRepositoryService).remove(oldRequest);
    }

    @Test
    void requestPasswordResetInvalidUsernameExceptionThrown() {
        final String username = Generator.generateUserAccount().getUsername();
        when(userRepositoryService.findByUsername(username))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                sut.requestPasswordReset(username)
        );

        verify(userRepositoryService).findByUsername(username);
    }


    @Test
    void expiredRequestIsNotValid() {
        final PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCreatedAt(Instant.now().minus(configuration.getSecurity()
                                                              .getPasswordChangeTokenValidity())
                                    .minusNanos(1));

        boolean isValid = sut.isValid(request);
        assertFalse(isValid);
    }

    @Test
    void requestIsValid() {
        final PasswordChangeRequest request = new PasswordChangeRequest();
        Instant created = Instant.now().minus(configuration.getSecurity()
                                                           .getPasswordChangeTokenValidity()
                                                           .dividedBy(2));
        request.setCreatedAt(created);

        boolean isValid = sut.isValid(request);
        assertTrue(isValid);
    }

    @Test
    void changePasswordValidRequestPasswordChanged() {
        final UserAccount account = Generator.generateUserAccountWithPassword();
        final String originalPassword = account.getPassword();
        final PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCreatedAt(Instant.now());
        request.setToken(UUID.randomUUID().toString());
        request.setUserAccount(account);
        request.setUri(Generator.generateUri());

        final PasswordChangeDto dto = new PasswordChangeDto();
        dto.setToken(request.getToken());
        dto.setNewPassword(UUID.randomUUID().toString());
        dto.setUri(Generator.generateUri());

        when(passwordChangeRequestRepositoryService.find(dto.getUri()))
                .thenReturn(Optional.of(request));
        when(userRepositoryService.find(account.getUri()))
                .thenReturn(Optional.of(account));

        sut.changePassword(dto);

        verify(passwordChangeRequestRepositoryService).remove(request);
        verify(userRepositoryService).update(account);
        assertEquals(dto.getNewPassword(), account.getPassword());
        assertNotEquals(originalPassword, account.getPassword());
    }

    @Test
    void changePasswordRequestNotFoundExceptionThrown() {
        final PasswordChangeDto dto = new PasswordChangeDto();
        dto.setUri(Generator.generateUri());

        when(passwordChangeRequestRepositoryService.find(dto.getUri()))
                .thenReturn(Optional.empty());

        assertThrows(InvalidPasswordChangeTokenException.class, () ->
                sut.changePassword(dto)
        );
        verify(passwordChangeRequestRepositoryService).find(dto.getUri());
    }

    @Test
    void changePasswordInvalidRequestExceptionThrown() {
        final PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCreatedAt(Instant.now().minus(configuration.getSecurity()
                                                              .getPasswordChangeTokenValidity())
                                    .minusNanos(1));
        request.setUri(Generator.generateUri());
        request.setToken(UUID.randomUUID().toString());

        final PasswordChangeDto dto = new PasswordChangeDto();
        dto.setToken(request.getToken());
        dto.setUri(request.getUri());

        when(passwordChangeRequestRepositoryService.find(dto.getUri()))
                .thenReturn(Optional.of(request));

        assertThrows(InvalidPasswordChangeTokenException.class, () ->
                sut.changePassword(dto)
        );
        verify(passwordChangeRequestRepositoryService).find(dto.getUri());
    }

    @Test
    void changePasswordValidURINotMatchingTokenExceptionThrown() {
        final PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCreatedAt(Instant.now());
        request.setUri(Generator.generateUri());
        request.setToken(UUID.randomUUID().toString());

        final PasswordChangeDto dto = new PasswordChangeDto();
        dto.setToken("non-existing-token");
        dto.setUri(request.getUri());

        when(passwordChangeRequestRepositoryService.find(dto.getUri()))
                .thenReturn(Optional.of(request));

        assertThrows(InvalidPasswordChangeTokenException.class, () ->
                sut.changePassword(dto)
        );
        verify(passwordChangeRequestRepositoryService).find(dto.getUri());
    }


}
