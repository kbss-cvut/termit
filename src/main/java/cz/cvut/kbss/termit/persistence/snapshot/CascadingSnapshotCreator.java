package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /**
     * SKOS relationships that define cascading of the snapshot operation.
     * <p>
     * I.e., vocabularies whose terms are connected via one of these relationships are included for snapshot creation.
     */
    private static final List<URI> CASCADE_RELATIONSHIPS = Stream.of(
            SKOS.BROAD_MATCH, SKOS.NARROW_MATCH, SKOS.EXACT_MATCH, SKOS.RELATED_MATCH
    ).map(URI::create).collect(Collectors.toList());

    private final EntityManager em;

    private final String snapshotVocabularyQuery;
    private final String snapshotTermQuery;

    public CascadingSnapshotCreator(Configuration configuration, EntityManager em) {
        super(configuration);
        this.em = em;
        this.snapshotVocabularyQuery = Utils.loadQuery("snapshot/vocabulary.ru");
        this.snapshotTermQuery = Utils.loadQuery("snapshot/term.ru");
    }

    @Override
    public Snapshot createSnapshot(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        LOG.info("Creating snapshot of {}.", vocabulary);
        final List<URI> toSnapshot = resolveVocabulariesToSnapshot(vocabulary);
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

    private List<URI> resolveVocabulariesToSnapshot(Vocabulary root) {
        LOG.trace("Resolving vocabularies to snapshot, starting from {}.", root);
        final List<URI> toSnapshot = new ArrayList<>();
        toSnapshot.add(root.getUri());
        // Using old-school iteration to prevent concurrent modification issues when adding items to list under iteration
        for (int i = 0; i < toSnapshot.size(); i++) {
            final Set<URI> toAdd = new HashSet<>(em.createNativeQuery("SELECT DISTINCT ?v WHERE {\n" +
                                                           "    ?t a ?term ;\n" +
                                                           "       ?inVocabulary ?vocabulary ;\n" +
                                                           "       ?y ?z .\n" +
                                                           "    ?z a ?term ;\n" +
                                                           "       ?inVocabulary ?v .\n" +
                                                           "    FILTER (?v != ?vocabulary)\n" +
                                                           "    FILTER (?y IN (?cascadingRelationships))\n" +
                                                           "}", URI.class)
                                                   .setParameter("term", URI.create(SKOS.CONCEPT))
                                                   .setParameter("inVocabulary",
                                                           URI.create(
                                                                   cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                                                   .setParameter("vocabulary", toSnapshot.get(i))
                                                   .setParameter("cascadingRelationships", CASCADE_RELATIONSHIPS)
                                                   .getResultList());
            // Explicitly imported vocabularies (it is likely it they were already added due to term relationships, but just
            // to be sure)
            toAdd.addAll(em.createNativeQuery("SELECT DISTINCT ?imported WHERE { ?v ?imports ?imported . }", URI.class)
                           .setParameter("imports", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                           .setParameter("v", toSnapshot.get(i)).getResultList());
            // Not very fast with lists, but we do not expect the list to be large
            toSnapshot.forEach(toAdd::remove);
            toSnapshot.addAll(toAdd);
        }
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
