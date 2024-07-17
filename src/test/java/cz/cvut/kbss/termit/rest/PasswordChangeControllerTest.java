package cz.cvut.kbss.termit.rest;


import cz.cvut.kbss.termit.dto.PasswordChangeDto;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.InvalidPasswordChangeTokenException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.business.PasswordChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.util.UUID;

import static cz.cvut.kbss.termit.service.business.PasswordChangeService.INVALID_TOKEN_ERROR_MESSAGE_ID;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PasswordChangeControllerTest extends BaseControllerTestRunner {

    @Mock
    private PasswordChangeService passwordChangeService;

    @InjectMocks
    private PasswordChangeController sut;

    @BeforeEach
    void setUp() {
        setUp(sut);
    }

    @Test
    void passwordResetRequestsPasswordReset() throws Exception {
        final UserAccount userAccount = Generator.generateUserAccount();

        mockMvc.perform(post("/password/reset/" + userAccount.getUsername()))
               .andExpect(status().isOk());
        verify(passwordChangeService).requestPasswordReset(userAccount.getUsername());
    }

    @Test
    void passwordChangeRequestedInvalidUserNotFoundReturned() throws Exception {
        final UserAccount userAccount = Generator.generateUserAccount();
        final String username = userAccount.getUsername();

        doThrow(NotFoundException.create(UserAccount.class, username))
                .when(passwordChangeService).requestPasswordReset(username);

        mockMvc.perform(post("/password/reset/" + username))
               .andExpect(status().isNotFound());

        verify(passwordChangeService).requestPasswordReset(username);
    }

    @Test
    void passwordChangeChangesPassword() throws Exception {
        final PasswordChangeDto dto = new PasswordChangeDto();
        dto.setUri(Generator.generateUri());
        dto.setNewPassword(UUID.randomUUID().toString());
        dto.setToken(UUID.randomUUID().toString());

        mockMvc.perform(post("/password/change").content(toJson(dto)).contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isOk());
        verify(passwordChangeService).changePassword(refEq(dto));
    }

    @Test
    void passwordChangeRequestedWithInvalidTokenForbiddenReturned() throws Exception {
        final PasswordChangeDto dto = new PasswordChangeDto();
        dto.setUri(Generator.generateUri());
        dto.setNewPassword(UUID.randomUUID().toString());
        dto.setToken(UUID.randomUUID().toString());

        doThrow(new InvalidPasswordChangeTokenException("Invalid or expired password change link", INVALID_TOKEN_ERROR_MESSAGE_ID))
                .when(passwordChangeService).changePassword(refEq(dto));

        mockMvc.perform(post("/password/change").content(toJson(dto)).contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(jsonPath("messageId").value(INVALID_TOKEN_ERROR_MESSAGE_ID))
               .andExpect(status().isForbidden());

        verify(passwordChangeService).changePassword(refEq(dto));
    }
}
