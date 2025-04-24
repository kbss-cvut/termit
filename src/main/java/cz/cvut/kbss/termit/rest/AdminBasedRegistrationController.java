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

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.business.UserService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Allows only administrators to register new users.
 *
 * Available only if internal security is used.
 */
@ConditionalOnProperty(prefix = "termit.security", name = "provider", havingValue = "internal", matchIfMissing = true)
@Tag(name = "Admin User Registration", description = "Allows admins to register new users.")
@RestController
@RequestMapping("/admin/users")
public class AdminBasedRegistrationController {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBasedRegistrationController.class);

    private final UserService userService;

    @Autowired
    public AdminBasedRegistrationController(UserService userService) {
        this.userService = userService;
        LOG.debug("Instantiating admin-based registration controller.");
    }

    @Operation(security = {@SecurityRequirement(name="bearer-key")},
               description = "Creates a new user account. If the password is blank, the account is locked, and an email will be sent to the new user with a link to create a password.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "409", description = "User data are invalid")
    })
    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> createUser(@RequestBody UserAccount user) {
        userService.adminCreateUser(user);
        LOG.info("User {} successfully registered by {}.", user, userService.getCurrent().getUsername());
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
