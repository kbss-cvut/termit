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
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.repository.UserRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * User account-related business logic.
 */
@Service
public class UserService {

    private final UserRepositoryService repositoryService;

    private final SecurityUtils securityUtils;

    @Autowired
    public UserService(UserRepositoryService repositoryService, SecurityUtils securityUtils) {
        this.repositoryService = repositoryService;
        this.securityUtils = securityUtils;
    }

    /**
     * Gets accounts of all users in the system.
     *
     * @return List of user accounts ordered by last name and first name
     */
    public List<UserAccount> findAll() {
        return repositoryService.findAll();
    }

    /**
     * Finds a user with the specified id.
     *
     * @param id User identifier
     * @return Matching user wrapped in an {@code Optional}
     */
    public Optional<UserAccount> find(URI id) {
        return repositoryService.find(id);
    }

    /**
     * Finds a user with the specified id.
     *
     * @param id User identifier
     * @return Matching user account
     * @throws NotFoundException When no matching account is found
     */
    public UserAccount findRequired(URI id) {
        return repositoryService.findRequired(id);
    }

    /**
     * Retrieves currently logged in user.
     *
     * @return Currently logged in user's account
     */
    public UserAccount getCurrent() {
        final UserAccount account = securityUtils.getCurrentUser();
        account.erasePassword();
        return account;
    }
}
