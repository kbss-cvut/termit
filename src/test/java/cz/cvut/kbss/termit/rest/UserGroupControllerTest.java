package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.AbstractUser;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.UserGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static cz.cvut.kbss.termit.environment.util.ContainsSameEntities.containsSameEntities;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserGroupControllerTest extends BaseControllerTestRunner {

    @Mock
    private UserGroupService groupService;

    @Spy
    private IdentifierResolver identifierResolver = new IdentifierResolver();

    @InjectMocks
    private UserGroupController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
    }

    @Test
    void createReturnsCreatedResponseWithLocationHeader() throws Exception {
        final UserGroup group = Generator.generateUserGroup();
        final String fragment = IdentifierResolver.extractIdentifierFragment(group.getUri());
        final MvcResult mvcResult = mockMvc.perform(post(UserGroupController.PATH).content(toJson(group))
                                                                                  .contentType(
                                                                                          MediaType.APPLICATION_JSON_VALUE))
                                           .andExpect(status().isCreated()).andReturn();
        verifyLocationEquals(UserGroupController.PATH + "/" + fragment, mvcResult);
        verify(groupService).persist(group);
    }

    @Test
    void removeRemovesSpecifiedGroupViaService() throws Exception {
        final UserGroup toRemove = Generator.generateUserGroup();
        final String fragment = IdentifierResolver.extractIdentifierFragment(toRemove.getUri());
        when(groupService.getRequiredReference(toRemove.getUri())).thenReturn(toRemove);
        mockMvc.perform(delete(UserGroupController.PATH + "/" + fragment)).andExpect(status().isNoContent());
        verify(groupService).remove(toRemove);
    }

    @Test
    void addMembersFindsUsersAndAddsThemToSpecifiedGroupViaService() throws Exception {
        final UserGroup target = Generator.generateUserGroup();
        final String fragment = IdentifierResolver.extractIdentifierFragment(target.getUri());
        when(groupService.findRequired(target.getUri())).thenReturn(target);
        final List<User> users = List.of(Generator.generateUserWithId(), Generator.generateUserWithId());
        users.forEach(u -> when(groupService.findRequiredUser(u.getUri())).thenReturn(u));
        mockMvc.perform(post(UserGroupController.PATH + "/" + fragment + "/members")
                                .content(toJson(users.stream().map(AbstractUser::getUri).collect(Collectors.toList())))
                                .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isNoContent());
        users.forEach(u -> verify(groupService).findRequiredUser(u.getUri()));
        final ArgumentCaptor<Collection<User>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(groupService).addMembers(eq(target), captor.capture());
        assertThat(captor.getValue(), containsSameEntities(users));
    }

    @Test
    void removeMembersFindsUsersAndRemovesThemFromSpecifiedGroupViaService() throws Exception {
        final UserGroup target = Generator.generateUserGroup();
        final String fragment = IdentifierResolver.extractIdentifierFragment(target.getUri());
        when(groupService.findRequired(target.getUri())).thenReturn(target);
        final List<User> users = List.of(Generator.generateUserWithId(), Generator.generateUserWithId());
        target.setMembers(new HashSet<>(users));
        users.forEach(u -> when(groupService.findRequiredUser(u.getUri())).thenReturn(u));
        mockMvc.perform(delete(UserGroupController.PATH + "/" + fragment + "/members")
                                .content(toJson(users.stream().map(AbstractUser::getUri).collect(Collectors.toList())))
                                .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isNoContent());
        users.forEach(u -> verify(groupService).findRequiredUser(u.getUri()));
        final ArgumentCaptor<Collection<User>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(groupService).removeMembers(eq(target), captor.capture());
        assertThat(captor.getValue(), containsSameEntities(users));
    }

    @Test
    void updateLabelUpdatesGroupLabelWithSpecifiedValueViaService() throws Exception {
        final UserGroup target = Generator.generateUserGroup();
        final String fragment = IdentifierResolver.extractIdentifierFragment(target.getUri());
        when(groupService.findRequired(target.getUri())).thenReturn(target);
        final String newLabel = "new label";
        mockMvc.perform(put(UserGroupController.PATH + "/" + fragment + "/label").content(newLabel)
                                                                                 .contentType(MediaType.TEXT_PLAIN))
               .andExpect(status().isNoContent());
        final ArgumentCaptor<UserGroup> captor = ArgumentCaptor.forClass(UserGroup.class);
        verify(groupService).update(captor.capture());
        assertEquals(newLabel, captor.getValue().getLabel());
    }
}
