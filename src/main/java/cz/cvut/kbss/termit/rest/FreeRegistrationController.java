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

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.business.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Allows registration for anyone.
 *
 * Available only if internal security is used.
 */
@ConditionalOnProperty(prefix = "termit.security", name = "provider", havingValue = "internal", matchIfMissing = true)
@Tag(name = "Registration", description = "User self-registration API")
@RestController
@RequestMapping("/users")
@Profile("!admin-registration-only")
public class FreeRegistrationController {

    private static final Logger LOG = LoggerFactory.getLogger(FreeRegistrationController.class);

    private final UserService userService;

    @Autowired
    public FreeRegistrationController(UserService userService) {
        this.userService = userService;
        LOG.debug("Instantiating free registration controller.");
    }

    @Operation(description = "Registers the specified user account.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User account successfully registered."),
            @ApiResponse(responseCode = "409", description = "User data are invalid.")
    })
    @PreAuthorize("permitAll()")
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public ResponseEntity<Void> createUser(@Parameter(description = "User account data.")
                                           @RequestBody UserAccount user) {
        userService.persist(user);
        LOG.info("User {} successfully registered.", user);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
