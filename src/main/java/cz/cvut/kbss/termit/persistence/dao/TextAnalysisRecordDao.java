/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.TextAnalysisRecord;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

@Repository
public class TextAnalysisRecordDao {

    private final EntityManager em;

    @Autowired
    public TextAnalysisRecordDao(EntityManager em) {
        this.em = em;
    }

    public void persist(TextAnalysisRecord record) {
        try {
            em.persist(record);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Gets the latest {@link TextAnalysisRecord} for the specified Resource.
     *
     * @param resource Analyzed Resource
     * @return Latest analysis record, if it exists
     */
    public Optional<TextAnalysisRecord> findLatest(Resource resource) {
        Objects.requireNonNull(resource);
        try {
            return Optional.of(em.createNativeQuery("SELECT ?x WHERE { " +
                    "?x a ?type ;" +
                    "?hasResource ?resource ;" +
                    "?hasDateCreated ?dateCreated ." +
                    "} ORDER BY DESC(?dateCreated) LIMIT 1", TextAnalysisRecord.class)
                                 .setParameter("type", URI.create(
                                         "http://onto.fel.cvut.cz/ontologies/application/termit/pojem/záznam-o-textové-analýze"))
                                 .setParameter("hasResource", URI.create(
                                         "http://onto.fel.cvut.cz/ontologies/application/termit/pojem/má-analyzovaný-zdroj"))
                                 .setParameter("hasDateCreated", URI.create(Vocabulary.s_p_ma_datum_a_cas_vytvoreni))
                                 .setParameter("resource", resource.getUri()).getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
