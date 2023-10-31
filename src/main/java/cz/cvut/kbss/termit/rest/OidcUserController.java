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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Controller for basic user-related operations that do not involve editing/adding/removing user accounts.
 *
 * Enabled when OIDC security is used.
 */
@ConditionalOnProperty(prefix = "termit.security", name = "provider", havingValue = "oidc")
@Tag(name = "Users", description = "User management API")
@RestController
@RequestMapping(UserController.PATH)
@PreAuthorize("hasRole('" + SecurityConstants.ROLE_RESTRICTED_USER + "')")
public class OidcUserController extends BaseController {

    private final UserService userService;

    public OidcUserController(UserService userService, IdentifierResolver idResolver, Configuration config) {
        super(idResolver, config);
        this.userService = userService;
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets the currently logged-in user.")
    @ApiResponse(responseCode = "200", description = "Metadata of the current user's account.")
    @GetMapping(value = UserController.CURRENT_USER_PATH, produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public UserAccount getCurrent() {
        return userService.getCurrent();
    }

    @Operation(security = {@SecurityRequirement(name = "bearer-key")},
               description = "Gets assets that the user with the specified identifier can manage, i.e., has security-level access to them.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of managed assets."),
            @ApiResponse(responseCode = "404", description = "Account not found.")
    })
    @GetMapping(value = "/{localName}/managed-assets", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    public List<RdfsResource> getManagedAssets(@Parameter(description = UserController.UserControllerDoc.ID_LOCAL_NAME_DESCRIPTION,
                                                          example = UserController.UserControllerDoc.ID_LOCAL_NAME_EXAMPLE)
                                               @PathVariable String localName,
                                               @Parameter(description = UserController.UserControllerDoc.ID_NAMESPACE_DESCRIPTION,
                                                          example = UserController.UserControllerDoc.ID_NAMESPACE_EXAMPLE)
                                               @RequestParam(name = Constants.QueryParams.NAMESPACE,
                                                             required = false) Optional<String> namespace) {
        final URI id = idResolver.resolveIdentifier(namespace.orElse(config.getNamespace().getUser()), localName);
        return userService.getManagedAssets(userService.getRequiredReference(id));
    }
}
