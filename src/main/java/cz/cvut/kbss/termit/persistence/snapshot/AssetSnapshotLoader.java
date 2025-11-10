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
package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Asset;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads asset snapshots from the repository.
 *
 * @param <T> Asset type
 */
public class AssetSnapshotLoader<T extends Asset<?>> {

    private final EntityManager em;

    private final URI assetType;

    private final URI snapshotType;

    public AssetSnapshotLoader(EntityManager em, URI assetType, URI snapshotType) {
        this.em = em;
        this.assetType = assetType;
        this.snapshotType = snapshotType;
    }

    public List<Snapshot> findSnapshots(T asset) {
        Objects.requireNonNull(asset);
        try {
            return em.createNativeQuery("SELECT ?s ?created ?asset ?type ?author ?authorFirstName ?authorLastName ?authorUsername WHERE { " +
                                                "?s a ?snapshotType ; " +
                                                "?hasCreated ?created ; " +
                                                "?versionOf ?source . " +
                                                "OPTIONAL {?s ?hasAuthor ?vAuthor .} " +
                                                "OPTIONAL {?s ?pdp_je_pojmem_ze_slovniku ?vocSnapshot . ?vocSnapshot ?hasAuthor ?tAuthor . } " +
                                                "BIND (COALESCE(?vAuthor, ?tAuthor) as ?author) . " +
                                                "BIND (?source as ?asset) . " +
                                                "BIND (?snapshotType as ?type) . " +
                                                "OPTIONAL { " +
                                                "  ?author ?firstName ?authorFirstName ; " +
                                                "          ?lastName ?authorLastName ; " +
                                                "          ?accountName ?authorUsername . " +
                                                "} " +
                                                "} ORDER BY DESC(?created)",
                                        "Snapshot")
                     .setParameter("snapshotType", snapshotType)
                     .setParameter("hasCreated",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_datum_a_cas_vytvoreni_verze))
                     .setParameter("hasAuthor",
                                   URI.create(DC.Terms.CREATOR))
                     .setParameter("pdp_je_pojmem_ze_slovniku",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                     .setParameter("firstName",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_krestni_jmeno))
                     .setParameter("lastName",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_prijmeni))
                     .setParameter("accountName",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_uzivatelske_jmeno))
                     .setParameter("versionOf", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi))
                     .setParameter("source", asset).getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    public Optional<T> findVersionValidAt(T asset, Instant at) {
        Objects.requireNonNull(asset);
        Objects.requireNonNull(at);
        try {
            return Optional.of(em.createNativeQuery("SELECT ?s WHERE { " +
                                                            "?s a ?type ; " +
                                                            "a ?snapshotType ; " +
                                                            "?versionOf ?asset ; " +
                                                            "?hasCreated ?created . " +
                                                            "FILTER (?created <= ?at) " +
                                                            "} ORDER BY DESC(?created)",
                                                    (Class<T>) asset.getClass())
                                       .setMaxResults(1)
                                 .setParameter("type", assetType)
                                 .setParameter("snapshotType", snapshotType)
                                 .setParameter("hasCreated",
                                               URI.create(
                                                       cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_datum_a_cas_vytvoreni_verze))
                                 .setParameter("versionOf", URI.create(
                                         cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi))
                                 .setParameter("at", at)
                                 .setParameter("asset", asset).getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
