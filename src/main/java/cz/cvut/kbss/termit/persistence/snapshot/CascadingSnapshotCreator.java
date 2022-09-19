package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

import static cz.cvut.kbss.termit.util.Constants.SKOS_CONCEPT_MATCH_RELATIONSHIPS;
import static cz.cvut.kbss.termit.util.Utils.uriToString;

/**
 * {@link SnapshotCreator} implementation that cascades the operation.
 * <p>
 * The cascading will recursively include vocabularies whose terms are in SKOS-based relationships with terms from the
 * vocabularies already selected for snapshot creation.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CascadingSnapshotCreator extends SnapshotCreator {

    private static final Logger LOG = LoggerFactory.getLogger(CascadingSnapshotCreator.class);

    private final EntityManager em;

    private final VocabularyDao vocabularyDao;

    private final String snapshotVocabularyQuery;
    private final String snapshotTermQuery;

    public CascadingSnapshotCreator(Configuration configuration, EntityManager em,
                                    VocabularyDao vocabularyDao) {
        super(configuration);
        this.em = em;
        this.vocabularyDao = vocabularyDao;
        this.snapshotVocabularyQuery = Utils.loadQuery("snapshot/vocabulary.ru");
        this.snapshotTermQuery = Utils.loadQuery("snapshot/term.ru");
    }

    @Override
    public Snapshot createSnapshot(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        LOG.info("Creating snapshot of {}.", vocabulary);
        final Set<URI> toSnapshot = resolveVocabulariesToSnapshot(vocabulary);
        toSnapshot.forEach(v -> {
            snapshotVocabulary(v);
            snapshotTerms(v);
        });
        final Snapshot snapshot = new Snapshot(snapshotUri(vocabulary.getUri()), timestamp, vocabulary.getUri(),
                                               cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku);
        LOG.debug("Snapshot created: {}", snapshot);
        return snapshot;
    }

    private URI snapshotUri(URI source) {
        return URI.create(source.toString() + getSnapshotSuffix());
    }

    private Set<URI> resolveVocabulariesToSnapshot(Vocabulary root) {
        LOG.trace("Resolving vocabularies to snapshot, starting from {}.", root);
        final Set<URI> toSnapshot = vocabularyDao.getRelatedVocabularies(root, SKOS_CONCEPT_MATCH_RELATIONSHIPS);
        LOG.trace("Found {} vocabularies to snapshot: {}", toSnapshot.size(), toSnapshot);
        return toSnapshot;
    }

    private void snapshotVocabulary(URI vocabulary) {
        LOG.trace("Creating snapshot of vocabulary {} with identifier {}.", uriToString(vocabulary),
                  uriToString(snapshotUri(vocabulary)));
        em.createNativeQuery(snapshotVocabularyQuery).setParameter("vocabulary", vocabulary)
          .setParameter("suffix", getSnapshotSuffix())
          .setParameter("created", timestamp)
          .executeUpdate();
    }

    private void snapshotTerms(URI vocabulary) {
        em.createNativeQuery(snapshotTermQuery).setParameter("vocabulary", vocabulary)
          .setParameter("suffix", getSnapshotSuffix())
          .setParameter("created", timestamp)
          .executeUpdate();
    }
}
