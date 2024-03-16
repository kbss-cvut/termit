/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.rest.dto.UserUpdateDto;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.UserService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.service.IdentifierResolver.extractIdentifierFragment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest extends BaseControllerTestRunner {

    private static final String BASE_URL = "/users";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Configuration configuration;

    @Mock
    private UserService userService;

    @Mock
    private IdentifierResolver idResolverMock;

    @InjectMocks
    private UserController sut;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
        this.user = Generator.generateUserAccount();
        Environment.setCurrentUser(user);
    }

    @Test
    void getAllReturnsAllUsers() throws Exception {
        final List<UserAccount> users = IntStream.range(0, 5).mapToObj(i -> Generator.generateUserAccount())
                                                 .collect(Collectors.toList());
        when(userService.findAll()).thenReturn(users);

        final MvcResult mvcResult = mockMvc.perform(get(BASE_URL).accept(MediaType.APPLICATION_JSON_VALUE))
                                           .andExpect(status().isOk()).andReturn();
        final List<UserAccount> result = readValue(mvcResult, new TypeReference<List<UserAccount>>() {
        });
        assertEquals(users, result);
    }

    @Test
    void updateCurrentSendsUserUpdateToService() throws Exception {
        final UserUpdateDto dto = dtoForUpdate();

        mockMvc.perform(
                       put(BASE_URL + "/current").content(toJson(dto)).contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isNoContent());
        verify(userService).updateCurrent(dto);
    }

    private UserUpdateDto dtoForUpdate() {
        final UserUpdateDto dto = new UserUpdateDto();
        dto.setUri(user.getUri());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setPassword("newPassword");
        dto.setUsername(user.getUsername());
        dto.setOriginalPassword(user.getPassword());
        return dto;
    }

    @Test
    void unlockUnlocksUser() throws Exception {
        final String newPassword = "newPassword";

        when(idResolverMock.resolveIdentifier(eq(configuration.getNamespace().getUser()), any())).thenReturn(
                user.getUri());
        when(userService.findRequired(user.getUri())).thenReturn(user);
        mockMvc.perform(delete(BASE_URL + "/" + extractIdentifierFragment(user.getUri()) + "/lock")
                                .content(newPassword))
               .andExpect(status().isNoContent());
        verify(userService).unlock(user, newPassword);
    }

    @Test
    void enableEnablesUser() throws Exception {
        when(idResolverMock.resolveIdentifier(eq(configuration.getNamespace().getUser()), any())).thenReturn(
                user.getUri());
        when(userService.findRequired(user.getUri())).thenReturn(user);
        mockMvc.perform(post(BASE_URL + "/" + extractIdentifierFragment(user.getUri()) + "/status"))
               .andExpect(status().isNoContent());
        verify(userService).enable(user);
    }

    @Test
    void disableDisablesUser() throws Exception {
        when(idResolverMock.resolveIdentifier(eq(configuration.getNamespace().getUser()), any())).thenReturn(
                user.getUri());
        when(userService.findRequired(user.getUri())).thenReturn(user);
        mockMvc.perform(delete(BASE_URL + "/" + extractIdentifierFragment(user.getUri()) + "/status"))
               .andExpect(status().isNoContent());
        verify(userService).disable(user);
    }

    @Test
    void existsChecksForUsernameExistence() throws Exception {
        when(userService.exists(user.getUsername())).thenReturn(true);
        final MvcResult mvcResult = mockMvc.perform(get(BASE_URL + "/username").param("username", user.getUsername()))
                                           .andReturn();
        final Boolean result = readValue(mvcResult, Boolean.class);
        assertTrue(result);
    }

    @Test
    void changeRoleUsesProvidedNamespaceToResolveUserUri() throws Exception {
        final String namespace = Vocabulary.s_c_uzivatel_termitu + "/";
        when(idResolverMock.resolveIdentifier(any(), any())).thenReturn(user.getUri());
        when(userService.findRequired(user.getUri())).thenReturn(user);
        mockMvc.perform(put(BASE_URL + "/" + extractIdentifierFragment(user.getUri()) + "/role")
                                .queryParam(Constants.QueryParams.NAMESPACE, namespace)
                                .content(Vocabulary.s_c_omezeny_uzivatel_termitu).contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().isNoContent());
        verify(idResolverMock).resolveIdentifier(namespace, extractIdentifierFragment(user.getUri()));
        verify(userService).changeRole(user, Vocabulary.s_c_omezeny_uzivatel_termitu);
    }

    @Test
    void getManagedAssetsRetrievesManagedAssetsForUserWithSpecifiedUri() throws Exception {
        final String namespace = Vocabulary.s_c_uzivatel_termitu + "/";
        when(idResolverMock.resolveIdentifier(any(), any())).thenReturn(user.getUri());
        when(userService.getRequiredReference(user.getUri())).thenReturn(user);
        final List<RdfsResource> resources = Collections.singletonList(
                new RdfsResource(Generator.generateUri(), MultilingualString.create("Test term", Environment.LANGUAGE),
                                 null,
                                 SKOS.CONCEPT));
        when(userService.getManagedAssets(user)).thenReturn(resources);
        final MvcResult mvcResult = mockMvc.perform(
                get(BASE_URL + "/" + extractIdentifierFragment(user.getUri()) + "/managed-assets")
                        .queryParam(Constants.QueryParams.NAMESPACE, namespace)).andReturn();
        final List<RdfsResource> result = readValue(mvcResult, new TypeReference<List<RdfsResource>>() {
        });
        assertEquals(resources, result);
        verify(userService).getManagedAssets(user);
    }
}
