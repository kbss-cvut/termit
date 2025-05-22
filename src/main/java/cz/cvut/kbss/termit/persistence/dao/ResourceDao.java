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

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.asset.provenance.ModifiesData;
import cz.cvut.kbss.termit.asset.provenance.SupportsLastModification;
import cz.cvut.kbss.termit.event.AssetUpdateEvent;
import cz.cvut.kbss.termit.event.RefreshLastModifiedEvent;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Repository
public class ResourceDao extends BaseAssetDao<Resource> implements SupportsLastModification {

    private static final URI LABEL_PROPERTY = URI.create(DC.Terms.TITLE);

    private volatile long lastModified;

    public ResourceDao(EntityManager em, Configuration config, DescriptorFactory descriptorFactory) {
        super(Resource.class, em, config.getPersistence(), descriptorFactory);
        refreshLastModified();
    }

    @Override
    protected URI labelProperty() {
        return LABEL_PROPERTY;
    }

    /**
     * Ensures that the specified instance is detached from the current persistence context.
     *
     * @param resource Instance to detach
     */
    public void detach(Resource resource) {
        em.detach(resource);
    }

    /**
     * Persists the specified Resource into the context of the specified Vocabulary.
     *
     * @param resource   Resource to persist
     * @param vocabulary Vocabulary providing context
     * @throws IllegalArgumentException When the specified resource is neither a {@code Document} nor a {@code File}
     */
    @ModifiesData
    public void persist(Resource resource, cz.cvut.kbss.termit.model.Vocabulary vocabulary) {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(vocabulary);
        final Descriptor descriptor = createDescriptor(resource, vocabulary.getUri());
        try {
            em.persist(resource, descriptor);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
        refreshLastModified();
    }

    private Descriptor createDescriptor(Resource resource, URI vocabularyUri) {
        final Descriptor descriptor;
        if (resource instanceof Document) {
            descriptor = descriptorFactory.documentDescriptor(vocabularyUri);
        } else if (resource instanceof File) {
            descriptor = descriptorFactory.fileDescriptor(vocabularyUri);
        } else {
            throw new IllegalArgumentException(
                    "Resource " + resource + " cannot be persisted into vocabulary context.");
        }
        return descriptor;
    }

    @ModifiesData
    @Override
    public Resource update(Resource entity) {
        final Resource updated;
        try {
            final URI vocabularyId = resolveVocabularyId(entity);
            if (vocabularyId != null) {
                setDocumentOnFileIfNecessary(entity, vocabularyId);
                // This evict is a bit overkill, but there are multiple relationships that would have to be evicted
                em.getEntityManagerFactory().getCache().evict(vocabularyId);
                updated = em.merge(entity, createDescriptor(entity, vocabularyId));
            } else {
                updated = em.merge(entity);
            }
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
        refreshLastModified();
        eventPublisher.publishEvent(new AssetUpdateEvent(this, updated));
        return updated;
    }

    private URI resolveVocabularyId(Resource resource) {
        if (resource instanceof Document) {
            return ((Document) resource).getVocabulary();
        } else if (resource instanceof File f) {
            if (f.getDocument() != null) {
                return f.getDocument().getVocabulary();
            }
            return null;
        }
        return null;
    }

    private void setDocumentOnFileIfNecessary(Resource file, URI vocabularyId) {
        if (!(file instanceof File)) {
            return;
        }
        final File original = em.find(File.class, file.getUri(), descriptorFactory.fileDescriptor(vocabularyId));
        ((File) file).setDocument(original.getDocument());
    }

    @Override
    public List<Resource> findAll() {
        try {
            return em.createNativeQuery("SELECT ?x WHERE {" +
                                                "?x a ?type ;" +
                                                "?hasLabel ?label ." +
                                                "FILTER NOT EXISTS { ?y ?hasFile ?x . } " +
                                                "FILTER NOT EXISTS { ?x a ?vocabulary . } " +
                                                "} ORDER BY LCASE(?label)", Resource.class)
                     .setParameter("type", typeUri)
                     .setParameter("hasLabel", labelProperty())
                     .setParameter("hasFile", URI.create(Vocabulary.s_p_ma_soubor))
                     .setParameter("vocabulary", URI.create(Vocabulary.s_c_slovnik))
                     .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public void refreshLastModified() {
        this.lastModified = System.currentTimeMillis();
    }

    @EventListener
    public void refreshLastModified(RefreshLastModifiedEvent event) {
        refreshLastModified();
    }
}
