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
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.asset.provenance.ModifiesData;
import cz.cvut.kbss.termit.asset.provenance.SupportsLastModification;
import cz.cvut.kbss.termit.event.RefreshLastModifiedEvent;
import cz.cvut.kbss.termit.exception.PersistenceException;
import cz.cvut.kbss.termit.model.Glossary;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.dao.util.Validator;
import cz.cvut.kbss.termit.util.Configuration;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class VocabularyDao extends AssetDao<Vocabulary> implements SupportsLastModification {

    private static final URI LABEL_PROPERTY = URI.create(DC.Terms.TITLE);

    private volatile long lastModified;

    private final ApplicationContext context;

    @Autowired
    public VocabularyDao(EntityManager em, Configuration config, DescriptorFactory descriptorFactory, ApplicationContext context) {
        super(Vocabulary.class, em, config, descriptorFactory);
        refreshLastModified();
        this.context = context;
    }

    @Override
    protected URI labelProperty() {
        return LABEL_PROPERTY;
    }

    @Override
    public List<Vocabulary> findAll() {
        final List<Vocabulary> result = super.findAll();
        result.sort(Comparator.comparing(Vocabulary::getLabel));
        return result;
    }

    @Override
    public Optional<Vocabulary> find(URI id) {
        Objects.requireNonNull(id);
        try {
            return Optional.ofNullable(em.find(type, id, descriptorFactory.vocabularyDescriptor(id)));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public Optional<Vocabulary> getReference(URI id) {
        Objects.requireNonNull(id);
        try {
            return Optional.ofNullable(em.getReference(type, id, descriptorFactory.vocabularyDescriptor(id)));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Gets identifiers of all vocabularies imported by the specified vocabulary, including transitively imported ones.
     *
     * @param entity Base vocabulary, whose imports should be retrieved
     * @return Collection of (transitively) imported vocabularies
     */
    public Collection<URI> getTransitivelyImportedVocabularies(Vocabulary entity) {
        Objects.requireNonNull(entity);
        try {
            return em.createNativeQuery("SELECT DISTINCT ?imported WHERE {" +
                    "?x ?imports+ ?imported ." +
                    "}", URI.class)
                     .setParameter("imports", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                     .setParameter("x", entity.getUri()).getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Gets identifiers of vocabularies which directly import the supplied one.
     *
     * @param vocabulary vocabulary, importing vocabularies of which are fetched
     * @return Collection of vocabularies which directly import #vocabulary
     */
    public List<Vocabulary> getImportingVocabularies(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        try {
            return em.createNativeQuery("SELECT DISTINCT ?importing WHERE {" +
                "?importing ?imports ?imported ." +
                "}", Vocabulary.class)
                .setParameter("imports", URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                .setParameter("imported", vocabulary.getUri()).getResultList();
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @ModifiesData
    @Override
    public Vocabulary update(Vocabulary entity) {
        Objects.requireNonNull(entity);
        try {
            // Evict possibly cached instance loaded from default context
            em.getEntityManagerFactory().getCache().evict(Vocabulary.class, entity.getUri(), null);
            return em.merge(entity, descriptorFactory.vocabularyDescriptor(entity));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    @ModifiesData
    @Override
    public void persist(Vocabulary entity) {
        Objects.requireNonNull(entity);
        try {
            em.persist(entity, descriptorFactory.vocabularyDescriptor(entity));
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Updates glossary contained in the specified vocabulary.
     * <p>
     * The vocabulary is passed for correct context resolution, as glossary existentially depends on its owning
     * vocabulary.
     *
     * @param entity Owner of the updated glossary
     * @return The updated entity
     */
    public Glossary updateGlossary(Vocabulary entity) {
        Objects.requireNonNull(entity);
        return em.merge(entity.getGlossary(), descriptorFactory.glossaryDescriptor(entity));
    }

    /**
     * Checks whether terms from the {@code subjectVocabulary} reference (as parent terms) any terms from the {@code
     * targetVocabulary}.
     *
     * @param subjectVocabulary Subject vocabulary identifier
     * @param targetVocabulary  Target vocabulary identifier
     * @return Whether subject vocabulary terms reference target vocabulary terms
     */
    public boolean hasInterVocabularyTermRelationships(URI subjectVocabulary, URI targetVocabulary) {
        Objects.requireNonNull(subjectVocabulary);
        Objects.requireNonNull(targetVocabulary);
        return em.createNativeQuery("ASK WHERE {" +
                "    ?t ?isTermFromVocabulary ?subjectVocabulary ; " +
                "       ?hasParentTerm ?parent . " +
                "    ?parent ?isTermFromVocabulary ?import . " +
                "    {" +
                "        SELECT ?import WHERE {" +
                "           ?targetVocabulary ?importsVocabulary* ?import . " +
                "} } }", Boolean.class)
                 .setParameter("isTermFromVocabulary",
                         URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                 .setParameter("subjectVocabulary", subjectVocabulary)
                 .setParameter("hasParentTerm", URI.create(SKOS.BROADER))
                 .setParameter("targetVocabulary", targetVocabulary)
                 .setParameter("importsVocabulary",
                         URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                 .getSingleResult();
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

    @Transactional
    public List<ValidationResult> validateContents(Vocabulary voc) {
        final Validator validator = context.getBean(Validator.class);
        try {
            final Collection<URI> importClosure = getTransitivelyImportedVocabularies(voc);
            importClosure.add(voc.getUri());
            return validator.validate(importClosure);
        } catch (IOException e) {
            throw new PersistenceException(e);
        }
    }
}
