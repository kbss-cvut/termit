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
import cz.cvut.kbss.termit.persistence.dao.UserAccountDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Service
public class UserRepositoryService {

    private final UserAccountDao userAccountDao;

    @Autowired
    public UserRepositoryService(UserAccountDao userAccountDao) {
        this.userAccountDao = userAccountDao;
    }

    public List<UserAccount> findAll() {
        final List<UserAccount> accounts = userAccountDao.findAll();
        accounts.forEach(UserAccount::erasePassword);
        return accounts;
    }

    public Optional<UserAccount> find(URI uri) {
        return userAccountDao.find(uri).map(u -> {
            u.erasePassword();
            return u;
        });
    }

    public UserAccount findRequired(URI uri) {
        return find(uri).orElseThrow(() -> NotFoundException.create(UserAccount.class.getSimpleName(), uri));
    }
}
