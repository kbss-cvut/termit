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

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class SnapshotDao {

    /**
     * Supported snapshot types.
     */
    private static final List<URI> SNAPSHOT_TYPES = List.of(
            URI.create(Vocabulary.s_c_vocabulary_version),
            URI.create(Vocabulary.s_c_term_version)
    );

    private final EntityManager em;

    public SnapshotDao(EntityManager em) {
        this.em = em;
    }

    public Optional<Snapshot> find(URI uri) {
        Objects.requireNonNull(uri);
        try {
            return Optional.of((Snapshot) em.createNativeQuery("SELECT DISTINCT ?s ?created ?asset ?type ?author ?authorFirstName ?authorLastName ?authorUsername WHERE { " +
                                                                       "?id a ?snapshotType ; " +
                                                                       "a ?type ; " +
                                                                       "?versionOf ?asset ; " +
                                                                       "?hasCreated ?created . " +
                                                                       "OPTIONAL { " +
                                                                       "  ?id ?creator ?author . " +
                                                                       "  ?author ?firstName ?authorFirstName ; " +
                                                                       "          ?lastName ?authorLastName ; " +
                                                                       "          ?accountName ?authorUsername . " +
                                                                       "} " +
                                                                       "FILTER (?type in (?supportedTypes)) " +
                                                                       "BIND (?id as ?s)" +
                                                                       "}", "Snapshot")
                                            .setParameter("id", uri)
                                            .setParameter("snapshotType", URI.create(Vocabulary.s_c_object_version))
                                            .setParameter("versionOf", URI.create(Vocabulary.s_p_is_version_of))
                                            .setParameter("hasCreated",
                                                          URI.create(Vocabulary.s_p_has_date_and_time_of_creation_of_version))
                                            .setParameter("creator",
                                                          URI.create(DC.Terms.CREATOR))
                                            .setParameter("firstName",
                                                          URI.create(Vocabulary.s_p_has_name))
                                            .setParameter("lastName",
                                                          URI.create(Vocabulary.s_p_has_surname))
                                            .setParameter("accountName",
                                                          URI.create(Vocabulary.s_p_has_username))
                                            .setParameter("supportedTypes", SNAPSHOT_TYPES)
                                            .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
