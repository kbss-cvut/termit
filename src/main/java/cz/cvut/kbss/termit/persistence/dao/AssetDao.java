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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static cz.cvut.kbss.termit.persistence.dao.util.SparqlPatterns.insertLanguagePattern;

@Repository
public class AssetDao {

    private static final Logger LOG = LoggerFactory.getLogger(AssetDao.class);

    private final EntityManager em;

    public AssetDao(EntityManager em) {
        this.em = em;
    }

    /**
     * Finds a page of most recently added/edited assets.
     *
     * @param pageSpec Specification of the page to load
     * @return Page of recently added/edited assets
     */
    public Page<RecentlyModifiedAsset> findLastEdited(Pageable pageSpec) {
        try {
            final List<AssetWithType> recentlyModifiedUniqueAssets = findUniqueLastModifiedEntities(pageSpec, null);
            return new PageImpl<>(recentlyModifiedUniqueAssets.stream()
                                                              .map(asset -> getRecentlyModifiedAsset(asset, null))
                                                              .filter(Optional::isPresent)
                                                              .map(Optional::get)
                                                              .collect(Collectors.toList()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private Optional<RecentlyModifiedAsset> getRecentlyModifiedAsset(AssetWithType asset, User author) {
        final Query query = em
                .createNativeQuery(
                        "SELECT DISTINCT ?entity ?label ?modified ?modifiedBy ?vocabulary ?type ?changeType WHERE {" +
                                "?x a ?change ;" +
                                "   a ?chType ;" +
                                "?hasModifiedEntity ?ent ;" +
                                "?hasEditor ?author ;" +
                                "?hasModificationDate ?modified ." +
                                "?ent ?hasLabel ?label . " +
                                insertVocabularyPattern(asset) +
                                "BIND (?ent as ?entity)" +
                                insertLanguagePattern() +
                                "BIND (?author as ?modifiedBy)" +
                                "FILTER (?chType != ?change)" +
                                "FILTER (?hasLabel in (?labelProperties))" +
                                "BIND (?assetType as ?type)" +
                                "BIND (IF(?chType = ?persist, ?persist, ?update) as ?changeType)" +
                                "FILTER (lang(?label) = ?language)" +
                                "} ORDER BY DESC(?modified)", "RecentlyModifiedAsset")
                .setParameter("ent", asset.uri)
                .setParameter("assetType", asset.type)
                .setParameter("change", URI.create(Vocabulary.s_c_zmena))
                .setParameter("labelProperties", Arrays.asList(URI.create(SKOS.PREF_LABEL), URI.create(DC.Terms.TITLE)))
                .setParameter("hasModifiedEntity", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                .setParameter("hasEditor", URI.create(Vocabulary.s_p_ma_editora))
                .setParameter("hasModificationDate", URI.create(Vocabulary.s_p_ma_datum_a_cas_modifikace))
                .setParameter("persist", URI.create(Vocabulary.s_c_vytvoreni_entity))
                .setParameter("update", URI.create(Vocabulary.s_c_uprava_entity))
                .setParameter("hasLanguage", URI.create(DC.Terms.LANGUAGE))
                .setParameter("language", config.getLanguage())
                .setMaxResults(1);
        setVocabularyRelatedParameter(asset, query);
        if (author != null) {
            query.setParameter("author", author);
        }
        try {
            final RecentlyModifiedAsset rec = (RecentlyModifiedAsset) query.getSingleResult();
            rec.setEditor(em.find(User.class, rec.getModifiedBy()));
            return Optional.of(rec);
        } catch (NoResultException e) {
            // TODO This should be a temporary until we are able to properly record asset deletion as a change
            LOG.warn("Skipping change record of deleted asset {}.", asset);
            return Optional.empty();
        }
    }

    private String insertVocabularyPattern(AssetWithType elem) {
        switch (elem.type.toString()) {
            case SKOS.CONCEPT:
                return "?ent ?isFromVocabulary ?vocabulary . ";
            case Vocabulary.s_c_dokument:
                return "?ent ?hasVocabulary ?vocabulary . ";
            case Vocabulary.s_c_soubor:
                return "?ent ?inDocument/?hasVocabulary ?vocabulary . ";
            default:
                return "BIND (?ent as ?vocabulary)";
        }
    }

    private void setVocabularyRelatedParameter(AssetWithType elem, Query query) {
        switch (elem.type.toString()) {
            case SKOS.CONCEPT:
                query.setParameter("isFromVocabulary", URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku));
                break;
            case Vocabulary.s_c_dokument:
                query.setParameter("hasVocabulary", URI.create(Vocabulary.s_p_ma_dokumentovy_slovnik));
                break;
            case Vocabulary.s_c_soubor:
                query.setParameter("inDocument", URI.create(Vocabulary.s_p_je_casti_dokumentu))
                     .setParameter("hasVocabulary", URI.create(Vocabulary.s_p_ma_dokumentovy_slovnik));
                break;
            default:
                break;
        }
    }

    List<AssetWithType> findUniqueLastModifiedEntities(Pageable pageSpec, User author) {
        final int offset = (int) pageSpec.getOffset();
        final Query query = em.createNativeQuery("SELECT DISTINCT ?entity ?type WHERE {" +
                                                         "?x ?hasModifiedEntity ?entity . " +
                                                         "?entity a ?type ." +
                                                         "{ SELECT ?x WHERE { " +
                                                         "?x a ?change ; " +
                                                         "?hasModificationDate ?modified ; " +
                                                         "?hasEditor ?author . " +
                                                         "} ORDER BY DESC(?modified) } " +
                                                         "FILTER (?type IN (?assetTypes))" +
                                                         "}")
                              .setParameter("change", URI.create(Vocabulary.s_c_zmena))
                              .setParameter("hasModificationDate",
                                            URI.create(Vocabulary.s_p_ma_datum_a_cas_modifikace))
                              .setParameter("hasModifiedEntity",
                                            URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                              .setParameter("hasEditor", URI.create(Vocabulary.s_p_ma_editora))
                              .setParameter("assetTypes",
                                            Arrays.asList(URI.create(SKOS.CONCEPT), URI.create(Vocabulary.s_c_slovnik),
                                                          URI.create(Vocabulary.s_c_dokument),
                                                          URI.create(Vocabulary.s_c_soubor)))
                              .setFirstResult(offset)
                              .setMaxResults(pageSpec.getPageSize());
        if (author != null) {
            query.setParameter("author", author);
        }
        return (List<AssetWithType>) query.getResultStream().map((row) -> {
            final Object[] r = (Object[]) row;
            assert r.length == 2;
            assert r[0] instanceof URI && r[1] instanceof URI;
            return new AssetWithType((URI) r[0], (URI) r[1]);
        }).collect(Collectors.toList());
    }

    /**
     * Finds a page of most recently added/edited assets by the specified author.
     *
     * @param author   Author of the modifications
     * @param pageSpec Specification of the page to load
     * @return List of assets recently added/edited by the specified user
     */
    public Page<RecentlyModifiedAsset> findLastEditedBy(User author, Pageable pageSpec) {
        Objects.requireNonNull(author);
        try {
            final List<AssetWithType> recentlyModifiedUniqueAssets = findUniqueLastModifiedEntities(pageSpec, author);
            return new PageImpl<>(recentlyModifiedUniqueAssets.stream()
                                                              .map(asset -> getRecentlyModifiedAsset(asset, author))
                                                              .filter(Optional::isPresent)
                                                              .map(Optional::get)
                                                              .collect(Collectors.toList()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private static final class AssetWithType {
        private final URI uri;
        private final URI type;

        private AssetWithType(URI uri, URI type) {
            this.uri = uri;
            this.type = type;
        }
    }
}
