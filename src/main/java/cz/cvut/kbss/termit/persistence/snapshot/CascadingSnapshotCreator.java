/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.relationship.VocabularyRelationshipResolver;
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

    private final EntityManager em;

    private final VocabularyRelationshipResolver relationshipResolver;

    private final String snapshotVocabularyQuery;
    private final String snapshotTermQuery;

    public CascadingSnapshotCreator(Configuration configuration, EntityManager em,
                                    VocabularyRelationshipResolver relationshipResolver) {
        super(configuration);
        this.em = em;
        this.relationshipResolver = relationshipResolver;
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
        final Set<URI> toSnapshot = Stream.concat(Stream.of(root.getUri()),
                                                  relationshipResolver.getRelatedVocabularies(root.getUri()).stream())
                                          .collect(Collectors.toSet());
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
