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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.persistence.dao.GenericDao;
import cz.cvut.kbss.termit.persistence.dao.UserAccountDao;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.validation.Validator;

@Service
public class UserRepositoryService extends BaseRepositoryService<UserAccount> {

    private final UserAccountDao userAccountDao;

    private final IdentifierResolver idResolver;

    private final PasswordEncoder passwordEncoder;

    private Configuration.Namespace cfgNamespace;

    @Autowired
    public UserRepositoryService(UserAccountDao userAccountDao, IdentifierResolver idResolver,
                                 PasswordEncoder passwordEncoder, Validator validator,
                                 Configuration config) {
        super(validator);
        this.userAccountDao = userAccountDao;
        this.idResolver = idResolver;
        this.passwordEncoder = passwordEncoder;
        this.cfgNamespace = config.getNamespace();
    }

    @Override
    protected GenericDao<UserAccount> getPrimaryDao() {
        return userAccountDao;
    }

    /**
     * Checks whether a user with the specified username exists.
     *
     * @param username Username to search by
     * @return {@code true} if a user with the specifier username exists
     */
    public boolean exists(String username) {
        return userAccountDao.exists(username);
    }

    @Override
    protected UserAccount postLoad(UserAccount instance) {
        instance.erasePassword();
        return instance;
    }

    @Override
    protected void prePersist(UserAccount instance) {
        validate(instance);
        if (instance.getUri() == null) {
            instance.setUri(idResolver
                    .generateIdentifier(cfgNamespace.getUser(), instance.getFirstName(), instance.getLastName()));
        }
        if (instance.getPassword() != null) {
            instance.setPassword(passwordEncoder.encode(instance.getPassword()));
        }
    }

    @Override
    protected void preUpdate(UserAccount instance) {
        final UserAccount original = userAccountDao.find(instance.getUri()).orElseThrow(
                () -> new NotFoundException("User " + instance + " does not exist."));
        if (instance.getPassword() != null) {
            instance.setPassword(passwordEncoder.encode(instance.getPassword()));
        } else {
            instance.setPassword(original.getPassword());
        }
        validate(instance);
    }

    /**
     * Checks whether an admin account exists in the system.
     *
     * @return {@code true} when there is an admin account, {@code false} otherwise
     */
    public boolean doesAdminExist() {
        return userAccountDao.doesAdminExist();
    }
}
