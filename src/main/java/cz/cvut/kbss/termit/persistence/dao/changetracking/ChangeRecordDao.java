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
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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

    /**
     * Finds all change records to the specified asset.
     *
     * @param asset The changed asset
     * @return List of change records ordered by timestamp (descending)
     */
    public List<AbstractChangeRecord> findAll(HasIdentifier asset) {
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
