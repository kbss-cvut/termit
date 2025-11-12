package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Vocabulary;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public class VocabularySnapshotLoader extends AssetSnapshotLoader<Vocabulary> {

    public VocabularySnapshotLoader(EntityManager em) {
        super(em, URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik), URI.create(
                cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku));
    }

    @Override
    public List<Snapshot> findSnapshots(Vocabulary asset) {
        Objects.requireNonNull(asset);
        try {
            return em.createNativeQuery("""
                                                SELECT ?s ?created ?asset ?type ?author ?authorFirstName ?authorLastName ?authorUsername WHERE {
                                                ?s a ?snapshotType ;
                                                  ?hasCreated ?created ;
                                                  ?versionOf ?source .
                                                OPTIONAL {
                                                  ?s ?hasAuthor ?author .
                                                  ?author ?firstName ?authorFirstName ;
                                                          ?lastName ?authorLastName ;
                                                          ?accountName ?authorUsername .
                                                }
                                                BIND (?source as ?asset) .
                                                BIND (?snapshotType as ?type) .
                                                } ORDER BY DESC(?created)
                                                """,
                                        "Snapshot")
                     .setParameter("snapshotType", snapshotType)
                     .setParameter("hasCreated",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_datum_a_cas_vytvoreni_verze))
                     .setParameter("hasAuthor",
                                   URI.create(DC.Terms.CREATOR))
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

    @Override
    protected Class<Vocabulary> assetClass() {
        return Vocabulary.class;
    }
}
