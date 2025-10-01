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

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestPersistenceConfig;
import cz.cvut.kbss.termit.environment.config.TestRestSecurityConfig;
import cz.cvut.kbss.termit.environment.config.TestServiceConfig;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.business.UserService;
import cz.cvut.kbss.termit.service.mail.Postman;
import cz.cvut.kbss.termit.service.notification.PasswordChangeNotifier;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static cz.cvut.kbss.termit.util.Constants.REST_MAPPING_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminBasedRegistrationController.class)
@EnableAspectJAutoProxy
@EnableTransactionManagement
@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties({Configuration.class})
@ContextConfiguration(classes = {
        TestRestSecurityConfig.class,
        TestPersistenceConfig.class,
        TestServiceConfig.class}, initializers = {ConfigDataApplicationContextInitializer.class})
@ActiveProfiles("test")
class AdminBasedRegistrationControllerTest extends BaseControllerTestRunner {

    private static final String PATH = REST_MAPPING_PATH + "/admin/users";

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private SecurityUtils securityUtils;

    @MockitoSpyBean
    private Postman postman;

    @MockitoSpyBean
    private PasswordChangeNotifier passwordChangeNotifier;

    @MockitoSpyBean
    private UserService userService;

    @Test
    void createUserPersistsUserWhenCalledByAdmin() throws Exception {
        final UserAccount admin = Generator.generateUserAccountWithPassword();
        admin.addType(Vocabulary.s_c_administrator_termitu);
        Environment.setCurrentUser(admin);
        when(securityUtils.getCurrentUser()).thenReturn(admin);
        userService.persist(admin);
        final UserAccount user = Generator.generateUserAccountWithPassword();
        mockMvc.perform(post(PATH).content(toJson(user))
                                  .contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isCreated());
        verify(userService).adminCreateUser(user);
    }

    @Test
    void createUserThrowsForbiddenForNonAdminUser() throws Exception {
        final UserAccount admin = Generator.generateUserAccount();
        Environment.setCurrentUser(admin);
        when(securityUtils.getCurrentUser()).thenReturn(admin);
        final UserAccount user = Generator.generateUserAccount();
        mockMvc.perform(post(PATH).content(toJson(user))
                                  .contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isForbidden());
        verify(userService, never()).persist(any());
    }

    @Test
    void createUserSendsEmailWhenPasswordIsEmpty() throws Exception {
        final UserAccount admin = Generator.generateUserAccountWithPassword();
        admin.addType(Vocabulary.s_c_administrator_termitu);
        Environment.setCurrentUser(admin);
        when(securityUtils.getCurrentUser()).thenReturn(admin);
        userService.persist(admin);
        final UserAccount user = Generator.generateUserAccount();
        mockMvc.perform(post(PATH).content(toJson(user))
                                 .contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isCreated());

        ArgumentCaptor<PasswordChangeRequest> argumentCaptor = ArgumentCaptor.forClass(PasswordChangeRequest.class);
        verify(passwordChangeNotifier).sendCreatePasswordEmail(argumentCaptor.capture());
        assertEquals(user, argumentCaptor.getValue().getUserAccount());
        verify(postman).sendMessage(any());
    }
}
