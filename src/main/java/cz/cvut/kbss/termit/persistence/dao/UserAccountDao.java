/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration.Persistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class UserAccountDao extends BaseDao<UserAccount> {

    private final Persistence config;

    @Autowired
    public UserAccountDao(EntityManager em, Configuration config) {
        super(UserAccount.class, em);
        this.config = config.getPersistence();
    }

    /**
     * Finds a user with the specified username.
     *
     * @param username Username to search by
     * @return User with matching username
     */
    public Optional<UserAccount> findByUsername(String username) {
        Objects.requireNonNull(username);
        try {
            return Optional
                    .of(em.createNativeQuery("SELECT ?x WHERE { ?x a ?type ; ?hasUsername ?username . }", type)
                            .setParameter("type", typeUri)
                            .setParameter("hasUsername", URI.create(Vocabulary.s_p_ma_uzivatelske_jmeno))
                            .setParameter("username", username, config.getLanguage()).getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Checks whether a user with the specified username exists.
     *
     * @param username Username to check
     * @return {@code true} if a user with the specified username exists
     */
    public boolean exists(String username) {
        Objects.requireNonNull(username);
        return em.createNativeQuery("ASK WHERE { ?x a ?type ; ?hasUsername ?username . }", Boolean.class)
                .setParameter("type", typeUri)
                .setParameter("hasUsername", URI.create(Vocabulary.s_p_ma_uzivatelske_jmeno))
                .setParameter("username", username, config.getLanguage()).getSingleResult();
    }

    @Override
    public List<UserAccount> findAll() {
        try {
            return em.createNativeQuery("SELECT ?x WHERE {" +
                    "?x a ?type ;" +
                    "?hasLastName ?lastName ;" +
                    "?hasFirstName ?firstName ." +
                    "} ORDER BY ?lastName ?firstName", type)
                    .setParameter("type", typeUri)
                    .setParameter("hasLastName", URI.create(Vocabulary.s_p_ma_prijmeni))
                    .setParameter("hasFirstName", URI.create(Vocabulary.s_p_ma_krestni_jmeno))
                    .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Checks whether an admin account exist in the repository.
     * @return {@code true} if there is an admin account (at least one), {@code false} otherwise
     */
    public boolean doesAdminExist() {
        try {
            return em.createNativeQuery("ASK { ?x a ?adminType . }", Boolean.class).setParameter("adminType", URI.create(Vocabulary.s_c_administrator_termitu)).getSingleResult();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
