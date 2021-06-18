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

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.rest.dto.UserUpdateDto;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.UserService;
import cz.cvut.kbss.termit.util.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    @Autowired
    public UserController(UserService userService, IdentifierResolver idResolver, Configuration config) {
        super(idResolver, config);
        this.userService = userService;
    }

    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public List<UserAccount> getAll() {
        return userService.findAll();
    }

    @GetMapping(value = "/current", produces = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public UserAccount getCurrent() {
        return userService.getCurrent();
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping(value = "/current", consumes = {MediaType.APPLICATION_JSON_VALUE, JsonLd.MEDIA_TYPE})
    public void updateCurrent(@RequestBody UserUpdateDto update) {
        userService.updateCurrent(update);
        LOG.debug("User {} successfully updated.", update);
    }

    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @DeleteMapping(value = "/{fragment}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@PathVariable(name = "fragment") String identifierFragment, @RequestBody String newPassword) {
        final UserAccount user = getUserAccountForUpdate(identifierFragment);
        userService.unlock(user, newPassword);
        LOG.debug("User {} successfully unlocked.", user);
    }

    private UserAccount getUserAccountForUpdate(String identifierFragment) {
        final URI id = idResolver.resolveIdentifier(config.getNamespace().getUser(), identifierFragment);
        return userService.findRequired(id);
    }

    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @PostMapping(value = "/{fragment}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(@PathVariable(name = "fragment") String identifierFragment) {
        final UserAccount user = getUserAccountForUpdate(identifierFragment);
        userService.enable(user);
        LOG.debug("User {} successfully enabled.", user);
    }

    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @DeleteMapping(value = "/{fragment}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable(name = "fragment") String identifierFragment) {
        final UserAccount user = getUserAccountForUpdate(identifierFragment);
        userService.disable(user);
        LOG.debug("User {} successfully disabled.", user);
    }

    @PreAuthorize("permitAll()")
    @GetMapping(value = "/username")
    public Boolean exists(@RequestParam(name = "username") String username) {
        return userService.exists(username);
    }

    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @PutMapping(value = "/{fragment}/role",
        consumes = {MediaType.TEXT_PLAIN_VALUE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeRole(@PathVariable(name = "fragment") String identifierFragment,
                           @RequestBody String role) {
        final UserAccount user = getUserAccountForUpdate(identifierFragment);
        userService.changeRole(user, role);
        LOG.debug("Role of user {} successfully changed to {}.", user, role);
    }
}
