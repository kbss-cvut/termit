package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public class TermSnapshotLoader extends AssetSnapshotLoader<Term> {

    public TermSnapshotLoader(EntityManager em) {
        super(em, URI.create(SKOS.CONCEPT), URI.create(Vocabulary.s_c_term_version));
    }

    @Override
    public List<Snapshot> findSnapshots(Term asset) {
        Objects.requireNonNull(asset);
        try {
            return em.createNativeQuery("""
                                                SELECT ?s ?created ?asset ?type ?author ?authorFirstName ?authorLastName ?authorUsername WHERE {
                                                ?s a ?snapshotType ;
                                                  ?hasCreated ?created ;
                                                  ?versionOf ?source .
                                                OPTIONAL {
                                                  ?s ?inVocabulary ?vocSnapshot . ?vocSnapshot ?hasAuthor ?author .
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
                     .setParameter("hasCreated", URI.create(Vocabulary.s_p_has_date_and_time_of_creation_of_version))
                     .setParameter("hasAuthor", URI.create(DC.Terms.CREATOR))
                     .setParameter("inVocabulary", URI.create(SKOS.IN_SCHEME))
                     .setParameter("firstName", URI.create(Vocabulary.s_p_has_name))
                     .setParameter("lastName", URI.create(Vocabulary.s_p_has_surname))
                     .setParameter("accountName", URI.create(Vocabulary.s_p_has_username))
                     .setParameter("versionOf", URI.create(Vocabulary.s_p_is_version_of))
                     .setParameter("source", asset).getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    protected Class<Term> assetClass() {
        return Term.class;
    }
}
