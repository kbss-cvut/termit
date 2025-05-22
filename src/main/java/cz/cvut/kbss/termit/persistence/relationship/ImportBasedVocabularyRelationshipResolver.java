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
package cz.cvut.kbss.termit.persistence.relationship;

import cz.cvut.kbss.jopa.model.EntityManager;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * Resolves related vocabularies based on explicit imports.
 */
@Component
class ImportBasedVocabularyRelationshipResolver implements VocabularyRelationshipResolver {

    private final EntityManager em;

    public ImportBasedVocabularyRelationshipResolver(EntityManager em) {
        this.em = em;
    }

    @Transactional(readOnly = true)
    @Nonnull
    @Override
    public Set<URI> getRelatedVocabularies(@Nonnull URI vocabulary) {
        return new HashSet<>(
                em.createNativeQuery("SELECT DISTINCT ?imported WHERE { ?v ?imports ?imported . }", URI.class)
                  .setParameter("imports",
                                URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                  .setParameter("v", vocabulary).getResultList());
    }
}
