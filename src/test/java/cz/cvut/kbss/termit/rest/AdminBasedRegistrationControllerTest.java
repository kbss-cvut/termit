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
import cz.cvut.kbss.termit.service.business.UserService;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static cz.cvut.kbss.termit.util.Constants.REST_MAPPING_PATH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminBasedRegistrationController.class)
@Import({TestRestSecurityConfig.class})
@ActiveProfiles("admin-registration-only")
class AdminBasedRegistrationControllerTest extends BaseControllerTestRunner {

    private static final String PATH = REST_MAPPING_PATH + "/users";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void createUserPersistsUserWhenCalledByAdmin() throws Exception {
        final UserAccount admin = Generator.generateUserAccount();
        admin.addType(Vocabulary.s_c_administrator_termitu);
        Environment.setCurrentUser(admin);
        final UserAccount user = Generator.generateUserAccount();
        mockMvc.perform(post(PATH).content(toJson(user))
                                  .contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isCreated());
        verify(userService).persist(user);
    }

    @Test
    void createUserThrowsForbiddenForNonAdminUser() throws Exception {
        final UserAccount admin = Generator.generateUserAccount();
        Environment.setCurrentUser(admin);
        final UserAccount user = Generator.generateUserAccount();
        mockMvc.perform(post(PATH).content(toJson(user))
                                  .contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isForbidden());
        verify(userService, never()).persist(any());
    }
}
