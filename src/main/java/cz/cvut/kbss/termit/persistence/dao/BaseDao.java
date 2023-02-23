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

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.asset.provenance.ModifiesData;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.util.EntityToOwlClassMapper;
import cz.cvut.kbss.termit.model.util.HasIdentifier;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Base implementation of the generic DAO API.
 */
public abstract class BaseDao<T extends HasIdentifier> implements GenericDao<T> {

    protected final Class<T> type;
    protected final URI typeUri;

    protected final EntityManager em;

    protected BaseDao(Class<T> type, EntityManager em) {
        this.type = type;
        this.typeUri = URI.create(EntityToOwlClassMapper.getOwlClassForEntity(type));
        this.em = em;
    }

    @Override
    public List<T> findAll() {
        try {
            return em.createNativeQuery("SELECT DISTINCT ?x WHERE { ?x a ?type . }", type).setParameter("type", typeUri)
                    .setDescriptor(getDescriptor())
                     .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public Optional<T> find(URI id) {
        Objects.requireNonNull(id);
        try {
            return Optional.ofNullable(em.find(type, id, getDescriptor()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public Optional<T> getReference(URI id) {
        Objects.requireNonNull(id);
        try {
            return Optional.ofNullable(em.getReference(type, id, getDescriptor()));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @ModifiesData
    @Override
    public void persist(T entity) {
        Objects.requireNonNull(entity);
        try {
            em.persist(entity, getDescriptor());
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @ModifiesData
    @Override
    public void persist(Collection<T> entities) {
        Objects.requireNonNull(entities);
        try {
            entities.forEach(em::persist);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @ModifiesData
    @Override
    public T update(T entity) {
        Objects.requireNonNull(entity);
        try {
            return em.merge(entity, getDescriptor());
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @ModifiesData
    @Override
    public void remove(T entity) {
        Objects.requireNonNull(entity);
        Objects.requireNonNull(entity.getUri());
        try {
            final Optional<T> reference = getReference(entity.getUri());
            reference.ifPresent(em::remove);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public boolean exists(URI id) {
        Objects.requireNonNull(id);
        try {
            return em.createNativeQuery("ASK { ?x a ?type . }", Boolean.class).setParameter("x", id)
                     .setParameter("type", typeUri).getSingleResult();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    protected Descriptor getDescriptor() {
        return new EntityDescriptor();
    }
}
