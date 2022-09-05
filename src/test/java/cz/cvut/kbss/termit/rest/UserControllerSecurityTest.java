/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestRestSecurityConfig;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;

import static cz.cvut.kbss.termit.service.IdentifierResolver.extractIdentifierFragment;
import static cz.cvut.kbss.termit.util.Constants.REST_MAPPING_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * This tests only the security aspect of {@link UserController}. Functionality is tested in {@link
 * UserControllerTest}.
 */
@WebMvcTest(UserController.class)
@Import({TestRestSecurityConfig.class})
class UserControllerSecurityTest extends BaseControllerTestRunner {

    private static final String BASE_URL = REST_MAPPING_PATH + "/users";

    @MockBean
    private IdentifierResolver idResolver;

    @MockBean
    private UserService userService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void findAllThrowsForbiddenForUnauthorizedUser() throws Exception {
        Environment.setCurrentUser(Generator.generateUserAccountWithPassword());
        when(userService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get(BASE_URL)).andExpect(status().isForbidden());
        verify(userService, never()).findAll();
    }

    @Test
    void getCurrentReturnsCurrentlyLoggedInUser() throws Exception {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        Environment.setCurrentUser(user);
        when(userService.getCurrent()).thenReturn(user);
        final MvcResult mvcResult = mockMvc.perform(get(BASE_URL + "/current").accept(MediaType.APPLICATION_JSON_VALUE))
                                           .andExpect(status().isOk()).andReturn();
        final UserAccount result = readValue(mvcResult, UserAccount.class);
        assertEquals(user, result);
    }

    @Test
    void unlockThrowsForbiddenForNonAdmin() throws Exception {
        // This one is not an admin
        Environment.setCurrentUser(Generator.generateUserAccountWithPassword());
        final UserAccount toUnlock = Generator.generateUserAccountWithPassword();

        mockMvc.perform(
                delete(BASE_URL + "/" + extractIdentifierFragment(toUnlock.getUri()) + "/lock")
                        .content(toUnlock.getPassword()))
               .andExpect(status().isForbidden());
        verify(userService, never()).unlock(any(), any());
    }

    @Test
    void enableThrowsForbiddenForNonAdmin() throws Exception {
        // This one is not an admin
        Environment.setCurrentUser(Generator.generateUserAccountWithPassword());
        final UserAccount toEnable = Generator.generateUserAccountWithPassword();

        mockMvc.perform(post(BASE_URL + "/" + extractIdentifierFragment(toEnable.getUri()) + "/status"))
               .andExpect(status().isForbidden());
        verify(userService, never()).enable(any());
    }

    @Test
    void disableThrowsForbiddenForNonAdmin() throws Exception {
        // This one is not an admin
        Environment.setCurrentUser(Generator.generateUserAccountWithPassword());
        final UserAccount toDisable = Generator.generateUserAccountWithPassword();

        mockMvc.perform(delete(BASE_URL + "/" + extractIdentifierFragment(toDisable.getUri()) + "/status"))
               .andExpect(status().isForbidden());
        verify(userService, never()).disable(any());
    }
}
