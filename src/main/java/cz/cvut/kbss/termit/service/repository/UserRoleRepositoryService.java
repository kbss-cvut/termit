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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.persistence.dao.UserRoleDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

@Service
public class UserRoleRepositoryService {

    private final UserRoleDao dao;

    @Autowired
    public UserRoleRepositoryService(UserRoleDao dao) {
        this.dao = dao;
    }

    public List<UserRole> findAll() {
        return dao.findAll();
    }

    public UserRole findRequired(cz.cvut.kbss.termit.security.model.UserRole userRole) {
        final URI id = URI.create(userRole.getType());
        return dao.find(id).orElseThrow(() -> NotFoundException.create(UserRole.class, id));
    }
}
