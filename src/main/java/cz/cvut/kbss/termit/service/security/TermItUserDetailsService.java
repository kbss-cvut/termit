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
package cz.cvut.kbss.termit.service.security;

import cz.cvut.kbss.termit.persistence.dao.UserAccountDao;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class TermItUserDetailsService implements UserDetailsService {

    private final UserAccountDao userAccountDao;

    @Autowired
    public TermItUserDetailsService(UserAccountDao userAccountDao) {
        this.userAccountDao = userAccountDao;
    }

    @Override
    public TermItUserDetails loadUserByUsername(String username) {
        return new TermItUserDetails(userAccountDao.findByUsername(username).orElseThrow(
                () -> new UsernameNotFoundException("User with username " + username + " not found.")));
    }
}
