package cz.cvut.kbss.termit.persistence.dao.workspace;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.PersistenceUtils;
import cz.cvut.kbss.termit.persistence.dao.AssetDao;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

public abstract class WorkspaceBasedAssetDao<T extends Asset> extends AssetDao<T> {

    protected final PersistenceUtils persistenceUtils;

    protected WorkspaceBasedAssetDao(Class<T> type, EntityManager em, Configuration config,
                                     DescriptorFactory descriptorFactory,
                                     PersistenceUtils persistenceUtils) {
        super(type, em, config, descriptorFactory);
        this.persistenceUtils = persistenceUtils;
    }

    @Override
    public List<RecentlyModifiedAsset> findLastEdited(int limit) {
        return findLastEditedBy(null, limit);
    }

    @Override
    public List<RecentlyModifiedAsset> findLastEditedBy(User author, int limit) {
        try {
            final List<URI> recentlyModifiedUniqueAssets = findUniqueLastModifiedEntitiesBy(author, limit);
            final List<RecentlyModifiedAsset> modified = recentlyModifiedUniqueAssets.stream().map(asset -> {
                final Query q = em
                        .createNativeQuery(
                                "SELECT DISTINCT ?entity ?label ?modified ?modifiedBy ?vocabulary ?type ?changeType WHERE {" +
                                        "?x a ?change ." +  // This is inferred, so potentially in the inference context
                                        "GRAPH ?g {" +
                                        "?x a ?chType ;" +
                                        "?hasModifiedEntity ?ent ;" +
                                        "?hasEditor ?author ;" +
                                        "?hasModificationDate ?modified . " +
                                        "}" +
                                        "?ent ?hasLabel ?label ." +
                                        "OPTIONAL { ?ent ?isFromVocabulary ?vocabulary . }" +
                                        "BIND (?cls as ?type)" +
                                        "BIND (?ent as ?entity)" +
                                        "BIND (?author as ?modifiedBy)" +
                                        "FILTER (?chType != ?change)" +
                                        "BIND (IF(?chType = ?persist, ?persist, ?update) as ?changeType)" +
                                        "FILTER (lang(?label) = ?language)" +
                                        "FILTER (?g IN (?changeTrackingContexts)) " +
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
                        .setParameter("changeTrackingContexts",
                                persistenceUtils.getCurrentWorkspaceChangeTrackingContexts())
                        .setParameter("language", config.get(ConfigParam.LANGUAGE)).setMaxResults(1);
                if (author != null) {
                    q.setParameter("author", author);
                }
                return (RecentlyModifiedAsset) q.getSingleResult();
            }).collect(Collectors.toList());
            loadLastEditors(modified);
            return modified;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    List<URI> findUniqueLastModifiedEntitiesBy(User author, int limit) {
        final TypedQuery<URI> q = em.createNativeQuery("SELECT DISTINCT ?entity WHERE {" +
                "?x a ?change ." +  // This is inferred, so potentially in the inference context
                "GRAPH ?g {" +
                "?x ?hasModificationDate ?modified ;" +
                "?hasEditor ?author ;" +
                "?hasModifiedEntity ?entity . }" +
                "?entity a ?type ." +
                "FILTER (?g IN (?changeTrackingContexts)) " +
                "} ORDER BY DESC(?modified)", URI.class).setParameter("change", URI.create(Vocabulary.s_c_zmena))
                                    .setParameter("hasModificationDate",
                                            URI.create(Vocabulary.s_p_ma_datum_a_cas_modifikace))
                                    .setParameter("hasEditor", URI.create(Vocabulary.s_p_ma_editora))
                                    .setParameter("hasModifiedEntity", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                                    .setParameter("changeTrackingContexts",
                                            persistenceUtils.getCurrentWorkspaceChangeTrackingContexts())
                                    .setParameter("type", typeUri).setMaxResults(limit);
        if (author != null) {
            q.setParameter("author", author);
        }
        return q.getResultList();
    }
}
