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
package cz.cvut.kbss.termit.rest;


import cz.cvut.kbss.termit.dto.PasswordChangeDto;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.InvalidPasswordChangeRequestException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.business.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.util.UUID;

import static cz.cvut.kbss.termit.service.business.UserService.INVALID_TOKEN_ERROR_MESSAGE_ID;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PasswordChangeControllerTest extends BaseControllerTestRunner {

    @Mock
    private UserService userService;

    @InjectMocks
    private PasswordChangeController sut;

    @BeforeEach
    void setUp() {
        setUp(sut);
    }

    @Test
    void passwordResetRequestsPasswordReset() throws Exception {
        final UserAccount userAccount = Generator.generateUserAccount();

        mockMvc.perform(post("/password")
                       .content(userAccount.getUsername())
                       .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().isNoContent());
        verify(userService).requestPasswordReset(userAccount.getUsername());
    }

    @Test
    void passwordChangeRequestedInvalidUserNotFoundReturned() throws Exception {
        final UserAccount userAccount = Generator.generateUserAccount();
        final String username = userAccount.getUsername();

        doThrow(NotFoundException.create(UserAccount.class, username))
                .when(userService).requestPasswordReset(username);

        mockMvc.perform(post("/password")
                       .content(username)
                       .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().isNotFound());

        verify(userService).requestPasswordReset(eq(username));
    }

    @Test
    void passwordChangeChangesPassword() throws Exception {
        final PasswordChangeDto dto = new PasswordChangeDto();
        dto.setUri(Generator.generateUri());
        dto.setNewPassword(UUID.randomUUID().toString());
        dto.setToken(UUID.randomUUID().toString());

        mockMvc.perform(put("/password").content(toJson(dto)).contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isNoContent());
        verify(userService).changePassword(refEq(dto));
    }

    @Test
    void passwordChangeRequestedWithInvalidTokenConflictReturned() throws Exception {
        final PasswordChangeDto dto = new PasswordChangeDto();
        dto.setUri(Generator.generateUri());
        dto.setNewPassword(UUID.randomUUID().toString());
        dto.setToken(UUID.randomUUID().toString());

        doThrow(new InvalidPasswordChangeRequestException("Invalid or expired password change link", INVALID_TOKEN_ERROR_MESSAGE_ID))
                .when(userService).changePassword(refEq(dto));

        mockMvc.perform(put("/password").content(toJson(dto)).contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(jsonPath("messageId").value(INVALID_TOKEN_ERROR_MESSAGE_ID))
               .andExpect(status().isConflict());

        verify(userService).changePassword(refEq(dto));
    }
}
