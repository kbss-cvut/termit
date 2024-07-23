package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.PasswordChangeDto;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.InvalidPasswordChangeRequestException;
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
import java.util.List;
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

        when(passwordChangeRequestRepositoryService.findAllByUsername(userAccount.getUsername()))
                .thenReturn(List.of());
        when(userRepositoryService.findByUsername(userAccount.getUsername()))
                .thenReturn(Optional.of(userAccount));
        when(passwordChangeRequestRepositoryService.create(userAccount)).thenReturn(request);


        sut.requestPasswordReset(userAccount.getUsername());

        verify(passwordChangeRequestRepositoryService).create(userAccount);
        verify(passwordChangeNotifier).sendEmail(request);
    }

    @Test
    void requestPasswordResetSinglePreviousRequestRemoved() {
        final UserAccount userAccount = Generator.generateUserAccount();
        final PasswordChangeRequest oldRequest = new PasswordChangeRequest();
        final PasswordChangeRequest request = new PasswordChangeRequest();

        when(passwordChangeRequestRepositoryService.findAllByUsername(userAccount.getUsername()))
                .thenReturn(List.of(oldRequest));
        when(userRepositoryService.findByUsername(userAccount.getUsername()))
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

        when(passwordChangeRequestRepositoryService.findAllByUsername(userAccount.getUsername()))
                .thenReturn(List.of(oldRequest_A, oldRequest_B, oldRequest_C));
        when(userRepositoryService.findByUsername(userAccount.getUsername()))
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
        when(userRepositoryService.findByUsername(username))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                sut.requestPasswordReset(username)
        );

        verify(userRepositoryService).findByUsername(username);
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

        assertThrows(InvalidPasswordChangeRequestException.class, () ->
                sut.changePassword(dto)
        );
        verify(passwordChangeRequestRepositoryService).find(dto.getUri());
    }

    @Test
    void changePasswordExpiredRequestExceptionThrown() {
        final PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCreatedAt(Instant.now().minus(configuration.getSecurity()
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
        request.setCreatedAt(Instant.now());
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


}
