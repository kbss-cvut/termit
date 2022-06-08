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
            return em.createNativeQuery("SELECT ?s ?created ?asset ?type WHERE { " +
                                                "?s a ?snapshotType ; " +
                                                "?hasCreated ?created ; " +
                                                "?versionOf ?source . " +
                                                "BIND (?source as ?asset) . " +
                                                "BIND (?snapshotType as ?type) . " +
                                                "} ORDER BY DESC(?created)",
                                        "Snapshot")
                     .setParameter("snapshotType", snapshotType)
                     .setParameter("hasCreated",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_datum_a_cas_vytvoreni_verze))
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
                                                            "FILTER (?created > ?at) " +
                                                            "} ORDER BY ASC(?created) LIMIT 1",
                                                    (Class<T>) asset.getClass())
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
