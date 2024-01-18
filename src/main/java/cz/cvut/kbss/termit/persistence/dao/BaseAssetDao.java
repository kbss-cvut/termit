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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.RecentlyCommentedAsset;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.util.Configuration.Persistence;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base DAO implementation for assets managed by the application.
 *
 * @param <T> Type of the asset
 */
public abstract class BaseAssetDao<T extends Asset<?>> extends BaseDao<T> {

    protected final Persistence config;

    protected final DescriptorFactory descriptorFactory;

    BaseAssetDao(Class<T> type, EntityManager em, Persistence config, DescriptorFactory descriptorFactory) {
        super(type, em);
        this.config = config;
        this.descriptorFactory = descriptorFactory;
    }

    /**
     * Finds unique last commented assets.
     *
     * @param pageSpec Specification of the page to return
     * @return Page with commented assets
     */
    public Page<RecentlyCommentedAsset> findLastCommented(Pageable pageSpec) {
        try {
            return new PageImpl<>((List<RecentlyCommentedAsset>) em
                    .createNativeQuery(
                            "SELECT DISTINCT ?entity ?label ?lastCommentUri ?myLastCommentUri ?vocabulary ?type"
                                    + " WHERE { ?lastCommentUri a ?commentType ;"
                                    + "           ?hasEntity ?entity ."
                                    + "  ?entity ?hasLabel ?label ."
                                    + "  OPTIONAL { ?lastCommentUri ?hasModifiedTime ?modified . }"
                                    + "  OPTIONAL { ?lastCommentUri ?hasCreatedTime ?created . }"
                                    + "  OPTIONAL { ?entity ?inVocabulary ?vocabulary . }"
                                    + "  BIND(COALESCE(?modified,?created) AS ?lastCommented) "
                                    + "  BIND(?cls as ?type) "
                                    + "  { SELECT (MAX(?lastCommented2) AS ?max) {"
                                    + "           ?comment2 ?hasEntity ?entity ."
                                    + "           OPTIONAL { ?comment2 ?hasModifiedTime ?modified2 . }"
                                    + "           OPTIONAL { ?comment2 ?hasCreatedTime ?created2 . }"
                                    + "           BIND(COALESCE(?modified2,?created2) AS ?lastCommented2) "
                                    + "        } GROUP BY ?entity"
                                    + "  }"
                                    + "  FILTER (?lastCommented = ?max)"
                                    + "  FILTER (lang(?label) = ?language)"
                                    + "} ORDER BY DESC(?lastCommented) ", "RecentlyCommentedAsset")
                    .setParameter("cls", typeUri)
                    .setParameter("commentType", URI.create(Vocabulary.s_c_Comment))
                    .setParameter("hasEntity", URI.create(Vocabulary.s_p_topic))
                    .setParameter("hasLabel", labelProperty())
                    .setParameter("inVocabulary", URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku))
                    .setParameter("hasModifiedTime", URI.create(Vocabulary.s_p_ma_datum_a_cas_posledni_modifikace))
                    .setParameter("hasCreatedTime", URI.create(Vocabulary.s_p_ma_datum_a_cas_vytvoreni))
                    .setParameter("language", config.getLanguage())
                    .setFirstResult((int) pageSpec.getOffset())
                    .setMaxResults(pageSpec.getPageSize()).getResultStream()
                    .map(r -> {
                             final RecentlyCommentedAsset a = (RecentlyCommentedAsset) r;
                             return a.setLastComment(em.find(Comment.class, a.getLastCommentUri()));
                         }
                    ).collect(Collectors.toList()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Finds unique last commented entities.
     *
     * @param pageSpec Specification of the page to return
     * @return list
     */
    public Page<RecentlyCommentedAsset> findLastCommentedInReaction(User author, Pageable pageSpec) {
        try {
            return new PageImpl<>((List<RecentlyCommentedAsset>) em
                    .createNativeQuery("SELECT DISTINCT ?entity ?label ?lastCommentUri ?myLastCommentUri ?type"
                                               + " WHERE { ?lastCommentUri a ?commentType ;"
                                               + "           ?hasEntity ?entity ."
                                               + "  ?entity ?hasLabel ?label ."
                                               + "         ?myLastCommentUri ?hasEntity ?entity ;"
                                               + "                           ?hasAuthor ?author . "
                                               + "         OPTIONAL { ?myLastCommentUri ?hasModifiedTime ?modifiedByMe . } "
                                               + "         OPTIONAL { ?myLastCommentUri ?hasCreatedByMe  ?createdByMe . } "
                                               + "         BIND(COALESCE(?modifiedByMe,?createdByMe) AS ?lastCommentedByMe) "
                                               + " { SELECT (MAX(?lastCommentedByMe2) AS ?maxByMe) {"
                                               + "         ?commentByMe ?hasEntity ?entity ; "
                                               + "                      ?hasAuthor ?author . "
                                               + "          OPTIONAL { ?commentByMe ?hasModifiedTime ?modifiedByMe2 . } "
                                               + "          OPTIONAL { ?commentByMe ?hasCreatedTime ?createdByMe2 . } "
                                               + "          BIND(COALESCE(?modifiedByMe2,?createdByMe2) AS ?lastCommentedByMe2) "
                                               + "        } GROUP BY ?entity "
                                               + "  }"
                                               + "  FILTER (?lastCommentedByMe = ?maxByMe )"
                                               + "  FILTER(?myLastCommentUri != ?lastCommentUri)"
                                               + "  FILTER (lang(?label) = ?language)"
                                               + "  OPTIONAL { ?lastCommentUri ?hasModifiedTime ?modified . }"
                                               + "  OPTIONAL { ?lastCommentUri ?hasCreatedTime ?created . }"
                                               + "  BIND(COALESCE(?modified,?created) AS ?lastCommented) "
                                               + "  BIND(?cls as ?type) "
                                               + "  { SELECT (MAX(?lastCommented2) AS ?max) {"
                                               + "           ?comment2 ?hasEntity ?entity ."
                                               + "           OPTIONAL { ?comment2 ?hasModifiedTime ?modified2 . }"
                                               + "           OPTIONAL { ?comment2 ?hasCreatedTime ?created2 . }"
                                               + "           BIND(COALESCE(?modified2,?created2) AS ?lastCommented2) "
                                               + "        } GROUP BY ?entity"
                                               + "  }"
                                               + "  FILTER (?lastCommented = ?max )"
                                               + "} ORDER BY DESC(?lastCommented) ", "RecentlyCommentedAsset")
                    .setParameter("cls", typeUri)
                    .setParameter("commentType", URI.create(Vocabulary.s_c_Comment))
                    .setParameter("hasEntity", URI.create(Vocabulary.s_p_topic))
                    .setParameter("hasLabel", labelProperty())
                    .setParameter("hasModifiedTime", URI.create(Vocabulary.s_p_ma_datum_a_cas_posledni_modifikace))
                    .setParameter("hasCreatedTime", URI.create(Vocabulary.s_p_ma_datum_a_cas_vytvoreni))
                    .setParameter("hasAuthor", URI.create(Vocabulary.s_p_sioc_has_creator))
                    .setParameter("language", config.getLanguage())
                    .setParameter("author", author)
                    .setMaxResults(pageSpec.getPageSize()).setFirstResult((int) pageSpec.getOffset())
                    .getResultStream()
                    .map(r -> {
                             final RecentlyCommentedAsset a = (RecentlyCommentedAsset) r;
                             return a.setLastComment(em.find(Comment.class, a.getLastCommentUri()))
                                     .setMyLastComment(em.find(Comment.class, a.getMyLastCommentUri()));
                         }
                    ).collect(Collectors.toList()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Finds unique last commented entities.
     *
     * @param pageSpec Specification of the page to return
     * @return list
     */
    public Page<RecentlyCommentedAsset> findMyLastCommented(User author, Pageable pageSpec) {
        try {
            return new PageImpl<>((List<RecentlyCommentedAsset>) em
                    .createNativeQuery("SELECT DISTINCT ?entity ?label ?lastCommentUri ?myLastCommentUri ?type"
                                               + " WHERE { ?lastCommentUri a ?commentType ;"
                                               + "           ?hasEntity ?entity ."
                                               + "  ?entity ?hasLabel ?label ."
                                               + "        FILTER EXISTS { ?x ?hasModifiedEntity ?entity ;"
                                               + "           ?hasEditor ?author .}"
                                               + "  OPTIONAL { ?lastCommentUri ?hasModifiedTime ?modified . }"
                                               + "  OPTIONAL { ?lastCommentUri ?hasCreatedTime ?created . }"
                                               + "  BIND(COALESCE(?modified,?created) AS ?lastCommented) "
                                               + "  BIND(?cls as ?type) "
                                               + "  { SELECT (MAX(?lastCommented2) AS ?max) {"
                                               + "           ?comment2 ?hasEntity ?entity ."
                                               + "           OPTIONAL { ?comment2 ?hasModifiedTime ?modified2 . }"
                                               + "           OPTIONAL { ?comment2 ?hasCreatedTime ?created2 . }"
                                               + "           BIND(COALESCE(?modified2,?created2) AS ?lastCommented2) "
                                               + "        } GROUP BY ?entity"
                                               + "  }"
                                               + "  FILTER (?lastCommented = ?max )"
                                               + "  FILTER (lang(?label) = ?language)"
                                               + "} ORDER BY DESC(?lastCommented) ", "RecentlyCommentedAsset")
                    .setParameter("cls", typeUri)
                    .setParameter("commentType", URI.create(Vocabulary.s_c_Comment))
                    .setParameter("hasEntity", URI.create(Vocabulary.s_p_topic))
                    .setParameter("hasLabel", labelProperty())
                    .setParameter("hasEditor", URI.create(Vocabulary.s_p_ma_editora))
                    .setParameter("hasModifiedEntity", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                    .setParameter("author", author)
                    .setParameter("hasModifiedTime", URI.create(Vocabulary.s_p_ma_datum_a_cas_posledni_modifikace))
                    .setParameter("hasCreatedTime", URI.create(Vocabulary.s_p_ma_datum_a_cas_vytvoreni))
                    .setParameter("language", config.getLanguage())
                    .setMaxResults(pageSpec.getPageSize()).setFirstResult((int) pageSpec.getOffset())
                    .getResultStream()
                    .map(r -> {
                             final RecentlyCommentedAsset a = (RecentlyCommentedAsset) r;
                             return a.setLastComment(em.find(Comment.class, a.getLastCommentUri()));
                         }
                    ).collect(Collectors.toList()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Identifier of an RDF property representing this asset's label.
     *
     * @return RDF property identifier
     */
    protected abstract URI labelProperty();
}
