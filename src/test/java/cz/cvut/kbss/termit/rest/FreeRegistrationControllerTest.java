/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.business.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FreeRegistrationControllerTest extends BaseControllerTestRunner {

    @Mock
    private UserService userService;

    @InjectMocks
    private FreeRegistrationController sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        setUp(sut);
    }

    @Test
    void createUserPersistsUser() throws Exception {
        final UserAccount user = Generator.generateUserAccount();
        mockMvc.perform(post("/users").content(toJson(user)).contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isCreated());
        verify(userService).persist(user);
    }
}
