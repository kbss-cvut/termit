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
package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Objects;

/**
 * Determines repository context into which change tracking records are stored.
 */
@Component
public class ChangeTrackingContextResolver {

    private final EntityManager em;

    private final String contextExtension;

    @Autowired
    public ChangeTrackingContextResolver(EntityManager em, Configuration config) {
        this.em = em;
        this.contextExtension = config.getChangetracking().getContext().getExtension();
    }

    /**
     * Resolves change tracking context of the specified changed asset.
     * <p>
     * In general, each vocabulary has its own change tracking context, so changes to it and all its terms are stored in
     * this context.
     *
     * @param changedAsset Asset for which change records will be generated
     * @return Identifier of the change tracking context of the specified asset
     */
    public URI resolveChangeTrackingContext(Asset<?> changedAsset) {
        Objects.requireNonNull(changedAsset);
        if (changedAsset instanceof Vocabulary) {
            return URI.create(changedAsset.getUri().toString().concat(contextExtension));
        } else if (changedAsset instanceof Term) {
            return URI.create(resolveTermVocabulary((Term) changedAsset).toString().concat(contextExtension));
        }
        return URI.create(changedAsset.getUri().toString().concat(contextExtension));
    }

    private URI resolveTermVocabulary(Term term) {
        if (term.getGlossary() != null) {
            return em.createNativeQuery("SELECT DISTINCT ?v WHERE { ?v ?hasGlossary ?glossary . }", URI.class)
                     .setParameter("hasGlossary", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_glosar))
                     .setParameter("glossary", term.getGlossary()).getSingleResult();
        } else {
            return em.createNativeQuery("SELECT DISTINCT ?v WHERE { ?t ?inVocabulary ?v . }", URI.class)
                     .setParameter("inVocabulary",
                                   URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                     .setParameter("t", term).getSingleResult();
        }
    }
}
