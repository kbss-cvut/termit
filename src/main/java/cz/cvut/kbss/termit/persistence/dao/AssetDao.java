/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Base DAO implementation for assets managed by the application.
 *
 * @param <T> Type of the asset
 */
public abstract class AssetDao<T extends Asset<?>> extends BaseDao<T> {

    protected final Configuration config;

    AssetDao(Class<T> type, EntityManager em, Configuration config) {
        super(type, em);
        this.config = config;
    }

    /**
     * Finds the specified number of most recently added/edited assets.
     *
     * @param limit Number of assets to load
     * @return List of recently added/edited assets
     */
    public List<RecentlyModifiedAsset> findLastEdited(int limit) {
        try {
            final List<URI> recentlyModifiedUniqueAssets = findUniqueLastModifiedEntities(limit);
            final List<RecentlyModifiedAsset> modified = recentlyModifiedUniqueAssets.stream().map(asset -> {
                        return (RecentlyModifiedAsset) em
                                .createNativeQuery(
                                        "SELECT DISTINCT ?entity ?label ?modified ?modifiedBy ?vocabulary ?type ?changeType WHERE {" +
                                                "?x a ?change ;" +
                                                "   a ?chType ;" +
                                                "?hasModifiedEntity ?ent ;" +
                                                "?hasEditor ?modifiedBy ;" +
                                                "?hasModificationDate ?modified ." +
                                                "?ent ?hasLabel ?label ." +
                                                "OPTIONAL { ?ent ?isFromVocabulary ?vocabulary . }" +
                                                "BIND (?cls as ?type)" +
                                                "BIND (?ent as ?entity)" +
                                                "FILTER (?chType != ?change)" +
                                                "BIND (IF(?chType = ?persist, ?persist, ?update) as ?changeType)" +
                                                "FILTER (lang(?label) = ?language)" +
                                                "} ORDER BY DESC(?modified)", "RecentlyModifiedAsset")
                                .setParameter("cls", typeUri)
                                .setParameter("ent", asset)
                                .setParameter("change", URI.create(Vocabulary.s_c_zmena))
                                .setParameter("hasLabel", labelProperty())
                                .setParameter("hasModifiedEntity", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                                .setParameter("hasEditor", URI.create(Vocabulary.s_p_ma_editora))
                                .setParameter("hasModificationDate", URI.create(Vocabulary.s_p_ma_datum_a_cas_modifikace))
                                .setParameter("isFromVocabulary", URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku))
                                .setParameter("persist", URI.create(Vocabulary.s_c_vytvoreni_entity))
                                .setParameter("update", URI.create(Vocabulary.s_c_uprava_entity))
                                .setParameter("language", config.get(ConfigParam.LANGUAGE)).setMaxResults(1).getSingleResult();
                    }
            ).collect(Collectors.toList());
            loadLastEditors(modified);
            return modified;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    List<URI> findUniqueLastModifiedEntities(int limit) {
        return em.createNativeQuery("SELECT DISTINCT ?entity WHERE {" +
                "?x a ?change ;" +
                "?hasModificationDate ?modified ;" +
                "?hasModifiedEntity ?entity ." +
                "?entity a ?type ." +
                "} ORDER BY DESC(?modified)", URI.class).setParameter("change", URI.create(Vocabulary.s_c_zmena))
                 .setParameter("hasModificationDate", URI.create(Vocabulary.s_p_ma_datum_a_cas_modifikace))
                 .setParameter("hasModifiedEntity", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                 .setParameter("type", typeUri).setMaxResults(limit).getResultList();
    }

    private void loadLastEditors(List<RecentlyModifiedAsset> modified) {
        modified.forEach(m -> m.setEditor(em.find(User.class, m.getModifiedBy())));
    }

    /**
     * Finds the specified number of most recently added/edited assets by the specified author.
     *
     * @param author Author of the modifications
     * @param limit  Number of assets to load
     * @return List of assets recently added/edited by the specified user
     */
    public List<RecentlyModifiedAsset> findLastEditedBy(User author, int limit) {
        Objects.requireNonNull(author);
        try {
            final List<URI> recentlyModifiedUniqueAssets = findUniqueLastModifiedEntitiesBy(author, limit);
            return recentlyModifiedUniqueAssets.stream().map(asset -> {
                        final RecentlyModifiedAsset rec = (RecentlyModifiedAsset) em
                                .createNativeQuery(
                                        "SELECT DISTINCT ?entity ?label ?modified ?modifiedBy ?vocabulary ?type ?changeType WHERE {" +
                                                "?x a ?change ;" +
                                                "   a ?chType ;" +
                                                "?hasModifiedEntity ?ent ;" +
                                                "?hasEditor ?author ;" +
                                                "?hasModificationDate ?modified ." +
                                                "?ent ?hasLabel ?label ." +
                                                "OPTIONAL { ?ent ?isFromVocabulary ?vocabulary . }" +
                                                "BIND (?cls as ?type)" +
                                                "BIND (?ent as ?entity)" +
                                                "BIND (?author as ?modifiedBy)" +
                                                "FILTER (?chType != ?change)" +
                                                "BIND (IF(?chType = ?persist, ?persist, ?update) as ?changeType)" +
                                                "FILTER (lang(?label) = ?language)" +
                                                "} ORDER BY DESC(?modified)", "RecentlyModifiedAsset")
                                .setParameter("cls", typeUri)
                                .setParameter("ent", asset)
                                .setParameter("change", URI.create(Vocabulary.s_c_zmena))
                                .setParameter("hasLabel", labelProperty())
                                .setParameter("hasModifiedEntity", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                                .setParameter("hasEditor", URI.create(Vocabulary.s_p_ma_editora))
                                .setParameter("author", author)
                                .setParameter("hasModificationDate", URI.create(Vocabulary.s_p_ma_datum_a_cas_modifikace))
                                .setParameter("isFromVocabulary", URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku))
                                .setParameter("persist", URI.create(Vocabulary.s_c_vytvoreni_entity))
                                .setParameter("update", URI.create(Vocabulary.s_c_uprava_entity))
                                .setParameter("language", config.get(ConfigParam.LANGUAGE)).setMaxResults(1).getSingleResult();
                        rec.setEditor(author);
                        return rec;
                    }
            ).collect(Collectors.toList());
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    List<URI> findUniqueLastModifiedEntitiesBy(User author, int limit) {
        return em.createNativeQuery("SELECT DISTINCT ?entity WHERE {" +
                "?x a ?change ;" +
                "?hasModificationDate ?modified ;" +
                "?hasEditor ?author ;" +
                "?hasModifiedEntity ?entity ." +
                "?entity a ?type ." +
                "} ORDER BY DESC(?modified)", URI.class).setParameter("change", URI.create(Vocabulary.s_c_zmena))
                 .setParameter("hasModificationDate", URI.create(Vocabulary.s_p_ma_datum_a_cas_modifikace))
                 .setParameter("hasEditor", URI.create(Vocabulary.s_p_ma_editora))
                 .setParameter("author", author)
                 .setParameter("hasModifiedEntity", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                 .setParameter("type", typeUri).setMaxResults(limit).getResultList();
    }

    /**
     * Identifier of an RDF property representing this assets label.
     *
     * @return RDF property identifier
     */
    protected abstract URI labelProperty();
}
