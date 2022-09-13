package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
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
            URI.create(Vocabulary.s_c_verze_slovniku),
            URI.create(Vocabulary.s_c_verze_glosare),
            URI.create(Vocabulary.s_c_verze_modelu),
            URI.create(Vocabulary.s_c_verze_pojmu)
    );

    private final EntityManager em;

    public SnapshotDao(EntityManager em) {
        this.em = em;
    }

    public Optional<Snapshot> find(URI uri) {
        Objects.requireNonNull(uri);
        try {
            return Optional.of((Snapshot) em.createNativeQuery("SELECT DISTINCT ?s ?created ?asset ?type WHERE { " +
                                                                       "?id a ?snapshotType ; " +
                                                                       "a ?type ; " +
                                                                       "?versionOf ?asset ; " +
                                                                       "?hasCreated ?created . " +
                                                                       "FILTER (?type in (?supportedTypes)) " +
                                                                       "BIND (?id as ?s)" +
                                                                       "}", "Snapshot")
                                            .setParameter("id", uri)
                                            .setParameter("snapshotType", URI.create(Vocabulary.s_c_verze_objektu))
                                            .setParameter("versionOf", URI.create(Vocabulary.s_p_je_verzi))
                                            .setParameter("hasCreated",
                                                          URI.create(Vocabulary.s_p_ma_datum_a_cas_vytvoreni_verze))
                                            .setParameter("supportedTypes", SNAPSHOT_TYPES)
                                            .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
