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
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.asset.provenance.ModifiesData;
import cz.cvut.kbss.termit.asset.provenance.SupportsLastModification;
import cz.cvut.kbss.termit.event.RefreshLastModifiedEvent;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import static cz.cvut.kbss.termit.model.util.EntityToOwlClassMapper.getOwlClassForEntity;

@Repository
public class ResourceDao extends AssetDao<Resource> implements SupportsLastModification {

    private static final URI LABEL_PROPERTY = URI.create(DC.Terms.TITLE);

    private volatile long lastModified;

    public ResourceDao(EntityManager em, Configuration config, DescriptorFactory descriptorFactory) {
        super(Resource.class, em, config, descriptorFactory);
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
        try {
            final URI vocabularyId = resolveVocabularyId(entity);
            if (vocabularyId != null) {
                // This evict is a bit overkill, but there are multiple relationships that would have to be evicted
                em.getEntityManagerFactory().getCache().evict(vocabularyId);
                return em.merge(entity, createDescriptor(entity, vocabularyId));
            } else {
                return em.merge(entity);
            }
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private URI resolveVocabularyId(Resource resource) {
        if (resource instanceof Document) {
            return ((Document) resource).getVocabulary();
        } else if (resource instanceof File) {
            final File f = (File) resource;
            if (f.getDocument() != null) {
                // This is a workaround for the issue that GraphDB stores inferred statement (like File.document) in a separate
                // context, so that it is not loaded during merge and JOPA acts as though File.document has changed (which it cannot, as it is inferred)
                f.setDocument(em.find(Document.class, f.getDocument().getUri()));
                return f.getDocument().getVocabulary();
            }
            return null;
        }
        return null;
    }

    @Override
    public List<Resource> findAll() {
        try {
            return em.createNativeQuery("SELECT ?x WHERE {" +
                    "?x a ?type ;" +
                    "?hasLabel ?label ." +
                    "FILTER NOT EXISTS {" +
                    "{ ?y ?hasFile ?x . }" +
                    " UNION " +
                    "{ ?x a ?vocabulary . }" +
                    "} } ORDER BY ?label", Resource.class)
                     .setParameter("type", typeUri)
                     .setParameter("hasLabel", labelProperty())
                     .setParameter("hasFile", URI.create(Vocabulary.s_p_ma_soubor))
                     .setParameter("vocabulary",
                             URI.create(getOwlClassForEntity(cz.cvut.kbss.termit.model.Vocabulary.class)))
                     .getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Gets Terms the specified Resource is annotated with.
     * <p>
     * The terms are ordered by their name (ascending).
     *
     * @param resource Annotated resource
     * @return List of terms annotating the specified resource
     */
    public List<Term> findTerms(Resource resource) {
        Objects.requireNonNull(resource);
        try {
            return em.createNativeQuery("SELECT DISTINCT ?x WHERE {" +
                    "?x a ?term ;" +
                    "?hasLabel ?label ." +
                    "?assignment ?is-assignment-of ?x ;" +
                    "?has-target/?has-source ?resource ." +
                    "} ORDER BY ?label", Term.class)
                     .setParameter("term", URI.create(getOwlClassForEntity(Term.class)))
                     .setParameter("hasLabel", URI.create(SKOS.PREF_LABEL))
                     .setParameter("is-assignment-of", URI.create(Vocabulary.s_p_je_prirazenim_termu))
                     .setParameter("has-target", URI.create(Vocabulary.s_p_ma_cil))
                     .setParameter("has-source", URI.create(Vocabulary.s_p_ma_zdroj))
                     .setParameter("resource", resource.getUri()).getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    List<URI> findUniqueLastModifiedEntities(int limit) {
        // Must ensure vocabularies (which are technically also resources) are not included
        return em.createNativeQuery("SELECT DISTINCT ?entity WHERE {" +
                "?x a ?change ;" +
                "?hasModificationDate ?modified ;" +
                "?hasModifiedEntity ?entity ." +
                "?entity a ?type ." +
                "FILTER NOT EXISTS { ?entity a ?vocabulary . }" +
                "} ORDER BY DESC(?modified)", URI.class).setParameter("change", URI.create(Vocabulary.s_c_zmena))
                 .setParameter("hasModificationDate", URI.create(Vocabulary.s_p_ma_datum_a_cas_modifikace))
                 .setParameter("hasModifiedEntity", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                 .setParameter("type", typeUri)
                 .setParameter("vocabulary", URI.create(Vocabulary.s_c_slovnik))
                 .setMaxResults(limit).getResultList();
    }

    @Override
    List<URI> findUniqueLastModifiedEntitiesBy(User author, int limit) {
        return em.createNativeQuery("SELECT DISTINCT ?entity WHERE {" +
                "?x a ?change ;" +
                "?hasModificationDate ?modified ;" +
                "?hasEditor ?author ;" +
                "?hasModifiedEntity ?entity ." +
                "?entity a ?type ." +
                "FILTER NOT EXISTS { ?entity a ?vocabulary . }" +
                "} ORDER BY DESC(?modified)", URI.class).setParameter("change", URI.create(Vocabulary.s_c_zmena))
                 .setParameter("hasModificationDate", URI.create(Vocabulary.s_p_ma_datum_a_cas_modifikace))
                 .setParameter("hasEditor", URI.create(Vocabulary.s_p_ma_editora))
                 .setParameter("author", author)
                 .setParameter("hasModifiedEntity", URI.create(Vocabulary.s_p_ma_zmenenou_entitu))
                 .setParameter("type", typeUri)
                 .setParameter("vocabulary", URI.create(Vocabulary.s_c_slovnik))
                 .setMaxResults(limit).getResultList();
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
