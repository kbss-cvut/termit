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
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.rest.dto.UserUpdateDto;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.UserService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Controller for user management.
 *
 * Enabled when internal security is used.
 */
@ConditionalOnProperty(prefix = "termit.security", name = "provider", havingValue = "internal", matchIfMissing = true)
@Tag(name = "Users", description = "User management API")
@RestController
@RequestMapping(UserController.PATH)
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class UserController extends BaseController {

    public static final String PATH = "/users";
    public static final String CURRENT_USER_PATH = "/current";

    private static final Logger LOG = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    @Autowired
    public UserController(UserService userService, IdentifierResolver idResolver, Configuration config) {
        super(idResolver, config);
        this.userService = userService;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets all users of the system.")
    @ApiResponse(responseCode = "200", description = "List of users ordered by name.")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_ADMIN + "', '" + SecurityConstants.ROLE_FULL_USER + "')")
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<UserAccount> getAll() {
        return userService.findAll();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets the currently logged-in user.")
    @ApiResponse(responseCode = "200", description = "Metadata of the current user's account.")
    @GetMapping(value = CURRENT_USER_PATH, produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public UserAccount getCurrent() {
        return userService.getCurrent();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Updates the current user's account.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Update successful."),
            @ApiResponse(responseCode = "409", description = "Update data are invalid.")
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping(value = CURRENT_USER_PATH, consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public void updateCurrent(@Parameter(description = "Updated user account metadata")
                              @RequestBody UserUpdateDto update) {
        userService.updateCurrent(update);
        LOG.debug("User {} successfully updated.", update);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Unlocks a previously locked user account. An account is locked when the number of login attempts reaches configured threshold.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User account unlocked."),
            @ApiResponse(responseCode = "404", description = "Account not found.")
    })
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @DeleteMapping(value = "/{localName}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@Parameter(description = UserControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                  example = UserControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                       @PathVariable String localName,
                       @Parameter(description = UserControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                  example = UserControllerDoc.ID_NAMESPACE_EXAMPLE)
                       @RequestParam(name = Constants.QueryParams.NAMESPACE,
                                     required = false) Optional<String> namespace,
                       @Parameter(description = "New password to set for the user.")
                       @RequestBody String newPassword) {
        final UserAccount user = getUserAccountForUpdate(namespace, localName);
        userService.unlock(user, newPassword);
        LOG.debug("User {} successfully unlocked.", user);
    }

    private UserAccount getUserAccountForUpdate(Optional<String> namespace, String identifierFragment) {
        final URI id = idResolver.resolveIdentifier(namespace.orElse(config.getNamespace().getUser()),
                                                    identifierFragment);
        return userService.findRequired(id);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Enables a previously disabled user account.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User account enabled."),
            @ApiResponse(responseCode = "404", description = "Account not found.")
    })
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @PostMapping(value = "/{localName}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(@Parameter(description = UserControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                  example = UserControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                       @PathVariable String localName,
                       @Parameter(description = UserControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                  example = UserControllerDoc.ID_NAMESPACE_EXAMPLE)
                       @RequestParam(name = Constants.QueryParams.NAMESPACE,
                                     required = false) Optional<String> namespace) {
        final UserAccount user = getUserAccountForUpdate(namespace, localName);
        userService.enable(user);
        LOG.debug("User {} successfully enabled.", user);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Disables a user account, preventing the user from logging in.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User account disabled."),
            @ApiResponse(responseCode = "404", description = "Account not found.")
    })
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @DeleteMapping(value = "/{localName}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@Parameter(description = UserControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                   example = UserControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                        @PathVariable String localName,
                        @Parameter(description = UserControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                   example = UserControllerDoc.ID_NAMESPACE_EXAMPLE)
                        @RequestParam(name = Constants.QueryParams.NAMESPACE,
                                      required = false) Optional<String> namespace) {
        final UserAccount user = getUserAccountForUpdate(namespace, localName);
        userService.disable(user);
        LOG.debug("User {} successfully disabled.", user);
    }

    @Operation(description = "checks if an account with the specified username exists.")
    @ApiResponse(responseCode = "200", description = "Account existence status.")
    @PreAuthorize("permitAll()")
    @GetMapping(value = "/username")
    public Boolean exists(@Parameter(description = "Username whose existence to check.")
                          @RequestParam(name = "username") String username) {
        return userService.exists(username);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Updates the role of the specified user account.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User account role updated."),
            @ApiResponse(responseCode = "404", description = "Account not found.")
    })
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @PutMapping(value = "/{localName}/role", consumes = {MediaType.TEXT_PLAIN_VALUE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeRole(@Parameter(description = UserControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                      example = UserControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                           @PathVariable String localName,
                           @Parameter(description = UserControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                      example = UserControllerDoc.ID_NAMESPACE_EXAMPLE)
                           @RequestParam(name = Constants.QueryParams.NAMESPACE,
                                         required = false) Optional<String> namespace,
                           @Parameter(description = "Role to set to the target user account.")
                           @RequestBody String role) {
        final UserAccount user = getUserAccountForUpdate(namespace, localName);
        userService.changeRole(user, role);
        LOG.debug("Role of user {} successfully changed to {}.", user, role);
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets assets that the user with the specified identifier can manage, i.e., has security-level access to them.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of managed assets."),
            @ApiResponse(responseCode = "404", description = "Account not found.")
    })
    @GetMapping(value = "/{localName}/managed-assets", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    public List<RdfsResource> getManagedAssets(@Parameter(description = UserControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                          example = UserControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                                               @PathVariable String localName,
                                               @Parameter(description = UserControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                                          example = UserControllerDoc.ID_NAMESPACE_EXAMPLE)
                                               @RequestParam(name = Constants.QueryParams.NAMESPACE,
                                                             required = false) Optional<String> namespace) {
        final URI id = idResolver.resolveIdentifier(namespace.orElse(config.getNamespace().getUser()), localName);
        return userService.getManagedAssets(userService.getReference(id));
    }

    /**
     * A couple of constants for the {@link UserController} API documentation.
     */
    static final class UserControllerDoc {
        static final String ID_LOCAL_NAME_DESCRIPTION = "Locally (in the context of the specified namespace/default user namespace) unique part of the user account identifier.";
        static final String ID_LOCAL_NAME_EXAMPLE = "system-administrator";
        static final String ID_NAMESPACE_DESCRIPTION = "Identifier namespace. Allows to override the default user identifier namespace.";
        static final String ID_NAMESPACE_EXAMPLE = "http://onto.fel.cvut.cz/ontologies/uzivatel/";
    }
}
