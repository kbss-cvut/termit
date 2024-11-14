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
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.filter.ChangeRecordFilterDto;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Repository
public class ChangeRecordDao {

    private final ChangeTrackingContextResolver contextResolver;

    private final EntityManager em;

    public ChangeRecordDao(ChangeTrackingContextResolver contextResolver, EntityManager em) {
        this.contextResolver = contextResolver;
        this.em = em;
    }

    /**
     * Persists the specified change record into the specified repository context.
     *
     * @param record       Record to save
     * @param changedAsset The changed asset
     */
    public void persist(AbstractChangeRecord record, Asset<?> changedAsset) {
        Objects.requireNonNull(record);
        final EntityDescriptor descriptor = new EntityDescriptor(
                contextResolver.resolveChangeTrackingContext(changedAsset));
        descriptor.addAttributeDescriptor(em.getMetamodel().entity(AbstractChangeRecord.class).getAttribute("author"),
                new EntityDescriptor());
        descriptor.setLanguage(null);
        try {
            em.persist(record, descriptor);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    public List<AbstractChangeRecord> findAll(Asset<?> asset, ChangeRecordFilterDto filterDto) {
        if (filterDto.isEmpty()) {
            // there is nothing to filter, simple query can be used
            return findAll(asset);
        }
        return findAllFiltered(contextResolver.resolveChangeTrackingContext(asset), filterDto, Optional.of(asset), Optional.empty(), Pageable.unpaged());
    }

    /**
     * @param changeContext the context of change records
     * @param filter filter parameters
     * @param asset if present, only changes of the asset will be returned
     * @param assetType if present, only changes related to this asset type will be returned.
     */
    public List<AbstractChangeRecord> findAllFiltered(URI changeContext, ChangeRecordFilterDto filter, Optional<Asset<?>> asset, Optional<URI> assetType, Pageable pageable) {
        TypedQuery<AbstractChangeRecord> query = em.createNativeQuery("""
                         SELECT DISTINCT ?record WHERE {
""" + /* Select anything from change context */ """
                            GRAPH ?changeContext {
                                ?record a ?changeRecord .
                            }
""" + /* The record should be a subclass of changeType ("zmena") and have timestamp and author */ """
                            ?changeRecord ?subClassOf+ ?changeType .
                            ?record ?hasChangedEntity ?asset ;
                                ?hasTime ?timestamp ;
                                ?hasAuthor ?author .
""" + /* Find an asset type if it is known (deleted assets does not have a type */ """
                            OPTIONAL {
                                ?asset a ?assetType .
                                OPTIONAL {
                                    ?asset a ?assetTypeValue
                                    BIND(true as ?isAssetType)
                                }
                            }
""" + /* filter assets without a type (deleted) or with a matching type */ """
                            FILTER(!BOUND(?assetType) || ?isAssetType)
""" + /* Get author's name */ """
                            ?author ?hasFirstName ?firstName ;
                                ?hasLastName ?lastName .
                            BIND(CONCAT(?firstName, " ", ?lastName) as ?authorFullName)
""" + /* When its update record, there will be a changed attribute */ """
                            OPTIONAL {
                               ?record ?hasChangedAttribute ?attribute .
                               ?attribute ?hasRdfsLabel ?changedAttributeLabel .
                            }
""" + /* Get asset's name (but the asset might have been already deleted) */ """
                            OPTIONAL {
                                ?asset ?hasLabel ?assetPrefLabel .
                                BIND(?assetPrefLabel as ?finalAssetLabel)
                            }
                            OPTIONAL {
                                ?asset ?hasRdfsLabel ?assetRdfsLabel .
                                BIND(?assetRdfsLabel as ?finalAssetLabel)
                            }
""" + /* then try to get the label from (delete) record */ """
                            OPTIONAL {
                               ?record ?hasRdfsLabel ?recordRdfsLabel .
                               BIND(?recordRdfsLabel as ?finalAssetLabel)
                            }
""" + /* When label is still not bound, the term was probably deleted, find the delete record and get the label from it */ """
                            OPTIONAL {
                                ?deleteRecord a ?deleteRecordType;
                                    ?hasChangedEntity ?asset;
                                    ?hasRdfsLabel ?deleteRecordLabel .
                                BIND(?deleteRecordLabel as ?finalAssetLabel)
                            }
                            BIND(?assetLabelValue as ?assetLabel)
                            BIND(?authorNameValue as ?authorName)
                            BIND(?attributeNameValue as ?changedAttributeName)
                            FILTER (!BOUND(?assetLabel) || CONTAINS(LCASE(?finalAssetLabel), LCASE(?assetLabel)))
                            FILTER (!BOUND(?authorName) || CONTAINS(LCASE(?authorFullName), LCASE(?authorName)))
                            FILTER (!BOUND(?changedAttributeName) || CONTAINS(LCASE(?changedAttributeLabel), LCASE(?changedAttributeName)))
                         } ORDER BY DESC(?timestamp) ?attribute
                         """, AbstractChangeRecord.class)
                                                   .setParameter("changeContext", changeContext)
                                                   .setParameter("subClassOf", URI.create(RDFS.SUB_CLASS_OF))
                                                    .setParameter("changeType", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_c_zmena))
                                                   .setParameter("hasChangedEntity", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_zmenenou_entitu))
                                                   .setParameter("hasTime", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_datum_a_cas_modifikace))
                                                   .setParameter("hasAuthor", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_editora)) // record has author
                                                   .setParameter("hasFirstName", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_krestni_jmeno))
                                                   .setParameter("hasLastName", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_prijmeni))
                                                   // Optional - update change record
                                                   .setParameter("hasChangedAttribute", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_zmeneny_atribut))
                                                   .setParameter("hasRdfsLabel", URI.create(RDFS.LABEL))
                                                   // Optional -
                                                   .setParameter("hasLabel", URI.create(SKOS.PREF_LABEL))

                                                   // Optional asset label
                                                   .setParameter("deleteRecordType", URI.create(Vocabulary.s_c_smazani_entity));

        if(asset.isPresent() && asset.get().getUri() != null) {
            query = query.setParameter("asset", asset.get().getUri());
        } else if (assetType.isPresent()) {
            query = query.setParameter("assetTypeValue", assetType.get());
        }
        

        if(!Utils.isBlank(filter.getAssetLabel())) {
            query = query.setParameter("assetLabelValue", filter.getAssetLabel().trim());
        }
        if (!Utils.isBlank(filter.getAuthorName())) {
            query = query.setParameter("authorNameValue", filter.getAuthorName().trim());
        }
        if (filter.getChangeType() != null) {
            query = query.setParameter("changeRecord", filter.getChangeType());
        }
        if (!Utils.isBlank(filter.getChangedAttributeName())) {
            query = query.setParameter("attributeNameValue", filter.getChangedAttributeName().trim());
        }

        if(pageable.isUnpaged()) {
            return query.getResultList();
        }

        return query.setFirstResult((int) pageable.getOffset())
                    .setMaxResults(pageable.getPageSize()).getResultList();
    }

    /**
     * Finds all change records to the specified asset.
     *
     * @param asset The changed asset
     * @return List of change records ordered by timestamp (descending)
     */
    public List<AbstractChangeRecord> findAll(Asset<?> asset) {
        Objects.requireNonNull(asset);
        try {
            final Descriptor descriptor = new EntityDescriptor();
            descriptor.setLanguage(null);
            return em.createNativeQuery("SELECT ?r WHERE {" +
                             "?r a ?changeRecord ;" +
                             "?relatesTo ?asset ;" +
                             "?hasTime ?timestamp ." +
                             "OPTIONAL { ?r ?hasChangedAttribute ?attribute . }" +
                             "} ORDER BY DESC(?timestamp) ?attribute", AbstractChangeRecord.class)
                     .setParameter("changeRecord", URI.create(Vocabulary.s_c_zmena))
                     .setParameter("relatesTo", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                     .setParameter("hasChangedAttribute", URI.create(Vocabulary.s_p_ma_zmeneny_atribut))
                     .setParameter("hasTime", URI.create(Vocabulary.s_p_ma_datum_a_cas_modifikace))
                     .setParameter("asset", asset.getUri()).setDescriptor(descriptor).getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Gets a set of authors of the specified asset. That is, this method retrieves authors of persist change records
     * associated with the specified asset.
     *
     * @param asset Asset whose authors to get
     * @return A set (possibly empty) of users
     */
    public Set<User> getAuthors(HasIdentifier asset) {
        Objects.requireNonNull(asset);
        try {
            return new HashSet<>(em.createNativeQuery("SELECT ?author WHERE {" +
                                           "?x a ?persistRecord ;" +
                                           "?hasChangedEntity ?asset ;" +
                                           "?hasAuthor ?author }", User.class)
                                   .setParameter("persistRecord", URI.create(Vocabulary.s_c_vytvoreni_entity))
                                   .setParameter("hasChangedEntity", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                                   .setParameter("asset", asset.getUri())
                                   .setParameter("hasAuthor", URI.create(Vocabulary.s_p_ma_editora))
                                   .getResultList());
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }
}
