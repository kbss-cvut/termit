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

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.exception.UnsupportedAssetOperationException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.persistence.relationship.VocabularyRelationshipResolver;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Objects;
import java.util.stream.Stream;

@Component
public class CascadingVocabularySnapshotRemover implements SnapshotRemover {

    private final VocabularyRelationshipResolver relationshipResolver;

    private final VocabularyDao vocabularyDao;

    private final EntityManager em;

    public CascadingVocabularySnapshotRemover(VocabularyRelationshipResolver relationshipResolver,
                                              VocabularyDao vocabularyDao, EntityManager em) {
        this.relationshipResolver = relationshipResolver;
        this.vocabularyDao = vocabularyDao;
        this.em = em;
    }

    @Override
    public void removeSnapshot(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        ensureCanRemove(snapshot);
        Stream.concat(
                Stream.of(snapshot.getUri()),
                relationshipResolver.getRelatedVocabularies(snapshot.getUri()).stream()
        ).forEach(snapshotUri -> {
            final URI ctx = resolveSnapshotContext(snapshotUri);
            clearContext(ctx);
        });
    }

    private void ensureCanRemove(Snapshot snapshot) {
        if (!Utils.emptyIfNull(snapshot.getTypes()).contains(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku)) {
            throw new UnsupportedAssetOperationException("Only removal of vocabulary snapshots is supported.");
        }
        final Vocabulary toRemove = vocabularyDao.getReference(snapshot.getUri());
        if (!toRemove.isSnapshot()) {
            throw new UnsupportedOperationException("Vocabulary " + toRemove + " is not a snapshot.");
        }
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
