package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class AssetDao {

    private final EntityManager em;

    private final Configuration.Persistence config;

    public AssetDao(EntityManager em, Configuration config) {
        this.em = em;
        this.config = config.getPersistence();
    }

    /**
     * Finds a page of most recently added/edited assets.
     *
     * @param pageSpec Specification of the page to load
     * @return Page of recently added/edited assets
     */
    public Page<RecentlyModifiedAsset> findLastEdited(Pageable pageSpec) {
        try {
            final List<URI> recentlyModifiedUniqueAssets = findUniqueLastModifiedEntities(pageSpec, null);
            return new PageImpl<>(recentlyModifiedUniqueAssets.stream()
                                                              .map(asset -> getRecentlyModifiedAsset(asset, null))
                                                              .collect(Collectors.toList()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private RecentlyModifiedAsset getRecentlyModifiedAsset(URI asset, User author) {
        final Query query = em
                .createNativeQuery(
                        "SELECT DISTINCT ?entity ?label ?modified ?modifiedBy ?vocabulary ?type ?changeType WHERE {" +
                                "?x a ?change ;" +
                                "   a ?chType ;" +
                                "?hasModifiedEntity ?ent ;" +
                                "?hasEditor ?author ;" +
                                "?hasModificationDate ?modified ." +
                                "?ent a ?type ;" +
                                "?hasLabel ?label ." +
                                "OPTIONAL { ?ent ?isFromVocabulary ?vocabulary . }" +
                                "BIND (?ent as ?entity)" +
                                "BIND (?author as ?modifiedBy)" +
                                "FILTER (?type in (?assetTypes))" +
                                "FILTER (?chType != ?change)" +
                                "FILTER (?hasLabel in (?labelProperties))" +
                                "BIND (IF(?chType = ?persist, ?persist, ?update) as ?changeType)" +
                                "FILTER (lang(?label) = ?language)" +
                                "} ORDER BY DESC(?modified)", "RecentlyModifiedAsset")
                .setParameter("assetTypes", Arrays.asList(URI.create(SKOS.CONCEPT), URI.create(Vocabulary.s_c_slovnik),
                                                          URI.create(Vocabulary.s_c_zdroj)))
                .setParameter("ent", asset)
                .setParameter("change", URI.create(Vocabulary.s_c_zmena))
                .setParameter("labelProperties", Arrays.asList(URI.create(SKOS.PREF_LABEL), URI.create(DC.Terms.TITLE)))
                .setParameter("hasModifiedEntity", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                .setParameter("hasEditor", URI.create(Vocabulary.s_p_ma_editora))
                .setParameter("hasModificationDate", URI.create(Vocabulary.s_p_ma_datum_a_cas_modifikace))
                .setParameter("isFromVocabulary", URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku))
                .setParameter("persist", URI.create(Vocabulary.s_c_vytvoreni_entity))
                .setParameter("update", URI.create(Vocabulary.s_c_uprava_entity))
                .setParameter("language", config.getLanguage()).setMaxResults(1);
        if (author != null) {
            query.setParameter("author", author);
        }
        final RecentlyModifiedAsset rec = (RecentlyModifiedAsset) query.getSingleResult();
        rec.setEditor(em.find(User.class, rec.getModifiedBy()));
        return rec;
    }

    List<URI> findUniqueLastModifiedEntities(Pageable pageSpec, User author) {
        final int offset = (int) pageSpec.getOffset();
        final Set<URI> result = new LinkedHashSet<>(pageSpec.getPageSize());
        int round = 0;
        List<Object> queryResult;
        do {
            final Query query = em.createNativeQuery("SELECT ?entity ?modified WHERE {" +
                                                             "?x a ?change ;" +
                                                             "?hasModificationDate ?modified ;" +
                                                             "?hasEditor ?author ;" +
                                                             "?hasModifiedEntity ?entity ." +
                                                             "} ORDER BY DESC(?modified)")
                                  .setParameter("change", URI.create(Vocabulary.s_c_zmena))
                                  .setParameter("hasModificationDate",
                                                URI.create(Vocabulary.s_p_ma_datum_a_cas_modifikace))
                                  .setParameter("hasModifiedEntity", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                                  .setParameter("hasEditor", URI.create(Vocabulary.s_p_ma_editora))
                                  .setFirstResult(offset + round * pageSpec.getPageSize())
                                  .setMaxResults(pageSpec.getPageSize());
            if (author != null) {
                query.setParameter("author", author);
            }
            queryResult = query.getResultList();
            queryResult.forEach((Object r) -> {
                final Object[] row = (Object[]) r;
                result.add((URI) row[0]);
            });
            round++;
        } while (result.size() < pageSpec.getPageSize() && !queryResult.isEmpty());
        return new ArrayList<>(result).subList(0, Math.min(pageSpec.getPageSize(), result.size()));
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
            final List<URI> recentlyModifiedUniqueAssets = findUniqueLastModifiedEntities(pageSpec, author);
            return new PageImpl<>(recentlyModifiedUniqueAssets.stream()
                                                              .map(asset -> getRecentlyModifiedAsset(asset, author))
                                                              .collect(Collectors.toList()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
