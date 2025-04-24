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
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.model.UserAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class PasswordChangeRequestDao extends BaseDao<PasswordChangeRequest> {

    @Autowired
    public PasswordChangeRequestDao(EntityManager em) {
        super(PasswordChangeRequest.class, em);
    }

    public List<PasswordChangeRequest> findAllByUserAccount(UserAccount userAccount) {
        Objects.requireNonNull(userAccount);
        try {
            return em.createQuery("SELECT DISTINCT t FROM " + type.getSimpleName() + " t WHERE t.userAccount = :userAccount", type)
                     .setParameter("userAccount", userAccount)
                     .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
