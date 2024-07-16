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

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.UserGroupService;
import cz.cvut.kbss.termit.util.Configuration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "User groups", description = "User group management API")
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
@RestController
@RequestMapping(UserGroupController.PATH)
public class UserGroupController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(UserGroupController.class);

    static final String PATH = "/groups";

    private final UserGroupService groupService;

    public UserGroupController(IdentifierResolver idResolver, Configuration config, UserGroupService groupService) {
        super(idResolver, config);
        this.groupService = groupService;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets a list of user groups.")
    @ApiResponse(responseCode = "200", description = "A list of user groups.")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_ADMIN + "', '" + SecurityConstants.ROLE_FULL_USER + "')")
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<UserGroup> getAll() {
        return groupService.findAll();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Creates a new user group.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User group successfully created."),
            @ApiResponse(responseCode = "409", description = "Provided data are invalid.")
    })
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> create(@Parameter(description = "User group metadata.")
                                       @RequestBody UserGroup group) {
        groupService.persist(group);
        LOG.debug("Group {} created.", group);
        return ResponseEntity.created(generateLocation(group.getUri())).build();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets the user group with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User group metadata."),
            @ApiResponse(responseCode = "404", description = "User group not found.")
    })
    @GetMapping(value = "/{localName}", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public UserGroup getById(@Parameter(description = UserGroupControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                        example = UserGroupControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                             @PathVariable String localName) {
        final URI uri = resolveIdentifier(UserGroup.NAMESPACE, localName);
        return groupService.findRequired(uri);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Deletes user group with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User group successfully deleted."),
            @ApiResponse(responseCode = "404", description = "User group not found.")
    })
    @DeleteMapping(value = "/{fragment}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@Parameter(description = UserGroupControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                  example = UserGroupControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                       @PathVariable String fragment) {
        final URI uri = resolveIdentifier(UserGroup.NAMESPACE, fragment);
        final UserGroup toRemove = groupService.getReference(uri);
        groupService.remove(toRemove);
        LOG.debug("Group {} removed.", toRemove);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Updates the label of the user group with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User group successfully updated."),
            @ApiResponse(responseCode = "404", description = "User group not found.")
    })
    @PutMapping(value = "/{fragment}/label")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateGroupLabel(
            @Parameter(description = UserGroupControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = UserGroupControllerDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String fragment,
            @Parameter(description = "New group name.")
            @RequestBody String label) {
        final UserGroup group = getById(fragment);
        group.setLabel(label);
        groupService.update(group);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Adds members to the user group with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Members successfully added."),
            @ApiResponse(responseCode = "404", description = "User group or user not found.")
    })
    @PostMapping(value = "/{fragment}/members", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMembers(
            @Parameter(description = UserGroupControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = UserGroupControllerDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String fragment,
            @Parameter(description = "Identifiers of users to add to the target user group.")
            @RequestBody Set<URI> toAdd) {
        final UserGroup target = getById(fragment);
        final List<User> usersToAdd = toAdd.stream().map(groupService::findRequiredUser)
                                           .collect(Collectors.toList());
        groupService.addMembers(target, usersToAdd);
        LOG.debug("{} users added to group {}.", usersToAdd.size(), target);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Removes members from the user group with the specified identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Members successfully removed."),
            @ApiResponse(responseCode = "404", description = "User group or user not found.")
    })
    @DeleteMapping(value = "/{fragment}/members", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMembers(
            @Parameter(description = UserGroupControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                       example = UserGroupControllerDoc.ID_LOCAL_NAME_EXAMPLE)
            @PathVariable String fragment,
            @Parameter(description = "Identifiers of users to remove from the target user group.")
            @RequestBody Set<URI> toRemove) {
        final UserGroup target = getById(fragment);
        final List<User> usersToRemove = toRemove.stream().map(groupService::findRequiredUser)
                                                 .collect(Collectors.toList());
        groupService.removeMembers(target, usersToRemove);
        LOG.debug("{} users removed from group {}.", usersToRemove.size(), target);
    }

    /**
     * A couple of constants for the {@link UserGroupController} API documentation.
     */
    private static final class UserGroupControllerDoc {
        private static final String ID_LOCAL_NAME_DESCRIPTION = "Locally (in the context of the specified namespace) unique part of the user group identifier.";
        private static final String ID_LOCAL_NAME_EXAMPLE = "instance-12345";
    }
}
