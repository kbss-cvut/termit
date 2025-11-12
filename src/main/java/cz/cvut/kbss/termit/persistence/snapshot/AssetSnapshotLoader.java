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
public abstract class AssetSnapshotLoader<T extends Asset<?>> {

    protected final EntityManager em;

    protected final URI assetType;

    protected final URI snapshotType;

    public AssetSnapshotLoader(EntityManager em, URI assetType, URI snapshotType) {
        this.em = em;
        this.assetType = assetType;
        this.snapshotType = snapshotType;
    }

    public abstract List<Snapshot> findSnapshots(T asset);

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
                                                    assetClass())
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

    protected abstract Class<T> assetClass();
}
