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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.model.UserRole;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRoleDao {

    private final EntityManager em;

    public UserRoleDao(EntityManager em) {
        this.em = em;
    }

    public List<UserRole> findAll() {
        return em.createQuery("SELECT r FROM " + UserRole.class.getSimpleName() + " r", UserRole.class).getResultList();
    }

    public Optional<UserRole> find(URI id) {
        return Optional.ofNullable(em.find(UserRole.class, id));
    }
}
