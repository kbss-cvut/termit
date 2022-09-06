package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.util.Constants;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

@Component
public class CascadingVocabularySnapshotRemover implements SnapshotRemover {

    private final VocabularyDao vocabularyDao;

    private final EntityManager em;

    public CascadingVocabularySnapshotRemover(VocabularyDao vocabularyDao, EntityManager em) {
        this.vocabularyDao = vocabularyDao;
        this.em = em;
    }

    @Override
    public void removeSnapshot(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        final Vocabulary toRemove = vocabularyDao.getReference(snapshot.getUri()).orElseThrow(
                () -> NotFoundException.create(Vocabulary.class, snapshot.getUri()));
        if (!toRemove.isSnapshot()) {
            throw new UnsupportedOperationException("Vocabulary " + toRemove + " is not a snapshot.");
        }
        final Set<URI> snapshotsToRemove = vocabularyDao.getRelatedVocabularies(toRemove,
                                                                                Constants.SKOS_CONCEPT_MATCH_RELATIONSHIPS);
        snapshotsToRemove.forEach(snapshotUri -> {
            final URI ctx = resolveSnapshotContext(snapshotUri);
            clearContext(ctx);
        });
    }

    private URI resolveSnapshotContext(URI snapshot) {
        return em.createNativeQuery("SELECT ?g WHERE { GRAPH ?g { ?snapshot a ?snapshotType . } }", URI.class)
                 .setParameter("snapshot", snapshot)
                 .setParameter("snapshotType", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku))
                 .getSingleResult();
    }

    private void clearContext(URI ctx) {
        em.createNativeQuery("DROP GRAPH ?ctx").setParameter("ctx", ctx).executeUpdate();
    }
}
